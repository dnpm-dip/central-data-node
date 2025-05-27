package de.dnpm.ccdn.core


import java.time.LocalDateTime
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.coding.Code
//import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.Submission


trait QueueOps[T]
{

  def setLastPollingTime(
    site: Code[Site],
    dt: LocalDateTime
  ): this.type


  def lastPollingTime(
    site: Code[Site]
  ): Option[LocalDateTime]


  def add(
    t: T
  ): this.type

  def addAll(
    ts: Seq[T]
  ): this.type

  def entries: Seq[T]

  def remove(
    t: T
  ): this.type

}

trait ReportQueue extends QueueOps[Submission.Report]

trait ReportQueueProvider extends SPI[ReportQueue]

object ReportQueue extends SPILoader[ReportQueueProvider]

