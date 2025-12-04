package de.dnpm.ccdn.core


import java.time.LocalDateTime
import scala.collection.mutable.Queue
import scala.collection.concurrent.{
  Map,
  TrieMap
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.Submission


final class FakeReportQueueProvider extends ReportQueueProvider
{
  override def getInstance: ReportQueue =
    FakeReportQueue
}

object FakeReportQueue extends ReportQueue
{

  val pollingTimes: Map[Code[Site],LocalDateTime] =
    TrieMap.empty

  
  val queue: Queue[Submission.Report] =
    Queue.empty


  override def setLastPollingTime(
    site: Code[Site],
    dt: LocalDateTime
  ): this.type = {
    pollingTimes.update(site,dt)
    this
  }

  override def lastPollingTime(
    site: Code[Site]
  ): Option[LocalDateTime] =
    pollingTimes.get(site)


  override def add(
    t: Submission.Report
  ): this.type = {
    queue += t
    this
  }

  override def addAll(
    ts: Seq[Submission.Report]
  ): this.type = {
    queue ++= ts
    this
  }

  override def entries: Seq[Submission.Report] =
    queue.toList


  override def remove(
    t: Submission.Report
  ): this.type = {
    queue -= t
    this
  }

}
