package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.concurrent.{
  Future,
  ExecutionContext
}
import cats.syntax.either._
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.model.{
  Id,
  NGSReport,
  Patient,
  HealthInsurance,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN,
  UseCase
}
import de.dnpm.ccdn.core.dip


final class FakeDIPConnectorProvider extends dip.ConnectorProvider
{
  override def getInstance: dip.Connector =
    FakeDIPConnector
}


object FakeDIPConnector extends dip.Connector
{

  private def rndReport(
    site: Code[Site],
    useCase: UseCase.Value
  ): Submission.Report =
    Submission.Report(
      Id[TransferTAN](randomUUID.toString),
      LocalDateTime.now,
      Id[Patient](randomUUID.toString),
      Submission.Report.Status.Unsubmitted,
      Coding[Site](site.value),
      useCase,
      Submission.Type.Initial,
      Some(NGSReport.Type.GenomeLongRead),
      None,
      HealthInsurance.Type.UNK,
      None,
      None
    )


  override def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Submission.Report.Filter
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Seq[Submission.Report]]] =
    Future.successful(
      Seq.fill(4)(rndReport(site,useCase)).asRight
    )

  override def confirmSubmitted(
    report: Submission.Report
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Unit]] =
    Future.successful(().asRight)

}
