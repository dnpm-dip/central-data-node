package de.dnpm.ccdn.core


import java.time.{Instant, LocalTime}
import java.time.temporal.ChronoUnit
import java.util.concurrent.{Executors, ScheduledExecutorService}
import java.util.concurrent.{TimeUnit, Future => JavaFuture}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import cats.syntax.either._
import cats.syntax.traverse._
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import de.dnpm.dip.util.Logging
import de.dnpm.dip.model.{NGSReport, Site}
import de.dnpm.dip.service.mvh.Submission
import Submission.Report.Status.{Submitted, Unsubmitted}
import de.dnpm.ccdn.core.bfarm.BfarmConnector
import de.dnpm.ccdn.core.dip.DipConnector
import de.dnpm.dip.coding.Code

import scala.collection.mutable.ListBuffer


object MVHReportingService
{
  import scala.concurrent.ExecutionContext.Implicits.global

  private[core] lazy val service =
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      dip.DipConnector.getInstance.get,
      bfarm.BfarmConnector.getInstance.get
    )

  /**
   * Entrypoint of the deployment for the JVM (see entrypoint.sh)
   */
  def main(args: Array[String]): Unit = {
    
    Runtime.getRuntime.addShutdownHook(
      new Thread { 
        override def run = {
          println("Shutting down MVH Reporting service...")
          service.stop()
        }
      }
    )

    service.start()
  }

}


class MVHReportingService
(
  config: Config,
  private[core] val pollingQueue: ReportRepository,
  private[core] val dipConnector: DipConnector,
  private[core] val bfarmConnector: BfarmConnector
)(
  implicit ec: ExecutionContext
)
extends Logging
with BatchingUtil
{

  private val mongoUri: Option[String] = sys.env.get("CCDN_MONGODB_URI") //TODO maybe put this into config

  private def writeSiteAvailabilityReports(reports: Iterable[ResponsivityReport]): Unit =
    mongoUri.foreach { uri =>
      import cats.effect.unsafe.implicits.global
      MongoClient.fromConnectionString[IO](uri).use { client =>
        for {
          db   <- client.getDatabase("ccdn")
          coll <- db.getCollection("siteAvailabilityReports")
          now   = Instant.now
          docs  = reports.map(r => Document("site" := r.site.value, "responsivity" := r.responsivity.toString, "timestamp" := now)).toList
          _    <- coll.insertMany(docs)
        } yield ()
      }.unsafeRunAndForget()
    }


  /**
   * Executes the runnable in [[pollingTask]] in regular intervals
   */
  private val pollingExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor


  /**
   * Regularly queries DIP sites for new submissions ([[pollReports]]), uploads them to
   * BfArM ([[uploadReports]]) and sends a confirmation to dip sites ([[confirmSubmissions]]).
   * The interval is retrieved from [[config#polling]]
   *
   * Before polling for new reports, the [[pollingQueue]] is checked for preexisting items,
   * which are pro  cessed before polling for new items in [[pollReports]]
   *
   * The state of this process is stored in the [[pollingQueue]] and in the
   * [[Submission.Report.status]] of it's items.
   *
   * Managed by [[pollingExecutor]]
   */
  private var pollingTask: Option[JavaFuture[_]] = None

  private val toSeconds =
    Map(
      TimeUnit.SECONDS -> 1,
      TimeUnit.MINUTES -> 60,
      TimeUnit.HOURS   -> 3600,
      TimeUnit.DAYS    -> 86400,
    )

  def start(): Unit = {

    log.info("Starting MVH Reporting service")
    log.info(s"Active Use Cases: ${config.activeUseCases.mkString(", ")}")
    log.info(s"Active sites: ${config.sites.keys.toList.sortBy(_.value).mkString(", ")}")
    log.info(s"Mongodb uri: ${mongoUri}")

    val period =
      config.polling.period*toSeconds(config.polling.timeUnit)

    val delay =
      config.polling.startTime
        .map(ChronoUnit.SECONDS.between(LocalTime.now,_))
        .collect {
          case delta if delta >= 0            => delta
          case delta if (86400 % period == 0) => period - (math.abs(delta) % period) 
          case delta                          => delta + 86400
        }
        .getOrElse(0L)  

    log.info(s"Scheduling report polling to start in $delay s with $period s period")   


    pollingTask =
      Some(
        pollingExecutor.scheduleAtFixedRate(
          () => {
            log.info(s"Conducting scheduled reporting workflow ${if (pollingQueue.exists(_ => true)) "with" else "without"} preexisting items in the queue")

            //responseLog stores notes about how well a site could be communicated with.
            // checkSiteApiVersion will store a success item for every site that was
            // available, so it is sufficient for the other functions to merely report
            // failures. The endresult is reduced into a single value per site after the for loop.
            val responseLog: ListBuffer[ResponsivityReport] = ListBuffer()

            for {
              validSites <- checkSiteApiVersion(responseLog)
              // Start by draining the report queue, if non-empty (in case the service had been interrupted) and
              // it thus contains reports whose upload hasn't been confirmed to the origin DIP), in order to avoid polling them again
              _ <- if (pollingQueue.exists(_.status == Unsubmitted)) uploadReports else Future.unit
              _ <- if (pollingQueue.exists(_.status == Submitted)) confirmSubmissions(responseLog) else Future.unit
              _ <- pollReports(validSites,responseLog)
              _ <- uploadReports
              _ <- confirmSubmissions(responseLog)
            } yield {
              writeSiteAvailabilityReports(
                responseLog
                  .groupBy(_.site)
                  .map { case (site, reports) =>
                    val responsivity =
                      if (reports.forall(_.responsivity == Responsivity.success)) Responsivity.success
                      else if (reports.forall(_.responsivity == Responsivity.failure)) Responsivity.failure
                      else Responsivity.mixedSuccess
                    ResponsivityReport(site, responsivity)
                  }
              )
            }
            ()
          },
          delay,
          period,
          TimeUnit.SECONDS
        )
      )
  }

  /**
   * How a site responded to requests. A record can consist of multiple items
   * for the same site and can be reduced to single value. Mixed reports reduce to [[mixedSuccess]]
   */
  object Responsivity extends Enumeration {
    val success = Value("fully")
    val mixedSuccess = Value("partial")
    val failure = Value("offline")
  }
  case class ResponsivityReport(val site:Code[Site],val responsivity: Responsivity.Value)


  def stop(): Unit = {
    log.info("Stopping MVH Reporting service")

    // Gracefully stop pollingTask, if running
    pollingTask.foreach(_ cancel false)

    log.debug("Finished stopping MVH Reporting service")
  }

  /**
   * Converts the submission report (reporting a genome sequencing and asking
   * for reimbursement) as it comes from the DIP node (in [[pollReports]]) into
   * a report that will be transmitted to the BfArM (in [[uploadReports]])
   */
  private val BfarmReport: Submission.Report => bfarm.SubmissionReport = {

    import de.dnpm.dip.service.mvh.UseCase._
    import de.dnpm.bfarm.model.base.LibraryType
    import bfarm.SubmissionReport.DiseaseType._
    import NGSReport.Type._

    report => bfarm.SubmissionReport(
      report.createdAt.toLocalDate,
      report.`type`,
      report.id,
      config.submitterId(report.site.code),
      config.dataNodeIds(report.useCase),
      report.useCase match {
        case MTB => Oncological
        case RD  => Rare
      },
      report.sequencingType.collect {
        case GenomeLongRead  => LibraryType.WGSLr
        case GenomeShortRead => LibraryType.WGS
        case Exome           => LibraryType.WES
        case Panel           => LibraryType.Panel
      }
      .getOrElse(LibraryType.None),
      report.healthInsuranceType
    )
  }


  private val isSiteApiVersionSupported: String => Boolean = _ == "1.2.3"

  /**
   *
   * @param availabilityBuffer used to collect information on how well a site could be communicated with.
   *                           This function should store a value for every site in [[config.sites]],
   *                           either [[Responsivity.failure]] or [[Responsivity.success]]
   * @return a list of sites that responded with a site code that is supported by the MVH network.
   */
  private[core] def checkSiteApiVersion(availabilityBuffer:ListBuffer[ResponsivityReport]): Future[Seq[Code[Site]]] = {
    Future.traverse(config.sites.keys.toSeq) { site =>
      dipConnector.getApiVersion(site)
        .map {
          case Right(v) if isSiteApiVersionSupported(v) =>
            availabilityBuffer += ResponsivityReport(site, Responsivity.success)
            Some(site)
          case Right(_) =>
            availabilityBuffer += ResponsivityReport(site, Responsivity.success)
            None
          case Left(_) =>
            availabilityBuffer += ResponsivityReport(site, Responsivity.failure)
            None
        }
        .recover {
          case _ =>
            availabilityBuffer += ResponsivityReport(site, Responsivity.failure)
            None
        }
    }.map(_.flatten)
  }

  /**
   * Communicates with all the configured DIP nodes, queries them for new  [[Submission.Reports]],
   * i.e. with status [[Unsubmitted]], and stores them in the [[pollingQueue]]
   */
  private[core] def pollReports(validSites: Seq[Code[Site]],
                                availabilityBuffer:ListBuffer[ResponsivityReport])
  : Future[Any] = {
    log.info(s"Polling Reports from ${config.sites.size} sites with up to ${config.activeUseCases.size} usecases")
    config.sites.toList
      .filter(configVal => validSites.contains(configVal._1))
      .sortBy(_._1.value) // Just for easier log reading: sort the sites alphabetically
      .traverse {
        case (site,info) =>
          info.useCases.intersect(config.activeUseCases) // ensure only active use cases are polled
            .toList
            .traverse { useCase =>

              log.debug(s"Polling $useCase SubmissionReports of $site")
              dipConnector.submissionReports(
                site,
                useCase,
                Submission.Report.Filter(
                  status = Some(Set(Submission.Report.Status.Unsubmitted))
                )
              )
              .andThen {
                case Success(Right(reports)) =>
                  log.debug(s"Enqueuing ${reports.size} $useCase SubmissionReports")
                  pollingQueue.saveIfAbsent(reports)

                case Success(Left(err)) =>
                  log.error(s"Problem polling $useCase SubmissionReports of site $curSite: $err")
                  availabilityBuffer += ResponsivityReport(curSite,Responsivity.failure)
              }
              // Recover lest the Future traversal be "short-circuited" into a failed Future
              .recover {
                case t =>
                  log.error(s"Error(s) occurred polling $useCase SubmissionReports of $curSite", t)
                  availabilityBuffer += ResponsivityReport(curSite,Responsivity.failure)
                  t.getMessage.asLeft
              }
          }
      }

  }


  /**
   * Communicates with the BfArM, sends them [[BfarmReport]] entities, each based
   * on one of all the [[Submission.Report]] entities in the [[pollingQueue]] that are
   * in status [[Unsubmitted]]. After this upload their status is changed to [[Submitted]]
   */
  private[core] def uploadReports: Future[Seq[Either[String,Unit]]] = {

    log.info("Uploading SubmissionReports...")
   
    pollingQueue.entries(_.status == Unsubmitted).traverse(
      report =>
        bfarmConnector.upload(BfarmReport(report))
          .map {
            case Right(_) =>
              log.info(s"SubmissionReport Uploaded: Site ${report.site.code}, TAN ${report.id}")
              pollingQueue.replace(report.copy(status = Submitted))

            case err @ Left(msg) =>
              log.error(s"Problem uploading SubmissionReport: Site ${report.site.code}, TAN ${report.id} - $msg")
              err
          }
          // Recover lest the Future traversal be "short-circuited" into a failed Future 
          .recover {
            case t =>
              log.error(s"Problem uploading SubmissionReport: Site ${report.site.code}, TAN ${report.id} - ${t.getMessage}")
              t.getMessage.asLeft
          }
    )

  }

  /**
   * Limits the number of submissions that can be processed simultaneously in [[confirmSubmissions]],
   * which is additionally limited by the actual number of available threads
   */
  private[core] val nSimultaneousSubmissionConfirmations:Int = 50

  /**
   * Send "submission confirmations" to the DIP nodes for each SubmissionReport that has
   * been successfully submitted to BfArM. If successful the SubmissionReport is removed from [[pollingQueue]]
   *
   * NOTE: Given that some DIP nodes are placed behind an Apache Tomcat server, which only
   * handles up to 200 sockets simultaneously by default, explicit batching is applied
   * to avoid deadlock in case more than 200 SubmissionReports were processed in parallel here.
   */

  private[core] def confirmSubmissions(availabilityBuffer:ListBuffer[ResponsivityReport]): Future[Seq[Either[String,Submission.Report]]] =
    batchTraverse(
      pollingQueue.entries(_.status == Submitted),
      nSimultaneousSubmissionConfirmations
    )(
      report => dipConnector.confirmSubmitted(report).map {
        case Right(_) =>
          pollingQueue.removeFromQueue(report).map(_ => report)

        case Left(msg) =>
          s"Problem confirming submission: Site ${report.site.code}, TAN ${report.id} - $msg".asLeft
      }
      .andThen {
        case Success(Right(_)) =>
          log.debug(s"Submission confirmed: Site ${report.site.code}, TAN ${report.id}")

        // Logs either the error message from the submission confirmation request or from queue removal
        case Success(Left(msg)) =>
          availabilityBuffer += ResponsivityReport(report.site.code,Responsivity.failure)
          log.error(msg)
      }
      // Recover lest the Future traversal be "short-circuited" into a failed Future
      .recover {
        case t =>
          log.error(s"Problem confirming submission: Site ${report.site.code}, TAN ${report.id} - ${t.getMessage}")
          availabilityBuffer += ResponsivityReport(report.site.code,Responsivity.failure)
          t.getMessage.asLeft
      }

    )

}
