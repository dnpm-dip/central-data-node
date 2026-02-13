package de.dnpm.ccdn.connector


import scala.concurrent.{
  Future, Promise,
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

import java.io.IOException



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

  /**
   * Is None, when expired or unused, becomes an empty promise while it is
   * being fetched, so that simultaneous request will wait for the same token.
   */
  @volatile private var tokenPromise:Option[Promise[Token]] = None
  /**
   * A lock object that wraps all read or write access to tokenPromise
   */
  private val tokenPromiseLock = new Object
  /**
   * Scheduled during token fetch to clear an expired token
   */
  private val expire: Runnable = tokenPromiseLock.synchronized {
    () => tokenPromise = None }


  private def getToken: Future[Token] = {

    import scala.concurrent.ExecutionContext.Implicits.global


    tokenPromiseLock.synchronized {
      if (tokenPromise.isDefined) {
        return tokenPromise.get.future
      } else {
        tokenPromise = Some(Promise[Token]())
      }
    }
    val tokenToFetch = tokenPromise.get
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
      .map(
        resp => resp.status match {
          case 200 => resp.body[JsValue].as[Token].asRight
          case _   => resp.body[JsValue].as[Error].asLeft
        }
      )
      .andThen {
        case Success(Right(tkn)) =>
          log.info(s"Updating API token reference and scheduling expiration in ${tkn.expires_in} s")
          tokenPromiseLock.synchronized {
            tokenToFetch.success(tkn)
            executor.schedule(expire, tkn.expires_in - 5, SECONDS)
            Future.successful(tokenPromise)
          }

        case Success(Left(err)) =>
          val errMsg: String = s"Failed to get BfArM API token: ${err.statusCode} ${err.error}: ${err.message}"
          tokenPromiseLock.synchronized {
            tokenToFetch.failure(new IOException(errMsg))
          }
          log.error(errMsg)

        case Failure(t) =>
          tokenPromiseLock.synchronized {
            tokenToFetch.failure(t)
          }
          log.error("Failed to get BfArM API token", t)
      }
      .collect {
        case Right(tkn) => tkn
      }

  }


  private def request(
    url: String
  )(
    implicit ec: ExecutionContext
  ): Future[WSRequest] =
    getToken
      .map(tkn =>
        wsclient.url(url)
          .withHttpHeaders("Authorization" -> s"${tkn.token_type} ${tkn.access_token}")
          .withRequestTimeout(timeout)
      )


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
        case 401 => upload(report)
        case _   =>
          val err = resp.body[JsValue].as[Error]
          Future.successful(s"${err.statusCode} ${err.error}: ${err.message}".asLeft)
      }

    } yield result

}
