package de.dnpm.ccdn.connector


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.concurrent.duration._
import scala.util.Either
import cats.syntax.either._
import play.api.libs.json.{
  Json,
  JsValue,
  Reads
}
import play.api.libs.ws.{
  StandaloneWSClient => WSClient,
}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.BfArM
import BfArM.SubmissionReport



final class BfArMConnectorProviderImpl extends BfArM.ConnectorProvider
{
  override def getInstance: BfArM.Connector =
    BfArMConnectorImpl.instance
}

object BfArMConnectorImpl
{

  final case class Error(
    code: Int,
    message: String
  )

  implicit val reads: Reads[Error] =
    Json.reads[Error]

  lazy val instance =
    new BfArMConnectorImpl(
      HttpClient.instance,
      System.getProperty("dnpm.ccdn.bfarm.baseurl"),
      Option(System.getProperty("dnpm.ccdn.connector.timeout")).map(_.toInt)
    )
}


import BfArMConnectorImpl._    


final class BfArMConnectorImpl
(
  private val wsclient: WSClient,
  private val baseURL: String,
  private val timeout: Option[Int]
)
extends BfArM.Connector
with Logging
{

  def upload(
    report: SubmissionReport
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,SubmissionReport]] =
    wsclient
      .url(s"$baseURL/api/upload")
      .withRequestTimeout(timeout.getOrElse(10) seconds)
      .post(Json.toJson(report))
      .map(
        response => response.status match {
          case 200 => report.asRight
          case _   => response.body[JsValue].as[Error].message.asLeft
        }
      )
}
