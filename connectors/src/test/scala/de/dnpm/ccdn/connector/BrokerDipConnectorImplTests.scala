package de.dnpm.ccdn.connector

import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import scala.concurrent.Future

class BrokerDipConnectorImplTests extends AsyncFlatSpec
  with AsyncMockFactory
{

  behavior of "BrokerDipConnectorImpl"

  private val baseUrl = "https://uktest.de"
  private val testSite = Code[Site]("UKT")
  private val expectedFullApiVersionRequestUrl = "https://uktest.de/api/peer2peer/meta-info"

  trait CustomRequest extends StandaloneWSRequest with DefaultBodyWritables {
    type Self = CustomRequest
    type Response = CustomResponse
  }
  trait CustomResponse extends StandaloneWSResponse

  /**
   * @param metaInfoResponse the Future returned by get() on the meta-info mock request
   * @param metaInfoUrl the exact URL the mock expects; use a literal string in tests that verify URL correctness
   */
  private class TestFixture(
    metaInfoResponse: Future[_ <: CustomResponse],
    metaInfoUrl: String = s"$baseUrl/api/peer2peer/meta-info"
  ) {
    val mockHttpClient: StandaloneWSClient = stub[StandaloneWSClient]

    // Stub /sites so that the connector constructor does not fail during initialisation
    private val mockSitesRequest: CustomRequest = stub[CustomRequest]
    (mockHttpClient.url _).when(s"$baseUrl/sites").returns(mockSitesRequest)
    (mockSitesRequest.withRequestTimeout _).when(*).returns(mockSitesRequest)
    (() => mockSitesRequest.get()).when().returns(
      Future.failed(new Exception("sites config not relevant in this test"))
    )

    val connector = new BrokerDipConnectorImpl(
      mockHttpClient,
      BrokerDipConnectorImpl.Config(baseUrl, Some(10), None)
    )

    // The sites request fails above, so inject the test site directly.
    connector.sitesConfig.set(Map(testSite -> "uktest.de"))

    // Stub the meta-info endpoint
    val mockMetaRequest: CustomRequest = stub[CustomRequest]
    (mockHttpClient.url _).when(metaInfoUrl).returns(mockMetaRequest)
    (mockMetaRequest.withRequestTimeout _).when(*).returns(mockMetaRequest)
    (mockMetaRequest.withVirtualHost _).when(*).returns(mockMetaRequest)
    (() => mockMetaRequest.get()).when().returns(metaInfoResponse)
  }


  it must "return the version string when the meta-info endpoint returns a JSON body with a 'version' field" in {
    val mockResponse: CustomResponse = stub[CustomResponse]
    (() => mockResponse.status).when().returns(200)
    (mockResponse.body[JsValue](_: BodyReadable[JsValue])).when(*).returns(
      Json.obj("version" -> "2.5.1", "unrelated" -> "field")
    )

    // Literal URL verifies that getApiVersion targets exactly this endpoint
    val fixture = new TestFixture(
      Future.successful(mockResponse),
      expectedFullApiVersionRequestUrl
    )
    fixture.connector.getApiVersion(testSite).map { result =>
      assertResult(Right("2.5.1"))(result)
    }
  }

  it must "return Left when the meta-info endpoint returns a JSON body without a 'version' field" in {
    val responseBodyJson = Json.obj("name" -> "dip-node", "status" -> "running")
    val mockResponse: CustomResponse = stub[CustomResponse]
    (() => mockResponse.status).when().returns(200)
    (mockResponse.body[JsValue](_: BodyReadable[JsValue])).when(*).returns(responseBodyJson)
    (() => mockResponse.body).when().returns(responseBodyJson.toString)

    val fixture = new TestFixture(Future.successful(mockResponse))
    fixture.connector.getApiVersion(testSite).map { result =>
      assert(result.isLeft, s"Expected Left for response without 'version' field, got: $result")
    }
  }

  it must "return Left containing the exception message when the connection to the site fails" in {
    val errorMsg = "Connection refused: uktest.de/443"

    val fixture = new TestFixture(Future.failed(new Exception(errorMsg)))
    fixture.connector.getApiVersion(testSite).map { result =>
      assertResult(Left(errorMsg))(result)
    }
  }
}