package de.dnpm.ccdn.connector

import de.dnpm.ccdn.core.{bfarm}
import de.dnpm.ccdn.core.bfarm.{CDN, SubmissionReport}
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.{Submission, TransferTAN}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import java.time.LocalDateTime
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{Duration => AwaitTimeout}
import scala.concurrent.{Await, ExecutionContext, Future}

class BfArMConnectorImplTests extends AsyncFlatSpec
  with AsyncTestSuite
  with AsyncMockFactory
{
  behavior of "BfArMConnectorProviderImpl"

  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def makeFakeReport: bfarm.SubmissionReport = {
    import bfarm.LibraryType
    import bfarm.SubmissionReport.DiseaseType.Oncological
    bfarm.SubmissionReport(
      LocalDateTime.now.toLocalDate,
      Submission.Type.Initial,
      Id[TransferTAN](randomUUID.toString),
      Id[Site]("260832299"),
      Id[CDN]("KDKTUE005"),
      Oncological,
      LibraryType.WGSLr,
      HealthInsurance.Type.UNK
    )
  }

  //how many documents are uploaded
  val nUploads = 10
  //increments every time a token is fetched, should increase once
  val tokenFetchCounter = new AtomicInteger(0)
  val bfarmConnectorConfig = BfArMConnectorImpl.Config(
    "apiURL", "authURL", "klient", "geh heim", Some(1)
  )

  //member names coincide with json fields and the object is built from them.
  // So building the object from JSON ensures nobody changes field names

  val pseudoToken:JsValue = Json.obj(
    "access_token"      -> "babelub",
    "expires_in"        -> 133742,
    "refresh_expires_in"-> 133742,
    "scope"             -> "monocle",
    "token_type"        -> "asdbest")
  //Could also define the token like following, but the way in use also ensures
  // that nobody changes the field names when JSON serialization depends on them
  // coinciding with the JSON coming from the identityprovider
  /*implicit val tokenWrites:Writes[Token] = Json.writes[Token]
  val pseudoToken2:JsValue = Json.toJson( BfArMConnectorImpl.Token(
    "babelub",133742,133742,"monocle","asdbest"))*/

  trait CustomRequest extends StandaloneWSRequest with DefaultBodyWritables {
    type Self = CustomRequest
    type Response = CustomResponse
  }
  trait CustomResponse extends StandaloneWSResponse {
  }

  val mockHttpClient:StandaloneWSClient = stub[StandaloneWSClient]

  //Auth mocks
  private val mockAuthRequest = stub[CustomRequest]
  private val mockAuthResponse = stub[mockAuthRequest.Response]
  (mockHttpClient.url _).when("authURL").returns(mockAuthRequest)
  (mockAuthRequest.withRequestTimeout _).when(*).returns(mockAuthRequest)
  (mockAuthRequest.post(_:Map[String,Seq[String]])(_:BodyWritable[Map[String,Seq[String]]]))
    .when(*,*)
    .onCall(_ => {
      tokenFetchCounter.incrementAndGet()
      Future.successful(mockAuthResponse)
    })
  (() => mockAuthResponse.status).when().returns(200)
  //point of this test: token should only be requested once
  (mockAuthResponse.body[JsValue](_:BodyReadable[JsValue]))
    .when(*).returns(pseudoToken)

  //Upload mocks
  private val mockUploadRequest = stub[CustomRequest]
  private val mockUploadResponse = stub[mockUploadRequest.Response]
  (mockHttpClient.url _).when("apiURL").returns(mockUploadRequest)
  (mockUploadRequest.withHttpHeaders _).when(*).returns(mockUploadRequest)
  (mockUploadRequest.withRequestTimeout _).when(*).returns(mockUploadRequest)
  (mockUploadRequest.post(_:Map[String,Seq[String]])(_:BodyWritable[Map[String,Seq[String]]]))
    .when(*,*).returns(Future.successful(mockUploadResponse))
  (() => mockUploadResponse.status).when().returns(200)

  val toTest = new BfArMConnectorImpl(
    bfarmConnectorConfig,
    mockHttpClient)

  it must "only fetch one token to make multiple uploads" in {
    assert(tokenFetchCounter.get() == 0)
    val testSubmissions:List[SubmissionReport] = List.fill(nUploads) (makeFakeReport)
    val allUploads = testSubmissions.map(it => toTest.upload(it))
    for (res <- Await.result(Future.sequence(allUploads),AwaitTimeout(5,TimeUnit.SECONDS))) {
      //reading out the results is actually needed so that all upload threads finish
      assert(res.isRight)
    }
    assert(tokenFetchCounter.get() == 1,
      "This means the token wasnt fetched the expected number of times (once)")
  }
}
