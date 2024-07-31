package de.dnpm.ccdn.core


import java.time.LocalDateTime
import scala.collection.mutable.Queue
import scala.collection.concurrent.{
  Map,
  TrieMap
}
//import scala.util.chaining._
//import play.api.libs.json.Json.{toJson,prettyPrint}



final class FakeReportQueueProvider extends ReportQueueProvider:
  override def getInstance: ReportQueue =
    FakeReportQueue


object FakeReportQueue extends ReportQueue:


  val pollingTimes: Map[Code[Site],LocalDateTime] =
    TrieMap.empty

  
  val queue: Queue[DNPM.SubmissionReport] =
    Queue.empty


  override def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): this.type =
    pollingTimes.update(site.code,dt)
    this


  override def lastPollingTime(
    site: Coding[Site]
  ): Option[LocalDateTime] =
    pollingTimes.get(site.code)


  override def add(
    t: DNPM.SubmissionReport
  ): this.type =
    queue += t
    this

  override def addAll(
    ts: Seq[DNPM.SubmissionReport]
  ): this.type =
    queue ++= ts//.tapEach(toJson(_) pipe prettyPrint pipe println)
    this


  override def entries: Seq[DNPM.SubmissionReport] =
    queue.toList


  override def remove(
    t: DNPM.SubmissionReport
  ): this.type =
    queue -= t
    this

