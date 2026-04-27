package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicInteger
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

object FakeDIPConnector {
  final val uploadDelayMsec = 100
}

class FakeDIPConnector extends dip.DipConnector
{
  /**
   * There will be an arbitrary number of submissions between 5 and 15
   * that each remote site will return
   */
  var nSubmissions: Int = new Random().nextInt(10) + 5

  /**
   * Counts currently active pseudo confirmations in [[confirmSubmitted()]]
   */
  val nActiveConfirmationWaits = new AtomicInteger(0)
  /**
   * Counts the maximum number of [[nActiveConfirmationWaits]]
   */
  val maxSimultaneousConfirmationWaits = new AtomicInteger(0)

  var confirmationsTakeTime: Boolean = false


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
      Seq.fill(nSubmissions)(rndReport(site,useCase)).asRight
    )

  override def confirmSubmitted(
    report: Submission.Report
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Submission.Report]] =
    if (confirmationsTakeTime) {
      Future {
        nActiveConfirmationWaits.updateAndGet(oldCount => {
          maxSimultaneousConfirmationWaits.updateAndGet(oldMax => Math.max(oldMax, oldCount + 1))
          oldCount + 1
        })
        Thread.sleep(FakeDIPConnector.uploadDelayMsec)
        nActiveConfirmationWaits.updateAndGet(old => old - 1)
        report.asRight
      }
    } else {
      Future.successful(report.asRight)
    }

}
