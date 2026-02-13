package de.dnpm.ccdn.connector

import de.dnpm.ccdn.connector.BfArMFakeReportFactory.makeFakeReport
import de.dnpm.ccdn.core.bfarm.SubmissionReport
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncTestSuite
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{Duration => AwaitTimeout}
import scala.concurrent.{Await, ExecutionContext, Future}

class BfArMConnectorImplTests extends AsyncFlatSpec
  with AsyncTestSuite
  with AsyncMockFactory
{
  //uses custom config.json
  behavior of "BfArMConnectorProviderImpl"

  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val nUploads = 10 //how many documents are uploaded
  val tokenFetchCounter = new AtomicInteger(0) //increments every time a token is fetched, should increase once
  val bfarmConnectorConfig = BfArMConnectorImpl.Config(
    "apiURL", "authURL", "klient", "geh heim", Some(1)
  )

  val pseudoToken:JsValue = Json.obj(
    "access_token"->  Json.toJson("Kreditkarte"),
    "expires_in"->  Json.toJson(133742),
    "refresh_expires_in"->  Json.toJson(133742),
    "scope"->  Json.toJson("monocle"),
    "token_type"->  Json.toJson("Hammer"))

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
    assert(tokenFetchCounter.get() == 1,"This means the token wasnt fetched the expected number of times (once)")
  }
}
