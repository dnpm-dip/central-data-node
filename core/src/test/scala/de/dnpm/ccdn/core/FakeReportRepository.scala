package de.dnpm.ccdn.core


import scala.collection.concurrent.{Map, TrieMap}
import cats.data.EitherNel
import de.dnpm.ccdn.core.dip.Report


final class FakeReportRepositoryProvider extends ReportRepositoryProvider
{
  override def getInstance: ReportRepository =
    FakeReportRepository()
}

case class FakeReportRepository() extends ReportRepository
{

  private val cache: Map[Key,Report] =
    TrieMap.empty


  override def saveIfAbsent(
    report: Report
  ): Either[String,Unit] = {
    cache.putIfAbsent(key(report),report)
    Right(())
  }

  override def saveIfAbsent(
    reports: Seq[Report],
  ): EitherNel[Report,Unit] = {
    reports.foreach(saveIfAbsent)
    Right(())
  }

  override def replace(
    report: Report
  ): Either[String,Unit] = {
    cache += key(report) -> report
    Right(())
  }

  override def entries(f: Report => Boolean): Seq[Report] =
    cache.values.filter(f).toSeq


  override def removeFromQueue(report: Report): Either[String,Unit] = {
    cache -= key(report)
    Right(())
  }

}
