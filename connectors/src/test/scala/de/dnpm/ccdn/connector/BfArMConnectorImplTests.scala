package de.dnpm.ccdn.connector

import de.dnpm.ccdn.core.bfarm
import de.dnpm.ccdn.core.bfarm.CDN
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.{Submission, TransferTAN}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import java.time.LocalDate
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

class BfArMConnectorImplTests extends AsyncFlatSpec
  with AsyncMockFactory
  with BeforeAndAfter
{
  behavior of "BfArMConnectorProviderImpl"


  private def makeFakeReport: bfarm.SubmissionReport = {
    import bfarm.LibraryType
    import bfarm.SubmissionReport.DiseaseType.Oncological
    bfarm.SubmissionReport(
      LocalDate.now,
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
  val LongTokenLifetime = 133742
  val ZeroTokenLifetime = 5 //BfArMConnectorImpl clears the token 5 seconds before formal expiration
  val apiUrlAddress ="somewhere"
  val authUrlAddress ="someplaceelse"
  //increments every time a token is fetched, should increase once
  val tokenFetchCounter = new AtomicInteger(0)
  val bfarmConnectorConfig = BfArMConnectorImpl.Config(
    apiUrlAddress, authUrlAddress, "clientValue", "geh heim", Some(1)
  )

  trait CustomRequest extends StandaloneWSRequest with DefaultBodyWritables {
    type Self = CustomRequest
    type Response = CustomResponse
  }
  trait CustomResponse extends StandaloneWSResponse { }

  val mockHttpClient:StandaloneWSClient = stub[StandaloneWSClient]

  private def makeToken(tokenExpiration:Int):JsValue =
    Json.obj(
      "access_token" -> "babelub",
      "expires_in" -> tokenExpiration,
      "refresh_expires_in" -> tokenExpiration,
      "scope" -> "monocle",
      "token_type" -> "asdbest")
  before{
    tokenFetchCounter.set(0)
  }

  //Auth mocks
  private def prepAuthMocks(tokenExpiration:Int) = {
    val pseudoToken:JsValue = makeToken(tokenExpiration)
    val mockAuthRequest = stub[CustomRequest]
    val mockAuthResponse = stub[mockAuthRequest.Response]
    (mockHttpClient.url _).when(authUrlAddress).returns(mockAuthRequest)
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
  }

  //Upload mocks
  private val mockUploadRequest = stub[CustomRequest]
  private val mockUploadResponse = stub[mockUploadRequest.Response]
  (mockHttpClient.url _).when(apiUrlAddress).returns(mockUploadRequest)
  (mockUploadRequest.withHttpHeaders _).when(*).returns(mockUploadRequest)
  (mockUploadRequest.withRequestTimeout _).when(*).returns(mockUploadRequest)
  (mockUploadRequest.post(_:Map[String,Seq[String]])(_:BodyWritable[Map[String,Seq[String]]]))
    .when(*,*).returns(Future.successful(mockUploadResponse))
  (() => mockUploadResponse.status).when().returns(200)

  val toTest = new BfArMConnectorImpl(
    bfarmConnectorConfig,
    mockHttpClient)

  ////////////////////////////////////////////////// Testcases

  it must "only fetch one token to make multiple uploads in short time" in {
    prepAuthMocks(LongTokenLifetime)

    //TODO inject prepAuthmocks and prepUploadmocks somehow into toTest and instantiate that thing in here.......

    assert(tokenFetchCounter.get() == 0)
    val submissionReports = List.fill(nUploads)(makeFakeReport)

    for {
      _ <- Future.traverse(submissionReports)(toTest.upload)
    } yield tokenFetchCounter.get mustBe 1
    assert(tokenFetchCounter.get() == 1) //TODO redundant
  }

  it must "fetch a new token for subsequent uploads if it expires during a series of uploads" in {
    prepAuthMocks(ZeroTokenLifetime)

    //TODO as above

    for{
      _ <- assert(tokenFetchCounter.get() == 0)
      _ <- toTest.upload(makeFakeReport)
      _ <- assert(tokenFetchCounter.get() == 1)
      _ <- toTest.upload(makeFakeReport)
      _ <- assert(tokenFetchCounter.get() == 2)
    } yield succeed
  }

  "Token" must "be deserializable from the JSON fields that sent from BfArM" in {
    //member names coincide with json fields and the object is built from them.
    // So we should ensure nobody changes field names

    import BfArMConnectorImpl.Token
    val expectedTokenLifetime = 123465 //TODO have a series of values for this or randomize every time
    val functioningToken = Json.fromJson[Token](makeToken(expectedTokenLifetime))
    assert(functioningToken.isSuccess)
    assert(functioningToken.get.access_token == "babelub")
    assert(functioningToken.get.expires_in == expectedTokenLifetime)
  }
}
