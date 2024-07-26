package de.dnpm.ccdn.core


import java.time.LocalDateTime
import scala.collection.mutable.Queue
import scala.collection.concurrent.{
  Map,
  TrieMap
}



final class FakeRepositoryProvider extends RepositoryProvider:
  override def getInstance: Repository =
    FakeRepository


object FakeRepository extends Repository:


  val pollingTimes: Map[Code[Site],LocalDateTime] =
    TrieMap.empty

  
  val queue: Queue[DNPM.SubmissionReport] =
    Queue.empty



  override def setLastPollingTime(
    site: Coding[Site],
    dt: LocalDateTime
  ): Either[String,Boolean] =
    pollingTimes.update(site.code,dt)
    Right(true)


  def lastPollingTime(
    site: Coding[Site]
  ): Either[String,LocalDateTime] =
    pollingTimes.get(site.code)
      .toRight("Invalid Site")


  def save(
    report: DNPM.SubmissionReport
  ): Either[String,DNPM.SubmissionReport] =
    queue -= report
    Right(report)


  def save(
    reports: Seq[DNPM.SubmissionReport]
  ): Either[String,Seq[DNPM.SubmissionReport]] =
    queue ++= reports
    Right(reports)


  def submissionReports: Either[String,List[DNPM.SubmissionReport]] =
    Right(queue.to(List))


  def delete(
    report: DNPM.SubmissionReport
  ): Either[String,DNPM.SubmissionReport] =
    queue -= report
    Right(report)


