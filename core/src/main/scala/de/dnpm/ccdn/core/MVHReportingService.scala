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
//import de.dnpm.dip.coding.Coding
import de.dnpm.dip.util.Logging


object MVHReportingService
{

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val service =
    new MVHReportingService(
      Config.instance,
      ReportQueue.getInstance.get,
      DNPM.Connector.getInstance.get,
      BfArM.Connector.getInstance.get
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
  dnpm: DNPM.Connector,
  bfarm: BfArM.Connector
)(
  implicit ec: ExecutionContext
)
extends Logging
{

  import DNPM.UseCase._
  import BfArM.SubmissionReport.DiseaseType._


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
/*          
          () => {
            for {
              rs <- pollReports
              _ <- uploadReports
            } yield ()
          },
*/
          0,
          config.polling.period.toLong,
          config.polling.timeUnit
        )
      )
   
   
  def stop(): Unit = {
    log.info("Stopping MVH Reporting service")
    scheduledTask.foreach(_.cancel(false))
  }


  private val toBfArMReport: DNPM.SubmissionReport => BfArM.SubmissionReport = {
    case DNPM.SubmissionReport(created,site,useCase,ttan,submType,seqType,qcPassed) =>
      BfArM.SubmissionReport(
        created.toLocalDate,
        submType,
        ttan,
        config.submitterIds(site.code),
        config.dataNodeId,
        BfArM.SubmissionReport.DataCategory.Clinical,
        useCase match {
          case MTB => Oncological
          case RD  => Rare
        },
        seqType.map {
          case DNPM.SequencingType.Panel    => BfArM.SequencingType.Panel
          case DNPM.SequencingType.Exome    => BfArM.SequencingType.WES
          case DNPM.SequencingType.Genome   => BfArM.SequencingType.WGS
          case DNPM.SequencingType.GenomeLr => BfArM.SequencingType.WGSLr
        }
        .getOrElse(BfArM.SequencingType.None),
        qcPassed        
      )
  }


  private[core] def pollReports: Future[Unit] = {

    log.info("Polling DNPM-SubmissionReports...")

    dnpm.sites.flatMap {
      case Right(sites) =>
        Future.sequence(
          sites.map {
            site =>
          
              log.info(s"Polling SubmissionReports of site '${site.display.getOrElse(site.code)}'")
          
              dnpm.dataSubmissionReports(
                site,
                queue.lastPollingTime(site).map(Period(_))
              )
              .andThen {
//                case Success(Right(reports: Seq[DNPM.SubmissionReport])) =>
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

    Future.sequence(
      queue.entries
        .map(
          report =>
            bfarm
              .upload(toBfArMReport(report))
              .andThen{ 
                case Success(Right(_: BfArM.SubmissionReport)) =>
                  log.debug("Upload successful")
                  queue.remove(report)
              }
        )
    )
    .map(_ => ())
  }

}
