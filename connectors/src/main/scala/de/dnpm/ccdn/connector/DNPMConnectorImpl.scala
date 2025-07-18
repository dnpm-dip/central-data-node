package de.dnpm.ccdn.connector


import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.concurrent.duration._
import scala.util.{
  Either,
  Success,
  Failure
}
import scala.util.chaining._
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
import play.api.libs.ws.JsonBodyReadables._
import de.dnpm.dip.util.{
  Logging,
  Retry
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{
  Submission,
  UseCase
}
import de.dnpm.ccdn.core.dip.{
  Connector,
  ConnectorProvider
}


final case class Collection[T](entries: List[T])

object Collection
{
  implicit def reads [T: Reads]: Reads[Collection[T]] =
    Json.reads[Collection[T]]
}


final class DIPConnectorProviderImpl extends ConnectorProvider
{
  override def getInstance: Connector =
    BrokerConnector.instance
}


object BrokerConnector
{

  final case class Config
  (
    baseURL: String,
    timeout: Option[Int],
    updatePeriod: Option[Long]
  )

  object Config
  {
    lazy val instance =
      Config(
        Option(System.getenv("CCDN_BROKER_BASEURL")).orElse(Option(System.getProperty("ccdn.dnpm.broker.baseurl"))).get,
        Option(System.getenv("CCDN_BROKER_CONNECTOR_TIMEOUT")).map(_.toInt),
        Option(System.getenv("CCDN_BROKER_CONNECTOR_UPDATE_PERIOD")).map(_.toLong)
      )
  }

  final case class SiteEntry
  (
    id: String,
    name: String,
    virtualhost: String
  )

  final case class SiteConfig
  (
    sites: Set[SiteEntry]
  )

  object SiteConfig
  {

    implicit val readsSiteEntry: Reads[SiteEntry] =
      Json.reads[SiteEntry]

    implicit val reads: Reads[SiteConfig] =
      Json.reads[SiteConfig]
  }

  lazy val instance =
    new BrokerConnector(
      HttpClient.instance,
      Config.instance
    )

}


import BrokerConnector._

final class BrokerConnector
(
  private val wsclient: WSClient,
  private val config: Config
)
extends Connector
with Logging
{

  private val timeout =
    config.timeout.getOrElse(10) seconds


  // Set-up for periodic auto-update of config

  import java.util.concurrent.{Executors,ScheduledExecutorService}
  import java.util.concurrent.TimeUnit.MINUTES
  import java.util.concurrent.atomic.AtomicReference


  private val sitesConfig: AtomicReference[Map[Code[Site],String]] =
    new AtomicReference(Map.empty)

  private implicit lazy val executor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor

  private val getSiteConfigTask =
    Retry(
      () => {

        import ExecutionContext.Implicits.global
    
        log.debug(s"Requesting peer connectivity config")
    
        request("/sites")
          .get()
          .map(_.body[JsValue].as[SiteConfig])
          .onComplete {
            case Success(SiteConfig(sites)) =>
              sitesConfig.set(
                sites.map {
                  case SiteEntry(id,_,vhost) => Code[Site](id) -> vhost
                }
                .toMap
              )
    
            case Failure(t) =>
              log.error(s"Broker connection error: ${t.getMessage}")
          }
      },
      name = "Get site config",
      maxTries = 5,
      period = 10
    )


  config.updatePeriod match {
    case Some(period) =>
      executor.scheduleAtFixedRate(
        getSiteConfigTask,
        0,
        period,
        MINUTES
      )

    case None =>
      getSiteConfigTask.run
  }


  private def request(
    rawUri: String
  ): WSRequest = {
    val uri =
      if (rawUri.startsWith("/")) rawUri.substring(1)
      else rawUri

    wsclient.url(s"${config.baseURL}/$uri")
      .withRequestTimeout(timeout)
  }


  private def request(
    site: Code[Site],
    rawUri: String
  ): WSRequest =
    request(rawUri)
      .withVirtualHost(sitesConfig.get()(site))


  override def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Submission.Report.Filter
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Seq[Submission.Report]]] =
    request(
      site, s"/api/${useCase.toString.toLowerCase}/peer2peer/mvh/submission-reports"
    )
    .pipe(
      req => filter.status match {
        case Some(set) => req.addQueryStringParameters("status" -> set.mkString(","))
        case None      => req
      }
    )
    .pipe {
      req =>
        filter.period match {
          case Some(period) =>
            req.addQueryStringParameters("created-after" -> period.start.format(ISO_LOCAL_DATE_TIME))
              .pipe(
                r => period.endOption match {
                  case Some(end) => r.addQueryStringParameters("created-before" -> end.format(ISO_LOCAL_DATE_TIME))
                  case None      => r
                }
              )
          case None => req
        }
    }
    .get()
    .map(
      _.body[JsValue]
       .as[Collection[Submission.Report]]
       .entries
       .asRight
    )
    .recover {
      case t => t.getMessage.asLeft
    }


  override def confirmSubmitted(
    report: Submission.Report
  )(
    implicit env: ExecutionContext
  ): Future[Either[String,Unit]] =
    request(
      report.site.code,
      s"/api/${report.useCase.toString.toLowerCase}/peer2peer/mvh/submission-reports/${report.id.value}:submitted"
    )
    .execute("POST")
    .map(
      r => r.status match {
        case 200 => ().asRight
        case _   => s"Error confirming submission of report ${report.id.value}: Status ${r.status}".asLeft
      } 
    )

}
