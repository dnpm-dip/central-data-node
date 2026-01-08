package de.dnpm.ccdn.core


import scala.collection.concurrent.{
  Map,
  TrieMap
}
import cats.data.EitherNel
import de.dnpm.dip.service.mvh.Submission


final class FakeReportRepositoryProvider extends ReportRepositoryProvider
{
  override def getInstance: ReportRepository =
    FakeReportRepository
}

object FakeReportRepository extends ReportRepository
{

  private val cache: Map[Key,Submission.Report] =
    TrieMap.empty


  override def saveIfAbsent(
    report: Submission.Report
  ): Either[String,Unit] = {
    cache.putIfAbsent(key(report),report)
    Right(())
  }

  override def saveIfAbsent(
    reports: Seq[Submission.Report],
  ): EitherNel[Submission.Report,Unit] = {
    reports.foreach(saveIfAbsent)
    Right(())
  }

  override def replace(
    report: Submission.Report
  ): Either[String,Unit] = {
    cache += key(report) -> report
    Right(())
  }

  override def entries(f: Submission.Report => Boolean): Seq[Submission.Report] =
    cache.values.filter(f).toSeq


  override def remove(report: Submission.Report): Either[String,Unit] = {
    cache -= key(report)
    Right(())
  }

}
