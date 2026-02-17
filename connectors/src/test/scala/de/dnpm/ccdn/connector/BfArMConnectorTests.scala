package de.dnpm.ccdn.connector


import java.time.LocalDate
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.scalamock.scalatest.AsyncMockFactory
import play.api.libs.json.{
  Json,
  JsValue,
  OWrites
}
import play.api.libs.ws.{
  BodyReadable,
  BodyWritable,
  StandaloneWSClient => WSClient,
  StandaloneWSRequest => WSRequest,
  StandaloneWSResponse => WSResponse
}
import play.api.libs.ws.DefaultBodyWritables
import de.dnpm.dip.model.{
  HealthInsurance,
  Id,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN
}
import de.dnpm.ccdn.core.bfarm.{
  CDN,
  LibraryType,
  SubmissionReport
}



trait FakeResponse extends WSResponse

trait FakeRequest extends WSRequest with DefaultBodyWritables {
  type Self = FakeRequest
  type Response = FakeResponse
}


class BfArMConnectorTests extends AsyncFlatSpec with AsyncMockFactory
{

  implicit val writesToken: OWrites[BfArMConnectorImpl.Token] =
    Json.writes[BfArMConnectorImpl.Token]

  val token =
    BfArMConnectorImpl.Token(
      access_token = "dummy",
      expires_in = 300,
      refresh_expires_in = 300,
      scope = "test",
      token_type = "Bearer"
    )

  def rndSubmissionReport: SubmissionReport =
    SubmissionReport(
      LocalDate.now,
      Submission.Type.Test,
      Id[TransferTAN](randomUUID.toString),
      Id[Site]("dummy"),
      Id[CDN]("dummy"),
      SubmissionReport.DiseaseType.Oncological,
      LibraryType.None,
      HealthInsurance.Type.UNK
    )


  val config =
    BfArMConnectorImpl.Config(
      "http://localhost/api/submission",
      "http://localhost/auth/token",
      "client-id",
      "changeit",
      Some(1)
    )


  // Test purpose: Ensure that the token fetch request is performed only once even on multiple uploads
  val tokenFetchCounter = new AtomicInteger(0)


  val wsclient = stub[WSClient]

  // Token request stubs
  val tokenRequest = stub[FakeRequest]
  val tokenResponse = stub[tokenRequest.Response]

  (wsclient.url _).when(config.authURL).returns(tokenRequest)
  (tokenRequest.withRequestTimeout _).when(*).returns(tokenRequest)
  (tokenRequest.post(_: Map[String,Seq[String]])(_: BodyWritable[Map[String,Seq[String]]]))
    .when(*,*)
    .onCall(_ => {
      tokenFetchCounter.incrementAndGet()
      Future.successful(tokenResponse)
    })
  (() => tokenResponse.status).when().returns(200)
  (tokenResponse.body[JsValue](_: BodyReadable[JsValue])).when(*).returns(Json.toJson(token))

  // Upload request stubs
  private val uploadRequest = stub[FakeRequest]
  private val uploadResponse = stub[uploadRequest.Response]

  (wsclient.url _).when(config.apiURL).returns(uploadRequest)
  (uploadRequest.withHttpHeaders _).when(*).returns(uploadRequest)
  (uploadRequest.withRequestTimeout _).when(*).returns(uploadRequest)
  (uploadRequest.post(_: JsValue)(_: BodyWritable[JsValue])).when(*,*).returns(Future.successful(uploadResponse))
  (() => uploadResponse.status).when().returns(200)


  val connector =
    new BfArMConnectorImpl(
      config,
      wsclient
    )


  "API Token" must "have been fetched only once on multiple upload calls" in {

    val submissionReports = List.fill(20)(rndSubmissionReport)

    for {
      _ <- Future.traverse(submissionReports)(connector.upload)
    } yield tokenFetchCounter.get mustBe 1

  }

}
