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
  Using
}
import scala.util.chaining._
import play.api.libs.json.{
  Json,
  Writes
}
import de.dnpm.ccdn.util.Logging
import de.dnpm.ccdn.core.{
  Code,
  Coding,
  DNPM,
  ReportQueue,
  ReportQueueProvider,
  Site
}


final class ReportQueueProviderImpl extends ReportQueueProvider: 
  override def getInstance: ReportQueue =
    FSBackedReportQueue.instance


object FSBackedReportQueue:

  lazy val instance =
    new FSBackedReportQueue(
      new File(System.getProperty("dnpm.ccdn.queue.dir"))
    )



final class FSBackedReportQueue
(
  val dir: File
)
extends ReportQueue
with Logging:

  dir.mkdirs

  private val pollingTimesFile: File =
    new File(dir,"PollingTimes.json")


  private val pollingTimes: Map[Code[Site],LocalDateTime] = 
    Try(new FileInputStream(pollingTimesFile))
      .map(Json.parse)
      .map(Json.fromJson[scala.collection.immutable.Map[Code[Site],LocalDateTime]](_).get)
      .map(TrieMap.from)
      .getOrElse(TrieMap.empty)
    

  private val queue: Queue[DNPM.SubmissionReport] =
    dir.listFiles(
      (_,name) => name.startsWith("Report_") && name.endsWith(".json")
    )
    .to(LazyList)
    .map(new FileInputStream(_))
    .map(Json.parse)
    .map(Json.fromJson[DNPM.SubmissionReport](_))
    .map(_.get)
    .to(Queue)


  private def file(
    report: DNPM.SubmissionReport
  ): File =
    new File(dir,s"Report_${report.site.code}_${report.transferTAN}.json")
    
  private def save[T: Writes](
    t: T,
    file: File
  ): Unit =
    Using(new FileWriter(file)){
      w =>
        Json.toJson(t)
          .pipe(Json.prettyPrint)
          .tap(w.write)
    }
    

  override def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): this.type =
    (pollingTimes += site.code -> dt)
      .tap(save(_,pollingTimesFile))
    this


  override def lastPollingTime(
    site: Coding[Site]
  ): Option[LocalDateTime] =
    pollingTimes.get(site.code)


  override def add(
    t: DNPM.SubmissionReport
  ): this.type = 
    save(t,file(t))
    queue += t
    this


  override def addAll(
    ts: Seq[DNPM.SubmissionReport]
  ): this.type =
    ts.foreach(add)
    this


  override def entries: Seq[DNPM.SubmissionReport] =
    queue.toList


  override def remove(
    t: DNPM.SubmissionReport
  ): this.type =
    file(t).delete
    queue -= t
    this

