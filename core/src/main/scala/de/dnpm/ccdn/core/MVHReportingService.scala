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


@main def run: Unit =

  import scala.concurrent.ExecutionContext.Implicits.global

  val service =
    new MVHReportingService(
      Config.instance,
      ReportQueue.getInstance.get,
      DNPM.Connector.getInstance.get,
      BfArM.Connector.getInstance.get
    )

  service.start

  while (true){ }





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

  private val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor

  private var task: Option[JavaFuture[?]] = None

  def start: Unit =
    task =
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
    task.foreach(_.cancel(false))



  val toDiseaseType =
    Map(
      DNPM.SubmissionReport.Domain.Oncology     -> BfArM.SubmissionReport.DiseaseType.Oncological,
      DNPM.SubmissionReport.Domain.RareDiseases -> BfArM.SubmissionReport.DiseaseType.Rare
    ) 

  val toBfArMReport: DNPM.SubmissionReport => BfArM.SubmissionReport =

    case DNPM.SubmissionReport(created,site,domain,ttan,submType,seqType,qcPassed) =>
      BfArM.SubmissionReport(
        created.toLocalDate,
        submType,
        ttan,
        config.submitterIds(site.code),
        config.dataNodeId,
        toDiseaseType(domain),
        BfArM.SubmissionReport.DataCategory.Clinical,
        seqType,
        qcPassed        
      )
   

  private[core] def pollReports: Future[Unit] =
    log.info("Polling DNPM-SubmissionReports...")
    dnpm.siteInfos
      .flatMap {
        case Right(siteInfos) =>
          Future.sequence(
            siteInfos.map {
              (site,domains) =>
            
                log.info(s"Polling SubmissionReports of site '${site.display.getOrElse(site.code)}'")
            
                val period =
                  queue.lastPollingTime(site)
                    .map(Period(_))
            
                dnpm.dataSubmissionReports(
                  site,
                  domains,
                  period
                )
                .andThen {
                  case Success(Right(reports)) =>
                    log.debug(s"Enqueueing ${reports.size} reports")
                    queue
                      .addAll(reports)
                      .setLastPollingTime(site,now)
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


