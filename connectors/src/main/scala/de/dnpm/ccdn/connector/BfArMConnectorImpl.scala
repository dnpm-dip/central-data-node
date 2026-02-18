package de.dnpm.ccdn.connector


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.concurrent.duration._
import scala.util.{
  Failure,
  Success
}
import cats.syntax.either._
import play.api.libs.json.{
  Json,
  JsValue,
  Reads
}
import play.api.libs.ws.{
  StandaloneWSClient => WSClient,
  StandaloneWSRequest => WSRequest
}
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.bfarm
import bfarm.SubmissionReport

import java.util.concurrent.atomic.AtomicReference



final class BfArMConnectorProviderImpl extends bfarm.ConnectorProvider
{
  override def getInstance: bfarm.Connector =
    BfArMConnectorImpl.instance
}

object BfArMConnectorImpl
{

  final case class Token
  (
    access_token: String,
    expires_in: Long,
    refresh_expires_in: Int,
    scope: String,
    token_type: String
  )

  object Token
  { 
    implicit val format: Reads[Token] =
      Json.reads[Token]
  }


  final case class Error
  (
    statusCode: Int,
    error: String,
    code: String,
    message: String
  )

  implicit val reads: Reads[Error] =
    Json.reads[Error]


  final case class Config
  (
    apiURL: String,
    authURL: String,
    clientId: String,
    clientSecret: String,
    timeout: Option[Int]
  )

  object Config
  { 
    lazy val instance =
      Config(
        Option(System.getenv("CCDN_BFARM_API_URL")).getOrElse(System.getProperty("ccdn.bfarm.api.url")),
        Option(System.getenv("CCDN_BFARM_AUTH_URL")).getOrElse(System.getProperty("ccdn.bfarm.auth.url")),
        Option(System.getenv("CCDN_BFARM_AUTH_CLIENT_ID")).getOrElse(System.getProperty("ccdn.bfarm.api.client.id")),
        Option(System.getenv("CCDN_BFARM_AUTH_CLIENT_SECRET")).getOrElse(System.getProperty("ccdn.bfarm.api.client.secret")),
        Option(System.getenv("CCDN_BFARM_API_TIMEOUT")).map(_.toInt)
      )
  }


  lazy val instance =
    new BfArMConnectorImpl(
      Config.instance,
      HttpClient.instance
    )

}


import BfArMConnectorImpl._    


final class BfArMConnectorImpl
(
  private val config: Config,
  private val wsclient: WSClient
)
extends bfarm.Connector
with Logging
{

  import java.util.concurrent.Executors
  import java.util.concurrent.TimeUnit.SECONDS

  private val timeout =
    config.timeout.getOrElse(10) seconds

  private val executor =
    Executors.newSingleThreadScheduledExecutor

  private val tokenCache:AtomicReference[Option[Future[Token]]] = new AtomicReference(None)

  /**
   * Scheduled during token fetch to clear an expired token
   */
  private val clearToken: Runnable = () => {
    log.debug("Clearing token")
    tokenCache.set(None)
  }


  private def fetchToken(implicit ec:ExecutionContext): Future[Token] = {

    log.info("Getting fresh API token")

    wsclient
      .url(config.authURL)
      .withRequestTimeout(timeout)
      .post(
        Map(
          "grant_type"    -> Seq("client_credentials"),
          "client_id"     -> Seq(config.clientId),
          "client_secret" -> Seq(config.clientSecret)
        )
      )
      .map(_.body[JsValue].as[Token])
      .andThen {
        case Success(tkn) =>
          // Schedule token removal after its expiration
          executor.schedule(clearToken, tkn.expires_in - 5, SECONDS)
        case Failure(t) =>
          log.error("Failed to get BfArM API token",t)
      }
  }

  private def request(
    url: String
  )(
    implicit ec: ExecutionContext
  ): Future[WSRequest] = {
    tokenCache.updateAndGet(_.orElse (Some(fetchToken)))
      .get
      .map(tkn =>
        wsclient.url(url)
          .withHttpHeaders("Authorization" -> s"${tkn.token_type} ${tkn.access_token}")
          .withRequestTimeout(timeout)
      )
  }


  override def upload(
    report: SubmissionReport
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Unit]] =
    for {
      req <- request(config.apiURL)

      _ = log.debug(s"Uploading SubmissionReport ${report.SubmittedCase.tan}")

      resp <- req.post(Json.toJson(report))

      result <- resp.status match {
        case 200 => Future.successful(().asRight)
        case _   =>
          val err = resp.body[JsValue].as[Error]
          clearToken.run()
          Future.successful(s"${err.statusCode} ${err.error}: ${err.message}".asLeft)
      }

    } yield result

}
