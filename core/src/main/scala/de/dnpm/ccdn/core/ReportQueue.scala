package de.dnpm.ccdn.core


import java.time.LocalDateTime
import de.dnpm.ccdn.util.{
  SPI,
  SPILoader
}



trait QueueOps[T,Err]:

  def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): this.type


  def lastPollingTime(
    site: Coding[Site]
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



type ReportQueue = QueueOps[DNPM.SubmissionReport,String]

trait ReportQueueProvider extends SPI[ReportQueue]

object ReportQueue extends SPILoader[ReportQueueProvider]

