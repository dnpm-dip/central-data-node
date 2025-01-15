package de.dnpm.ccdn.core


import java.time.LocalDateTime
import scala.collection.mutable.Queue
import scala.collection.concurrent.{
  Map,
  TrieMap
}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.ccdn.core.dip


final class FakeReportQueueProvider extends ReportQueueProvider
{
  override def getInstance: ReportQueue =
    FakeReportQueue
}

object FakeReportQueue extends ReportQueue
{

  val pollingTimes: Map[Coding[Site],LocalDateTime] =
    TrieMap.empty

  
  val queue: Queue[dip.SubmissionReport] =
    Queue.empty


  override def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): this.type = {
    pollingTimes.update(site,dt)
    this
  }

  override def lastPollingTime(
    site: Coding[Site]
  ): Option[LocalDateTime] =
    pollingTimes.get(site)


  override def add(
    t: dip.SubmissionReport
  ): this.type = {
    queue += t
    this
  }

  override def addAll(
    ts: Seq[dip.SubmissionReport]
  ): this.type = {
    queue ++= ts
    this
  }

  override def entries: Seq[dip.SubmissionReport] =
    queue.toList


  override def remove(
    t: dip.SubmissionReport
  ): this.type = {
    queue -= t
    this
  }

}
