package de.dnpm.ccdn.connector


import java.io.{
  File,
  FileInputStream,
  FileWriter
}

import java.time.LocalDateTime
import scala.collection.mutable.Queue
import scala.collection.concurrent.{
  Map,
  TrieMap
}
import scala.util.{
  Try,
  Failure,
  Using
}
import scala.util.chaining._
import play.api.libs.json.{
  Json,
  Writes
}
import de.dnpm.dip.util.Logging
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.Submission
import de.dnpm.ccdn.core.{
  ReportQueue,
  ReportQueueProvider
}


final class ReportQueueProviderImpl extends ReportQueueProvider
{
  override def getInstance: ReportQueue =
    FSBackedReportQueue.instance
}


object FSBackedReportQueue extends Logging
{

  private val PROP = "ccdn.queue.dir"

  private val ENV = "CCDN_QUEUE_DIR"

  lazy val instance =
    Try(new File(System getenv ENV))
      .orElse(Try(new File(System getProperty PROP)))
      .map(new FSBackedReportQueue(_))
      .recoverWith { 
        case t =>
          log.error(s"Couldn't set up Report Queue, most likely due to undefined property '$ENV'",t)
          Failure(t)
      }
      .get
}   


final class FSBackedReportQueue(
  val dir: File
)
extends ReportQueue
with Logging
{

  dir.mkdirs

  private val pollingTimesFile: File =
    new File(dir,"PollingTimes.json")


  private val pollingTimes: Map[String,LocalDateTime] = 
    Try(new FileInputStream(pollingTimesFile))
      .map(Json.parse)
      .map(Json.fromJson[scala.collection.immutable.Map[String,LocalDateTime]](_).get)
      .map(TrieMap.from)
      .getOrElse(TrieMap.empty)

      
  private val queue: Queue[Submission.Report] =
    dir.listFiles(
      (_,name) => name.startsWith("Report_") && name.endsWith(".json")
    )
    .to(LazyList)
    .map(new FileInputStream(_))
    .map(Json.parse)
    .map(Json.fromJson[Submission.Report](_))
    .map(_.get)
    .to(Queue)


  private def file(
    report: Submission.Report
  ): File =
    new File(dir,s"Report_${report.site.code}_${report.id}.json")
    
  private def save[T: Writes](
    t: T,
    file: File
  ): Unit = {

    Using(new FileWriter(file)){
      w =>
        Json.toJson(t)
          .pipe(Json.prettyPrint)
          .tap(w.write)
    }
    ()
  }
    

  override def setLastPollingTime(
    site: Code[Site],
    dt: LocalDateTime
  ): this.type = {
    Try(pollingTimes += site.toString -> dt)
      .map(save(_,pollingTimesFile))
      .recover { 
        case t => log.warn("Problem updating polling times",t)
      }

    this
  }


  override def lastPollingTime(
    site: Code[Site]
  ): Option[LocalDateTime] =
    pollingTimes.get(site.toString)


  override def add(
    report: Submission.Report
  ): this.type = {

    // Don't enqueue the same report twice
    if (!queue.exists(_ == report))
      Try(queue += report)
        .map(_ => save(report,file(report)))

    this
  }

  override def addAll(
    reports: Seq[Submission.Report]
  ): this.type = {
    reports.foreach(add)
    this
  }


  override def entries: Seq[Submission.Report] =
    queue.toList


  override def remove(
    report: Submission.Report
  ): this.type = {
    Try(queue -= report)
      .map(_ => file(report).delete)
      .recover { 
        case t =>
          log.warn("Problem removing report", t)
          false
      }

    this
  }

}
