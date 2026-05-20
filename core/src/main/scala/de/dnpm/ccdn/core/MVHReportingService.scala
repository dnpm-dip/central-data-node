package de.dnpm.ccdn.core


import java.time.{Clock, Instant, LocalDate, LocalTime}
import java.time.temporal.ChronoUnit
import java.util.concurrent.{ConcurrentLinkedQueue, Executors,
  ScheduledExecutorService, TimeUnit, Future => JavaFuture}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Success
import cats.syntax.either._
import cats.syntax.traverse._
import de.dnpm.dip.util.Logging
import de.dnpm.dip.model.{NGSReport, Site}
import de.dnpm.dip.service.mvh.Submission
import Submission.Report.Status.{Submitted, Unsubmitted}
import de.dnpm.ccdn.core.bfarm.BfarmConnector
import de.dnpm.ccdn.core.dip.DipConnector
import de.dnpm.dip.coding.Code
import scala.jdk.CollectionConverters.CollectionHasAsScala


object MVHReportingService
{
  import scala.concurrent.ExecutionContext.Implicits.global

  private[core] lazy val service =
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      dip.DipConnector.getInstance.get,
      bfarm.BfarmConnector.getInstance.get,
      PersistenceService.getInstance.get
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
  private[core] val bfarmConnector: BfarmConnector,
  private[core] val persistenceService: PersistenceService
)(
  implicit ec: ExecutionContext
)
extends Logging
with BatchingUtil
{
  /**
   * Serves local time. Abstracted for the purpose of unit tests
   */
  private[core] var clock: Clock = Clock.systemUTC()

  /**
   * Executes the runnable in [[pollingTask]] in regular intervals
   */
  private val pollingExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor


  /**
   * Regularly queries DIP sites for new submissions ([[pollReports]]), uploads
   * them to BfArM ([[uploadReports]]) and sends a confirmation to dip sites
   * ([[confirmSubmissions]]). The interval is retrieved from [[config#polling]]
   *
   * Before polling for new reports, the [[pollingQueue]] is checked for preexisting items,
   * which are processed before polling for new items in [[pollReports]]
   *
   * The state of this process is stored in the [[pollingQueue]] and in the
   * [[Submission.Report.status]] of it's items.
   *
   * Managed by [[pollingExecutor]]
   */
  private[core] var pollingTask: Option[JavaFuture[_]] = None

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
    if(config.mongoUri.isDefined) {
      log.debug(s"Mongodb uri: ${config.mongoUri.get}")
    } else{
      log.warn("Mongodb uri is undefined")
    }

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
          () => try {
            Await.result(conductReportingWorkflow(), Duration(45, TimeUnit.MINUTES))
          } catch {
            case _: java.util.concurrent.TimeoutException =>
              log.error("Reporting workflow did not complete within the 45-minute timeout")
          },
          delay,
          period,
          TimeUnit.SECONDS
        )
      )
  }

  /**
   * The functions in the [[DipConnector]] that communicate with external sites
   * return a report about connectivity. To end up with a single report per site
   * per attempt, this function coalesces the information collected in.
   * [[conductReportingWorkflow]]
   * @param responseLog collected base data
   * @return one report per site
   */
  private[core] def coalesceResponsivityReports(responseLog: Iterable[ResponsivityReport]) =
    responseLog
      .groupBy(_.site)
      .map { case (site, reports) =>
        val responsivity =
          if (reports.forall(_.responsivity == Responsivity.success))
            Responsivity.success
          else if (reports.forall(_.responsivity == Responsivity.failure))
            Responsivity.failure
          else
            Responsivity.mixedSuccess
        //strictly speaking this picks a random version string, but that
        // shouldn't be an issue, because only the one request to
        // getApiCompatibleDipSites would return a ResponsivityReport with a
        // version and all version info should be consistent anyway
        val version = reports.flatMap(_.versionString).headOption
        ResponsivityReport(site, responsivity, version)
      }

  /**
   * Executes one full cycle of the reporting workflow: checks site API versions,
   * drains any pre-existing queue entries, polls new reports, uploads them to
   * BfArM, and confirms back. Logs responsivity via [[PersistenceService]]
   */
  private[core] def conductReportingWorkflow(): Future[Unit] = {

    log.info(s"Conducting scheduled reporting workflow " +
      s"${if (pollingQueue.exists(_ => true)) "with" else "without"} " +
      s"preexisting items in the queue")

    //responseLog stores notes about how well a site could be communicated with.
    // checkSiteApiVersion will store a success item for every site that was
    // available, so it is sufficient for the other functions to merely report
    // failures. The endresult is reduced into a single value per site after the for loop.
    for {
      responseLog <- Future.successful(new ConcurrentLinkedQueue[ResponsivityReport])
      validSites <- getApiCompatibleDipSites(responseLog)
      // Start by draining the report queue, if non-empty (in case the service
      // had been interrupted) and it thus contains reports whose upload hasn't
      // been confirmed to the origin DIP), in order to avoid polling them again
      _ <- if (pollingQueue.exists(_.status == Unsubmitted)) uploadReports else Future.unit
      _ <- if (pollingQueue.exists(_.status == Submitted)) confirmSubmissions(responseLog) else Future.unit
      _ <- pollReports(validSites,responseLog)
      _ <- uploadReports
      _ <- confirmSubmissions(responseLog)
    } yield {
      persistenceService.writeSiteAvailabilityReports(
        coalesceResponsivityReports(responseLog.asScala), Instant.now(clock))
    }
  }

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


  private val versionCutoverDate: LocalDate = LocalDate.of(2026, 6, 1)
  private val isSiteApiVersionSupported: String => Boolean = version =>
    LocalDate.now(clock).isBefore(versionCutoverDate) || (version >= "1.3")

  /**
   *
   * @param availabilityBuffer used to collect information on how well a site
   *                           could be communicated with. This function should
   *                           store a value for every site in [[config.sites]],
   *                           either [[Responsivity.failure]] or
   *                           [[Responsivity.success]]
   * @return a list of sites that responded with an API version code that is
   *         supported by the MVH network.
   */
  private[core] def getApiCompatibleDipSites(availabilityBuffer:ConcurrentLinkedQueue[ResponsivityReport])
  : Future[Seq[Code[Site]]] = {
    Future.traverse(config.sites.keys.toSeq) { site =>
      dipConnector.getApiVersion(site)
        .map {
          case Right(v) if isSiteApiVersionSupported(v) =>
            availabilityBuffer.add(ResponsivityReport(site, Responsivity.success,Some(v)))
            Some(site)
          case Right(v) =>
            availabilityBuffer.add(ResponsivityReport(site, Responsivity.success, Some(v)))
            None
          case Left(_) =>
            availabilityBuffer.add(ResponsivityReport(site, Responsivity.failure))
            None
        }
        .recover {
          case _ =>
            availabilityBuffer.add(ResponsivityReport(site, Responsivity.failure))
            None
        }
    }.map(_.flatten)
  }

  /**
   * Communicates with all the configured DIP nodes, queries them for
   * new  [[Submission.Reports]], i.e. with status [[Unsubmitted]], and stores
   * them in the [[pollingQueue]]
   */
  private[core] def pollReports(validSites: Seq[Code[Site]],
                                availabilityBuffer:ConcurrentLinkedQueue[ResponsivityReport])
  : Future[Any] = {
    log.info(s"Polling Reports from ${config.sites.size} sites with up " +
      s"to ${config.activeUseCases.size} usecases")
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
                  log.error(s"Problem polling $useCase SubmissionReports of " +
                    s"site $site: $err")
                  availabilityBuffer.add(ResponsivityReport(site,Responsivity.failure))
              }
              // Recover lest the Future traversal be "short-circuited" into a failed Future
              .recover {
                case t =>
                  log.error(s"Error(s) occurred polling $useCase " +
                    s"SubmissionReports of $site", t)
                  availabilityBuffer.add(ResponsivityReport(site,Responsivity.failure))
                  t.getMessage.asLeft
              }
          }
      }

  }


  /**
   * Communicates with the BfArM, sends them [[BfarmReport]] entities, each based
   * on one of all the [[Submission.Report]] entities in the [[pollingQueue]]
   * that are in status [[Unsubmitted]]. After this upload their status is
   * changed to [[Submitted]]
   */
  private[core] def uploadReports: Future[Seq[Either[String,Unit]]] = {

    log.info("Uploading SubmissionReports...")
   
    pollingQueue.entries(_.status == Unsubmitted).traverse(
      report =>
        bfarmConnector.upload(BfarmReport(report))
          .map {
            case Right(_) =>
              log.info(s"SubmissionReport Uploaded: " +
                s"Site ${report.site.code}, TAN ${report.id}")
              pollingQueue.replace(report.copy(status = Submitted))

            case err @ Left(msg) =>
              log.error(s"Problem uploading SubmissionReport: " +
                s"Site ${report.site.code}, TAN ${report.id} - $msg")
              err
          }
          // Recover lest the Future traversal be "short-circuited" into a failed Future 
          .recover {
            case t =>
              log.error(s"Problem uploading SubmissionReport: " +
                s"Site ${report.site.code}, TAN ${report.id} - ${t.getMessage}")
              t.getMessage.asLeft
          }
    )

  }

  /**
   * Limits the number of submissions that can be processed simultaneously
   * in [[confirmSubmissions]], which is additionally limited by the actual
   * number of available threads
   */
  private[core] val nSimultaneousSubmissionConfirmations:Int = 50

  /**
   * Send "submission confirmations" to the DIP nodes for each SubmissionReport
   * that has been successfully submitted to BfArM. If successful the
   * SubmissionReport is removed from [[pollingQueue]]
   *
   * NOTE: Given that some DIP nodes are placed behind an Apache Tomcat server,
   * which only handles up to 200 sockets simultaneously by default, explicit
   * batching is applied to avoid deadlock in case more than 200
   * SubmissionReports were processed in parallel here.
   */

  private[core] def confirmSubmissions(availabilityBuffer:ConcurrentLinkedQueue[ResponsivityReport])
  :Future[Seq[Either[String,Submission.Report]]] =
    batchTraverse[Submission.Report, Seq, Future, Either[String, Submission.Report]](
      pollingQueue.entries(_.status == Submitted),
      nSimultaneousSubmissionConfirmations
    )(
      report => (dipConnector.confirmSubmitted(report).map {
        case Right(_) =>
          pollingQueue.removeFromQueue(report).map(_ => report)

        case Left(msg) =>
          (s"Problem confirming submission: Site ${report.site.code}, " +
            s"TAN ${report.id} - $msg").asLeft[Submission.Report]
      }: Future[Either[String, Submission.Report]])
      .andThen {
        case Success(Right(_)) =>
          log.debug(s"Submission confirmed: Site ${report.site.code}, " +
            s"TAN ${report.id}")

        //Logs either the error message from the submission confirmation request
        // or from queue removal
        case Success(Left(msg)) =>
          availabilityBuffer.add(ResponsivityReport(report.site.code,Responsivity.failure))
          log.error(msg)
      }
      // Recover lest the Future traversal be "short-circuited" into a failed Future
      .recover {
        case t =>
          log.error(s"Problem confirming submission: Site ${report.site.code}, " +
            s"TAN ${report.id} - ${t.getMessage}")
          availabilityBuffer.add(ResponsivityReport(report.site.code,Responsivity.failure))
          t.getMessage.asLeft[Submission.Report]
      }

    )

}
