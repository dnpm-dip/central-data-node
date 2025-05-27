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
  StandaloneWSRequest => WSRequest,
}
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.bfarm
import bfarm.SubmissionReport




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
    code: Int,
    message: String
  )

  implicit val reads: Reads[Error] =
    Json.reads[Error]


  final case class Config
  (
    apiBaseURL: String,
    authURL: String,
    clientId: String,
    clientSecret: String,
    timeout: Int
  )

  object Config
  { 

    lazy val instance = {

      val baseURL = System.getenv("CCDN_BFARM_API_BASEURL")

      Config(
        baseURL,
        Option(System.getenv("CCDN_BFARM_AUTH_URL"))
          .getOrElse(s"$baseURL/realms/mvgenomseq/protocol/openid-connect/token"),
        System.getenv("CCDN_BFARM_AUTH_CLIENT_ID"),
        System.getenv("CCDN_BFARM_AUTH_CLIENT_SECRET"),
        Option(System.getenv("CCDN_BFARM_API_TIMEOUT")).map(_.toInt).getOrElse(10)
      )
    }
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
  private val wsclient: WSClient,
)
extends bfarm.Connector
with Logging
{

  import java.util.concurrent.Executors
  import java.util.concurrent.TimeUnit.SECONDS
  import java.util.concurrent.atomic.AtomicReference

  private val executor =
    Executors.newSingleThreadScheduledExecutor


  private def getToken: Future[Token] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    wsclient
      .url(config.authURL)
      .withRequestTimeout(config.timeout seconds)
      .post(
        Map(
          "grant_type"    -> Seq("client_credentials"),
          "client_id"     -> Seq(config.clientId),
          "client_secret" -> Seq(config.clientSecret)
        )
      )
      .map(_.body[JsValue].as[Token])
  }

  private val token: AtomicReference[Future[Token]] =
    new AtomicReference(
      getToken.andThen {
        case Success(tkn) =>
          executor.scheduleWithFixedDelay(
            () => token.set(getToken),
            tkn.expires_in - 5,
            tkn.expires_in - 5,
            SECONDS
          )

        case Failure(t) =>
          log.error("Failed to get BfArM API token",t)
      }(
        scala.concurrent.ExecutionContext.global
      )
    )


  private def request(
    rawUri: String
  )(
    implicit ec: ExecutionContext
  ): Future[WSRequest] = {
    val uri = 
      if (rawUri startsWith "/") rawUri.substring(1)
      else rawUri

    token.get.map(
      tkn =>
        wsclient.url(s"${config.apiBaseURL}/$uri")
          .withHttpHeaders("Authorization" -> s"${tkn.token_type} ${tkn.access_token}")
          .withRequestTimeout(config.timeout seconds)
    )
  }


  override def upload(
    report: SubmissionReport
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Unit]] =
    for {
      req      <- request("api/upload")
      response <- req.post(Json.toJson(report))
    } yield response.status match {
      case 200 => ().asRight
      case _   => response.body[JsValue].as[Error].message.asLeft
    }

}
