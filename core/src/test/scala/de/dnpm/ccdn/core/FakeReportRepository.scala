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
  
  val queue: Map[(Code[Site],Id[TransferTAN]),Submission.Report] =
    TrieMap.empty


  override def save(report: Submission.Report): Either[String,Unit] = {
    queue += (report.site.code,report.id) -> report
    Right(())
  }

  override def save(reports: Seq[Submission.Report]): EitherNel[Submission.Report,Unit] = {
    reports.foreach(save)
    Right(())
  }

  override def entries(f: Submission.Report => Boolean): Seq[Submission.Report] =
    queue.values.filter(f).toSeq


  override def remove(report: Submission.Report): Either[String,Unit] = {
    queue -= (report.site.code -> report.id)
    Right(())
  }

}
