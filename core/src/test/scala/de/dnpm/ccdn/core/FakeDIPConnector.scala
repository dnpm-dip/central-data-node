package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.util.Random
import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.either._
import de.dnpm.dip.coding.{Code, Coding}
import de.dnpm.dip.model.{HealthInsurance, Id, NGSReport, Patient, Site}
import de.dnpm.dip.service.mvh.{Submission, TransferTAN, UseCase}


final class FakeDIPConnectorProvider extends dip.DipConnectorProvider
{
  override def getInstance: dip.DipConnector =
    new FakeDIPConnector
}

class FakeDIPConnector extends dip.DipConnector
{

  private val rnd = new Random


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
    // Return between 5 and 15 of Submission.Reports 
    Future.successful(
      Seq.fill(rnd.nextInt(10) + 5)(rndReport(site,useCase)).asRight
    )

  override def confirmSubmitted(
    report: Submission.Report
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Submission.Report]] =
    Future.successful(report.asRight)

}
