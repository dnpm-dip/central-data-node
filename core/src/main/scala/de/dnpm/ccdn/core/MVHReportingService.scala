package de.dnpm.ccdn.core


import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.{
  Executors,
  ScheduledExecutorService
}
import java.util.concurrent.{
  Future => JavaFuture,
  TimeUnit
}
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Success
import cats.syntax.either._
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm
import de.dnpm.dip.model.NGSReport
import de.dnpm.dip.service.mvh.Submission
import Submission.Report.Status.{
  Submitted,
  Unsubmitted
}


object MVHReportingService
{

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val service =
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      dip.Connector.getInstance.get,
      bfarm.Connector.getInstance.get
    )
    
    
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
  queue: ReportRepository,
  dipConnector: dip.Connector,
  bfarmConnector: bfarm.Connector
)(
  implicit ec: ExecutionContext
)
extends Logging
{

  private val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor


  private var scheduledTask: Option[JavaFuture[_]] = None

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


    scheduledTask =
      Some(
        executor.scheduleAtFixedRate(
          () => {
            for {
              // Start by draining the report queue, if non-empty (in case the service had been interrupted) and
              // it thus contains reports whose upload hasn't been confirmed to the origin DIP), in order to avoid polling them again
              _ <- if (queue.exists(_.status == Unsubmitted)) uploadReports else Future.unit
              _ <- if (queue.exists(_.status == Submitted)) confirmSubmissions else Future.unit
              _ <- pollReports
              _ <- uploadReports
              _ <- confirmSubmissions
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
    scheduledTask.foreach(_ cancel false)
  }


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


  private def key(report: Submission.Report) =
    report.site.code -> report.id


  private[core] def pollReports: Future[Any] =
    Future.traverse(
      config.sites.toList.sortBy(_._1.value)
    ){
      case (site,info) =>

        Future.traverse(
          info.useCases intersect config.activeUseCases  // ensure only active use cases are polled
        ){
          useCase => 
        
            log.info(s"Polling $useCase SubmissionReports of $site")
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
                queue.saveIfAbsent(reports,key(_))
            
              case Success(Left(err)) =>
                log.error(s"Problem polling $useCase SubmissionReports of site $site: $err")
            }
            // Recover lest the Future traversal be "short-circuited" into a failed Future 
            .recover {
              case t =>  
                log.error(s"Error(s) occurred polling $useCase SubmissionReports of '$site",t)
                t.getMessage.asLeft
            }
        }
    }  


  private[core] def uploadReports: Future[Seq[Either[String,Unit]]] = {

    log.info("Uploading SubmissionReports...")
   
    Future.traverse(
      queue.entries(_.status == Unsubmitted)
    )(
      report =>
        bfarmConnector
          .upload(BfarmReport(report))
          .map {
            case Right(_) =>
              log.info(s"SubmissionReport Uploaded: Site ${report.site.code}, TAN ${report.id}")
              queue.replace(key(report),report.copy(status = Submitted))

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


  private[core] def confirmSubmissions: Future[Seq[Either[String,Unit]]] = {

    log.info("Confirming report submissions...")
   
    Future.traverse(
      queue.entries(_.status == Submitted)
    )(
      report =>
        dipConnector.confirmSubmitted(report)
          .map {
            case Right(_) =>
              log.debug(s"Submission confirmed: Site ${report.site.code}, TAN ${report.id}")
              queue.remove(key(report))

            case err @ Left(msg) =>
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
