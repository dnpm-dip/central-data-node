package de.dnpm.ccdn.connector

import de.dnpm.ccdn.core.bfarm.SubmissionReport
import de.dnpm.ccdn.core.{Config, bfarm}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model._
import de.dnpm.dip.service.mvh.{Submission, TransferTAN, UseCase}
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest}

import java.time.LocalDateTime
import java.util.UUID.randomUUID

class BfArMConnectorImplTests extends AsyncFlatSpec with TestSuite with MockFactory /* Stubs? */ {
  //uses custom config.json

  trait CustomRequest extends StandaloneWSRequest {
    type Self = CustomRequest
  }

  private def rndReport: Submission.Report =
    Submission.Report(
      Id[TransferTAN](randomUUID.toString),
      LocalDateTime.now,
      Id[Patient](randomUUID.toString),
      Submission.Report.Status.Unsubmitted,
      Coding[Site]("UKFR"),
      UseCase.MTB,
      Submission.Type.Initial,
      Some(NGSReport.Type.GenomeLongRead),
      None,
      HealthInsurance.Type.UNK,
      None,
      None
    )
  private val BfarmReport: Submission.Report => bfarm.SubmissionReport = {

    import NGSReport.Type._
    import bfarm.LibraryType
    import bfarm.SubmissionReport.DiseaseType._
    import de.dnpm.dip.service.mvh.UseCase._

    report =>
      bfarm.SubmissionReport(
        report.createdAt.toLocalDate,
        report.`type`,
        report.id,
        Config.instance.submitterId(report.site.code),
        Config.instance.dataNodeIds(report.useCase),
        report.useCase match {
          case MTB => Oncological
          case RD  => Rare
        },
        report.sequencingType.collect {
            case GenomeLongRead  => LibraryType.WGSLr
            case GenomeShortRead => LibraryType.WGS
            case Exome           => LibraryType.WES
            case Panel           => LibraryType.Panel
          }
          .getOrElse(LibraryType.None),
        report.healthInsuranceType
      )
  }

  val bfarmConnectorConfig = BfArMConnectorImpl.Config(
    "apiURL","authURL","klient","geh heim",Some(1)
  )

  val testSubmissions:List[SubmissionReport] = List.fill(10) (BfarmReport(rndReport))
  val mockHttpClient:StandaloneWSClient = mock[StandaloneWSClient]
  val mockRequest = mock[CustomRequest]
  (mockHttpClient.url _).expects("authURL").returns(mockRequest)

  val toTest = new BfArMConnectorImpl(
    bfarmConnectorConfig,
    mockHttpClient)
  for (curSubm <- testSubmissions) {
    toTest.upload(curSubm)
  }



}
