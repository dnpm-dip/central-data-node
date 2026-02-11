package de.dnpm.ccdn.connector

import de.dnpm.ccdn.core.bfarm.SubmissionReport
import de.dnpm.ccdn.core.{Config, bfarm}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model._
import de.dnpm.dip.service.mvh.{Submission, TransferTAN, UseCase}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.JsonBodyReadables.readableAsJson
import play.api.libs.ws._

import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.concurrent.Future

class BfArMConnectorImplTests extends AsyncFlatSpec
  with AsyncTestSuite
  with AsyncMockFactory
{
  //uses custom config.json
  val nUploads = 1

  val pseudoToken:JsValue = Json.obj(
    "access_token"->  Json.toJson("Kreditkarte"),
    "expires_in"->  Json.toJson(133742),
    "refresh_expires_in"->  Json.toJson(133742),
    "scope"->  Json.toJson("monocle"),
    "token_type"->  Json.toJson("Hammer")


  )

  trait CustomRequest extends StandaloneWSRequest with DefaultBodyWritables {
    type Self = CustomRequest
    type Response = CustomResponse

  }
  trait CustomResponse extends StandaloneWSResponse {
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

  val testSubmissions:List[SubmissionReport] = List.fill(nUploads) (BfarmReport(rndReport))
  val mockHttpClient:StandaloneWSClient = mock[StandaloneWSClient]
  val mockAuthRequest = mock[CustomRequest]
  val mockAuthResponse = mock[mockAuthRequest.Response]


  (mockHttpClient.url _).expects("authURL").returns(mockAuthRequest)
  (mockAuthRequest.withRequestTimeout _).expects(*).returns(mockAuthRequest)
  (mockAuthRequest.post(_:Map[String,Seq[String]])(_:BodyWritable[Map[String,Seq[String]]]))
    .expects(*,*).returns(Future.successful(mockAuthResponse))
  (() => mockAuthResponse.status).expects().returns(200)
  (() => mockAuthResponse.body[JsValue]).expects().returns(pseudoToken)

  val toTest = new BfArMConnectorImpl(
    bfarmConnectorConfig,
    mockHttpClient)
  for (curSubm <- testSubmissions) {
    toTest.upload(curSubm)
  }



}
