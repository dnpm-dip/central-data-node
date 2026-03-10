package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.either._
import de.dnpm.dip.coding.{Code, Coding}
import de.dnpm.dip.model.{HealthInsurance, Id, NGSReport, Patient, Site}
import de.dnpm.dip.service.mvh.{Submission, TransferTAN, UseCase}

import java.util.concurrent.atomic.AtomicInteger

final class FakeDIPConnectorProvider extends dip.ConnectorProvider
{
  override def getInstance: dip.Connector =
    FakeDIPConnector()
}

object FakeDIPConnector {
  final val uploadDelayMsec = 100
}

case class FakeDIPConnector() extends dip.Connector
{
  /**
   * Describes how many submissions will be created for each request site and usecase
   * (see resources/config.json), so the actual number of submissions is 39 times this
   */
  var nSubmissions:Int = 4

  val nActiveConfirmationWaits = new AtomicInteger(0)
  val maxSimultaneousConfirmationWaits = new AtomicInteger(0)

  //val confirmationFinishTimings = new AtomicReference(List[Long]())
  /**
   * If true, every call to confirmSubmitted will wait [[FakeDIPConnector.uploadDelayMsec]]
   * milliseconds before setting it's returned future to something. Also activates counting active threads
   */
  var confirmationsTakeTime:Boolean = false


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
  ): Future[Either[String,Unit]] =
    if (confirmationsTakeTime) {
      Future{
        nActiveConfirmationWaits.updateAndGet(oldCount => {
          maxSimultaneousConfirmationWaits.updateAndGet(oldMax => Math.max(oldMax,oldCount+1))
          oldCount+1
        })
        Thread.sleep(FakeDIPConnector.uploadDelayMsec)
        nActiveConfirmationWaits.updateAndGet(old => old-1)
        ().asRight
      }
    } else {
      Future.successful(().asRight)
    }

}
