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
            for 
              _ <- pollReports
              _ <- uploadReports
            yield ()
          },
          0,
          config.polling.period,
          config.polling.timeUnit
        )
      )
   
   
  def stop: Unit =
    log.info("Stopping MVH Reporting service")
    scheduledTask.foreach(_.cancel(false))


  private val toBfArMReport: DNPM.SubmissionReport => BfArM.SubmissionReport =
    case DNPM.SubmissionReport(created,site,useCase,ttan,submType,seqType,qcPassed) =>
      BfArM.SubmissionReport(
        created.toLocalDate,
        submType,
        ttan,
        config.submitterIds(site.code),
        config.dataNodeId,
        BfArM.SubmissionReport.DataCategory.Clinical,
        useCase match
          case MTB => Oncological
          case RD  => Rare,
        seqType.map {
          case DNPM.SequencingType.Panel    => BfArM.SequencingType.Panel
          case DNPM.SequencingType.Exome    => BfArM.SequencingType.WES
          case DNPM.SequencingType.Genome   => BfArM.SequencingType.WGS
          case DNPM.SequencingType.GenomeLr => BfArM.SequencingType.WGSLr
        } getOrElse(BfArM.SequencingType.None),
        qcPassed        
      )
   

  private[core] def pollReports: Future[Unit] =
    log.info("Polling DNPM-SubmissionReports...")
    dnpm.sites.flatMap {
      case sites: List[Coding[Site]] =>
        Future.sequence(
          sites.map {
            site =>
          
              log.info(s"Polling SubmissionReports of site '${site.display.getOrElse(site.code)}'")
          
              dnpm.dataSubmissionReports(
                site,
                queue.lastPollingTime(site).map(Period(_))
              )
              .andThen {
                case Success(reports: Seq[DNPM.SubmissionReport]) =>
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

      case err: String =>
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
                case Success(_: BfArM.SubmissionReport) =>
                  log.debug("Upload successful")
                  queue.remove(report)
              }
        )
    )
    .map(_ => ())

