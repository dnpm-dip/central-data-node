package de.dnpm.ccdn.core


import java.time.LocalDateTime.now
import java.util.concurrent.{
  Executors,
  ScheduledExecutorService
}
import java.util.concurrent.{Future => JavaFuture}
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.{
  Failure,
  Success
}
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm
import de.dnpm.dip.model.{
  NGSReport,
  Period
}
import de.dnpm.dip.service.mvh.Submission


object MVHReportingService
{

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val service =
    new MVHReportingService(
      Config.instance,
      ReportQueue.getInstance.get,
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
  queue: ReportQueue,
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


  def start(): Unit = {
    log.info("Starting MVH Reporting service")

    scheduledTask =
      Some(
        executor.scheduleAtFixedRate(
          () => {
            for {
              _ <- uploadReports  // Start by draining the report queue, in case the service had been interrupted and
                                  // it thus contains reports whose upload hasn't been confirmed to the origin DIP,
                                  // to avoid polling them again
              _ <- pollReports
              _ <- uploadReports
            } yield ()
            ()
          },
          0,
          config.polling.period.toLong,
          config.polling.timeUnit
        )
      )
   
  }


  def stop(): Unit = {
    log.info("Stopping MVH Reporting service")
    scheduledTask.foreach(_ cancel false)
  }


  private val toBfarmReport: Submission.Report => bfarm.SubmissionReport = {

    import de.dnpm.dip.service.mvh.UseCase._
    import bfarm.SubmissionReport.LibraryType
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
        .getOrElse(bfarm.SubmissionReport.LibraryType.Undefined),
        report.healthInsuranceType,
      )
  }


  private[core] def pollReports: Future[Unit] = {

    log.info("Polling SubmissionReports...")

    Future.sequence(
      config.sites.toList.sortBy(_._1.value).map {
//      config.sites.map {
        case (site,info) =>
        
          log.info(s"Polling SubmissionReports of site '$site'")
          
          dipConnector.submissionReports(
            site,
            info.useCases intersect config.activeUseCases,
            Submission.Report.Filter(
              queue.lastPollingTime(site).map(t => Period(t)),
              Some(Set(Submission.Report.Status.Unsubmitted))
            )
          )
          .andThen {
            case Success(Right(reports)) =>
              log.debug(s"Enqueuing ${reports.size} reports")
              queue
                .addAll(reports)
                .setLastPollingTime(site,now)
        
            case Success(Left(err)) =>
              log.error(s"Problem polling SubmissionReports of site '$site: $err")

            case Failure(t) =>
              log.error(s"Error(s) occurred polling SubmissionReports of site '$site",t)
        
          }   
      }  
    )
    .map(_ => ())
  }


  private[core] def uploadReports: Future[Unit] = {

    log.info("Uploading SubmissionReports...")
   
    Future.sequence(
      queue.entries
        .map(
          report =>
            bfarmConnector
              .upload(toBfarmReport(report))
              .flatMap {
                case Right(_) =>
                  log.debug(s"Upload successful: Site = ${report.site.code} TAN = ${report.id}, confirming submission")
                  dipConnector.confirmSubmitted(report)

                case err @ Left(msg) =>
                  log.error(s"Problem uploading report, Site = ${report.site.code} TAN = ${report.id}: $msg")
                  Future.successful(err)
              }
              .andThen {
                case Success(Right(_)) =>
                  log.debug("Submission confirmation successful")
                  queue.remove(report)

                case Failure(t) =>
                  log.error(s"Problem confirming submission",t)
              }
        )
    )
    .map(_ => ())
    
  }

}
