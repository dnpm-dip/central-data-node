package de.dnpm.ccdn.core


import scala.collection.concurrent.{
  Map,
  TrieMap
}
import cats.data.EitherNel
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Id,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN
}


final class FakeReportRepositoryProvider extends ReportRepositoryProvider
{
  override def getInstance: ReportRepository =
    FakeReportRepository
}

object FakeReportRepository extends ReportRepository
{

  type Key = (Code[Site],Id[TransferTAN])

  private val cache: Map[Key,Submission.Report] =
    TrieMap.empty


  override def saveIfAbsent(
    key: Key,
    report: Submission.Report
  ): Either[String,Unit] = {
    cache.putIfAbsent(key,report)
    Right(())
  }

  override def saveIfAbsent(
    reports: Seq[Submission.Report],
    key: Submission.Report => Key
  ): EitherNel[Submission.Report,Unit] = {
    reports.foreach(r => saveIfAbsent(key(r),r))
    Right(())
  }

  override def replace(
    key: Key,
    report: Submission.Report
  ): Either[String,Unit] = {
    cache += key -> report
    Right(())
  }

  override def entries(f: Submission.Report => Boolean): Seq[Submission.Report] =
    cache.values.filter(f).toSeq


  override def remove(key: Key): Either[String,Unit] = {
    cache -= key
    Right(())
  }

}
