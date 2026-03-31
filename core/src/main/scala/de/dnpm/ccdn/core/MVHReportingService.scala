package de.dnpm.ccdn.core


import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.{Executors, ScheduledExecutorService}
import java.util.concurrent.{TimeUnit, Future => JavaFuture}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import cats.syntax.either._
import de.dnpm.dip.util.Logging
import de.dnpm.dip.model.NGSReport
import de.dnpm.dip.service.mvh.Submission
import Submission.Report.Status.{Submitted, Unsubmitted}
import de.dnpm.ccdn.core.MVHReportingService.nConfirmationThreads
import de.dnpm.ccdn.core.bfarm.BfarmConnector
import de.dnpm.ccdn.core.dip.DipConnector


object MVHReportingService
{
  import scala.concurrent.ExecutionContext.Implicits.global

  /** See [[MVHReportingService.confirmSubmissionsReports]]
   */
  private[core] val nConfirmationThreads = 32

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
{

  /**
   * Executes the runnable in [[pollingTask]] in regular intervals
   */
  private val pollingExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor


  /**
   * Regularly queries DIP sites for new submissions ([[pollReports]]), uploads them to
   * BfArM ([[uploadReports]]) and sends a confirmation to dip sites ([[confirmSubmissionsReports]]).
   * The interval is retrieved from [[config#polling]]
   *
   * Before polling for new reports, the [[pollingQueue]] is checked for preexisting items,
   * which are processed before polling for new items in [[pollReports]]
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
            log.info(s"Conducting scheduled reporting workflow ${if(pollingQueue.exists(_ => true)) "with" else "without"} preexisting items in the queue")
            for {
              // Start by draining the report queue, if non-empty (in case the service had been interrupted) and
              // it thus contains reports whose upload hasn't been confirmed to the origin DIP), in order to avoid polling them again
              _ <- if (pollingQueue.exists(_.status == Unsubmitted)) uploadReports else Future.unit
              _ <- if (pollingQueue.exists(_.status == Submitted)) confirmSubmissionsReports else Future.unit
              _ <- pollReports
              _ <- uploadReports
              _ <- confirmSubmissionsReports
            } yield ()
            ()
          },
          delay,
          period,
          TimeUnit.SECONDS
        )
      )
  }


  def stop(): Unit = {
    log.info("Stopping MVH Reporting service")

    //first stop higher order thread which would use threads in
    // confirmationExecutor, then stop the latter
    pollingTask.foreach(_ cancel false)
    this.confirmationExecutor.shutdown()
    if(! this.confirmationExecutor.awaitTermination(5,TimeUnit.SECONDS)){
      this.confirmationExecutor.shutdownNow()
    }
    log.debug("Finished stopping MVH Reporting service")
  }

  /**
   * Converts the submission report (reporting a genome sequencing and asking
   * for reimbursement) as it comes from the DIP node (in [[pollReports]]) into
   * a report that will be transmitted to the BfArM (in [[uploadReports]])
   */
  private val BfarmReport: Submission.Report => bfarm.SubmissionReport = {

    import de.dnpm.dip.service.mvh.UseCase._
    import bfarm.LibraryType
    import bfarm.SubmissionReport.DiseaseType._
    import NGSReport.Type._

    report =>
      bfarm.SubmissionReport(
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


  /**
   * Communicates with all the configured DIP installations, queries them for new
   * [[Submission.Report]] and stores them in the [[pollingQueue]] with status
   * [[Unsubmitted]]
   */
  private[core] def pollReports: Future[Any] = {

    log.info(s"Polling Reports from ${config.sites.size} sites with up to ${config.activeUseCases.size} usecases")
    Future.traverse(
      config.sites.toList.sortBy(_._1.value)
    ){
      case (site,info) =>

        Future.traverse(
          info.useCases intersect config.activeUseCases  // ensure only active use cases are polled
        ){
          useCase =>

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
                  log.debug(s"Enqueuing ${reports.size} $useCase SubmissionReport")
                  pollingQueue.saveIfAbsent(reports)

                case Success(Left(err)) =>
                  log.error(s"Problem polling $useCase SubmissionReports of site $site: $err")
              }
              // Recover lest the Future traversal be "short-circuited" into a failed Future
              .recover {
                case t =>
                  log.error(s"Error(s) occurred polling $useCase SubmissionReports of $site", t)
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
   
    Future.traverse(
      pollingQueue.entries(_.status == Unsubmitted)
    )(
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
   * Used to gracefully end calls to [[confirmSubmissionsReports]] on shutdown
   */
  private val confirmationExecutor = Executors.newFixedThreadPool(nConfirmationThreads)
  private val confirmationExecutionContext = ExecutionContext.fromExecutor(confirmationExecutor)

  /**
   * Communicates with the DIP sites to confirm to them that a submission has
   * been sent to the BfArM. If successful the submission is removed from [[pollingQueue]]
   * Runs with an overridden threadpool to limit simultaneous executions. Apache Tomcats
   * which deploy the DIP nodes only handle up to 200 sockets simultaneously by default.
   * If a node is offline for some time, the accrued submissions can lead to a deadlock
   * without this limit
   */
  private[core] def confirmSubmissionsReports: Future[Seq[Either[String,Unit]]] = {

    implicit val ec:ExecutionContext = confirmationExecutionContext
    log.info("Confirming report submissions...")
    Future.traverse(
      pollingQueue.entries(_.status == Submitted)
    )(
      report =>
      dipConnector.confirmSubmitted(report)
        .map {
          case Right(_) =>
            log.debug(s"Submission confirmed: Site ${report.site.code}, TAN ${report.id}")
            pollingQueue.removeFromQueue(report)

          case err@Left(msg) =>
            log.error(s"Problem confirming submission: Site ${report.site.code}, TAN ${report.id} - $msg")
            err
        }
        // Recover lest the Future traversal be "short-circuited" into a failed Future
        .recover {
          case t =>
            log.error(s"Problem confirming submission: Site ${report.site.code}, TAN ${report.id} - ${t.getMessage}")
            t.getMessage.asLeft
        }

    )

  }
}
