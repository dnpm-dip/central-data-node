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
  Success,
  Left,
  Right
}
import scala.util.chaining._
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm
import de.dnpm.dip.model.HealthInsurance
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
        override def run =
          println("Shutting down MVH Reporting service...")
          service.stop()
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

//  import dip.UseCase._


  private val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor

  private var scheduledTask: Option[JavaFuture[_]] = None

  def start(): Unit =
    log.info("Starting MVH Reporting service")
    scheduledTask =
      Some(
        executor.scheduleAtFixedRate(
          () => {
            pollReports.flatMap(u => uploadReports)
            ()
          },
          0,
          config.polling.period.toLong,
          config.polling.timeUnit
        )
      )
   
   
  def stop(): Unit = {
    log.info("Stopping MVH Reporting service")
    scheduledTask.foreach(_.cancel(false))
  }


/*
  private val tobfarmReport: dip.SubmissionReport => bfarm.SubmissionReport = {
    case dip.SubmissionReport(created,site,useCase,ttan,submType,seqType,qcPassed) =>
      bfarm.SubmissionReport(
        created.toLocalDate,
        submType,
        ttan,
        config.submitterIds(site.code),
        config.dataNodeId,
        bfarm.SubmissionReport.DataCategory.Clinical,
        useCase match {
          case MTB => Oncological
          case RD  => Rare
        },
        seqType.map {
          case dip.SequencingType.Panel    => bfarm.SequencingType.Panel
          case dip.SequencingType.Exome    => bfarm.SequencingType.WES
          case dip.SequencingType.Genome   => bfarm.SequencingType.WGS
          case dip.SequencingType.GenomeLr => bfarm.SequencingType.WGSLr
        }
        .getOrElse(bfarm.SequencingType.None),
        qcPassed        
      )
  }
*/

  private val toBfarmReport: Submission.Report => bfarm.SubmissionReport = {

    import de.dnpm.dip.service.mvh.UseCase._
    import bfarm.SubmissionReport.DiseaseType._

    report =>
      bfarm.SubmissionReport.Case(
        report.submittedAt.toLocalDate,
        report.`type`,
        report.transferTAN,
        config.submitterIds(report.site.code),
        config.dataNodeIds(report.useCase),
        report.useCase match { 
          case MTB => Oncological
          case RD  => Rare
        },
        report.healthInsuranceType match {
          case HealthInsurance.Type(value) => value
          case _                           => HealthInsurance.Type.UNK
        },
        true
      )
      .pipe(bfarm.SubmissionReport(_))
  }

  private[core] def pollReports: Future[Unit] = {

    log.info("Polling dip-SubmissionReports...")

    dipConnector.sites.flatMap {
      case Right(sites) =>
        Future.sequence(
          sites.map {
            site =>
          
              log.info(s"Polling SubmissionReports of site '${site.display.getOrElse(site.code)}'")
          
              dipConnector.dataSubmissionReports(
                site,
                queue.lastPollingTime(site).map(Period(_))
              )
              .andThen {
                case Success(Right(reports)) =>
                  log.debug(s"Enqueueing ${reports.size} reports")
                  queue
                    .addAll(reports)
                    .setLastPollingTime(site,now)

                case _ =>
                  log.error(s"Error(s) occurred polling SubmissionReports of site '${site.display.getOrElse(site.code)}")

              }   
          }
        )
        .map(_ => ())

      case Left(err) =>
        s"Problem getting site info list: $err"
          .tap(log.error)
          .pipe(new Exception(_))
          .pipe(Future.failed)
      }
  }
    
  private[core] def uploadReports: Future[Unit] = {

    log.info("Uploading SubmissionReports...")

//    Future.failed(new RuntimeException("TODO!"))
   
    Future.sequence(
      queue.entries
        .map(
          report =>
            bfarmConnector
              .upload(toBfarmReport(report))
              .andThen{ 
                case Success(Right(_: bfarm.SubmissionReport)) =>
                  log.debug("Upload successful")
                  queue.remove(report)
              }
        )
    )
    .map(_ => ())
    
  }

}
