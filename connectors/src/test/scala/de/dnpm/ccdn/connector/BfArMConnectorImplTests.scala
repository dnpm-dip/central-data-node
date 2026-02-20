package de.dnpm.ccdn.connector

import de.dnpm.ccdn.connector.BfArMConnectorImpl.Token
import de.dnpm.ccdn.core.bfarm
import de.dnpm.ccdn.core.bfarm.CDN
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.{Submission, TransferTAN}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsValue, Json, Writes}
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
  val LongTokenLifetime = 133742 //random amount of seconds long enough to run the test
  val ZeroTokenLifetime = 5 //BfArMConnectorImpl clears the token 5 seconds before formal expiration
  val apiUrlAddress ="somewhere" //just random values
  val authUrlAddress ="someplaceelse"
  //increments every time a token is fetched, should increase once
  val bfarmConnectorConfig = BfArMConnectorImpl.Config(
    apiUrlAddress, authUrlAddress, "clientValue", "geh heim", Some(1)
  )

  trait CustomRequest extends StandaloneWSRequest with DefaultBodyWritables {
    type Self = CustomRequest
    type Response = CustomResponse
  }
  trait CustomResponse extends StandaloneWSResponse { }

  class CustomTestException extends Exception("arsrtfhg")

  implicit val format: Writes[Token] =
    Json.writes[Token]
  private def makeToken(tokenExpiration:Int):Token =
    Token(
      "access_tokenVALUE",
      tokenExpiration.toLong,
      tokenExpiration,
      "scopeVALUE",
      "token_typeVALUE")



  private class TestFixture(tokenExpiration:Int,tokenFetchSucceeds:Boolean) {
    val tokenFetchCounter = new AtomicInteger(0)
    val mockHttpClient:StandaloneWSClient = stub[StandaloneWSClient]

    //Auth mocks
    val pseudoToken:Token = makeToken(tokenExpiration)
    val mockAuthRequest:CustomRequest = stub[CustomRequest]
    val mockAuthResponse:mockAuthRequest.Response = stub[mockAuthRequest.Response]
    (mockHttpClient.url _).when(authUrlAddress).returns(mockAuthRequest)
    (mockAuthRequest.withRequestTimeout _).when(*).returns(mockAuthRequest)
    (mockAuthRequest.post(_:Map[String,Seq[String]])(_:BodyWritable[Map[String,Seq[String]]]))
      .when(*,*)
      .onCall(_ => {
        tokenFetchCounter.incrementAndGet()
        if(tokenFetchSucceeds)
          Future.successful(mockAuthResponse)
        else
          Future.failed(new CustomTestException())
      })
    (() => mockAuthResponse.status).when().returns(200)
    //point of this test: token should only be requested once
    (mockAuthResponse.body[JsValue](_:BodyReadable[JsValue]))
      .when(*).returns(Json.toJson(pseudoToken))

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
  }

  ////////////////////////////////////////////////// Testcases

  it must "only fetch one token to make multiple uploads in short time" in {
    val fixture = new TestFixture(LongTokenLifetime,true)

    assert(fixture.tokenFetchCounter.get() == 0)
    val submissionReports = List.fill(nUploads)(makeFakeReport)

    for {
      _ <- Future.traverse(submissionReports)(fixture.toTest.upload)
    } yield assert(fixture.tokenFetchCounter.get() == 1)
  }


  it must "fetch a new token for subsequent uploads if it expires during a series of uploads" in {
    val fixture = new TestFixture(ZeroTokenLifetime,true)

    for{
      _ <- (assertResult(0, "Tokenfetchcounter should be initialized to zero before test")
              (fixture.tokenFetchCounter.get()))
      _ <- fixture.toTest.upload(makeFakeReport)
      _ <- assertResult(1, "Tokenfetch appearantly did not tirgger")(fixture.tokenFetchCounter.get())
      _ <- fixture.toTest.upload(makeFakeReport)
      _ <- assertResult(2)(fixture.tokenFetchCounter.get()) //no clue why it failed
      _ <- fixture.toTest.upload(makeFakeReport)
      _ <- assertResult(3)(fixture.tokenFetchCounter.get())
    } yield succeed
  }


  it must "fetch a new token for subsequent uploads if the first fetch failed" in {
    val fixture = new TestFixture(LongTokenLifetime,false)

    //TODO fails right now, probably should, but not sure

    for{
      _                   <- (assertResult(0, "Tokenfetchcounter should be initialized to zero before test")
                                          (fixture.tokenFetchCounter.get()))
      firstUploadAttempt  <- fixture.toTest.upload(makeFakeReport).recover[Either[String,Unit]](
                                {case _:CustomTestException => Left("testFehler1")})
      _                   <- assertResult(1, "Tokenfetch appearantly did not trigger")(fixture.tokenFetchCounter.get())
      secondUploadAttempt <- fixture.toTest.upload(makeFakeReport).recover[Either[String,Unit]](
                                {case _:CustomTestException => Left("testFehler2")})
      _                   <- assertResult(2)(fixture.tokenFetchCounter.get())
    } yield {
      assert(firstUploadAttempt.isLeft)
      assert(secondUploadAttempt.isLeft)
    }

  }


  it must "fail the upload when the fetch of the token fails" in {
    val fixture = new TestFixture(LongTokenLifetime,false)
    recoverToSucceededIf[CustomTestException] {
      fixture.toTest.upload(makeFakeReport)
    }
  }


  "Token" must "be deserializable from the JSON fields that sent from BfArM" in {
    //member names coincide with json fields and the object is built from them.
    // So we should ensure nobody changes field names
    import BfArMConnectorImpl.Token
    val testToken:Token = Token(
      "erztfuj",
      123465,
      9876,
      "khzhftgre",
      "bcvgfhjuz")
    val serializedToken = Json.toJson[Token](testToken)
    val deserializedToken = Json.fromJson[Token](serializedToken)

    assert(deserializedToken.isSuccess,"Serialization (or deserialization) failed")
    assertResult(testToken.access_token)(deserializedToken.get.access_token)
    assertResult(testToken.token_type)(deserializedToken.get.token_type)
    assertResult(testToken.scope)(deserializedToken.get.scope)
    assertResult(testToken.expires_in)(deserializedToken.get.expires_in)
    assertResult(testToken.refresh_expires_in)(deserializedToken.get.refresh_expires_in)
  }
}
