package de.dnpm.ccdn.core


import java.time.LocalDateTime.now
import java.util.concurrent.{
  Executors,
  ScheduledExecutorService
}
import java.util.concurrent.Future as JavaFuture
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.{
  Success,
  Failure
}
import scala.util.chaining._
import de.dnpm.ccdn.util.Logging


@main def exec: Unit =

  import scala.concurrent.ExecutionContext.Implicits.global

  val service =
    new MVHReportingService(
      Config.instance,
      ReportQueue.getInstance.get,
      DNPM.Connector.getInstance.get,
      BfArM.Connector.getInstance.get
    )

  Runtime.getRuntime.addShutdownHook(
    new Thread: 
      override def run =
        println("Shutting down MVH Reporting service...")
        service.stop
  )

  service.start



class MVHReportingService
(
  config: Config,
  queue: ReportQueue,
  dnpm: DNPM.Connector,
  bfarm: BfArM.Connector
)(
  using ec: ExecutionContext
)
extends Logging:

  import DNPM.UseCase._
  import BfArM.SubmissionReport.DiseaseType._


  private val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor

  private var scheduledTask: Option[JavaFuture[?]] = None

  def start: Unit =
    log.info("Starting MVH Reporting service")
    scheduledTask =
      Some(
        executor.scheduleAtFixedRate(
          () => {
            for {
              _ <- pollReports
              _ <- uploadReports
            } yield ()
          },
          0,
          config.polling.period,
          config.polling.timeUnit
        )
      )
   
   
  def stop: Unit =
    log.info("Stopping MVH Reporting service")
    scheduledTask.foreach(_.cancel(false))


  extension(uc: DNPM.UseCase)
    def toDiseaseType: BfArM.SubmissionReport.DiseaseType =
      uc match
        case MTB => Oncological
        case RD  => Rare


  private val toBfArMReport: DNPM.SubmissionReport => BfArM.SubmissionReport =

    case DNPM.SubmissionReport(created,site,useCase,ttan,submType,seqType,qcPassed) =>
      BfArM.SubmissionReport(
        created.toLocalDate,
        submType,
        ttan,
        config.submitterIds(site.code),
        config.dataNodeId,
        useCase.toDiseaseType,
        BfArM.SubmissionReport.DataCategory.Clinical,
        seqType,
        qcPassed        
      )
   

  private[core] def pollReports: Future[Unit] =
    log.info("Polling DNPM-SubmissionReports...")
    dnpm.sites
      .flatMap {
        case Right(sites) =>
          Future.sequence(
            sites.map {
              site =>
            
                log.info(s"Polling SubmissionReports of site '${site.display.getOrElse(site.code)}'")
            
                val period =
                  queue.lastPollingTime(site)
                    .map(Period(_))
            
                dnpm.dataSubmissionReports(
                  site,
                  period
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

    
  private[core] def uploadReports: Future[Unit] =
    log.info("Uploading SubmissionReports...")
    Future.sequence(
      queue.entries
        .map(
          report =>
          bfarm
            .upload(toBfArMReport(report))
            .andThen{ 
              case Success(Right(_)) =>
                log.debug("Upload successful")
                queue.remove(report)
            }
        )
    )
    .map(_ => ())


