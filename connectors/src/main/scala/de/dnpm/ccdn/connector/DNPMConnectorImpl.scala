package de.dnpm.ccdn.connector


import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.concurrent.duration._
import scala.util.{
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
  StandaloneWSClient as WSClient,
  StandaloneWSRequest as WSRequest,
  StandaloneWSResponse as WSResponse
}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.DefaultBodyReadables._
import de.dnpm.ccdn.util.Logging
import de.dnpm.ccdn.core.{
  Coding,
  DNPM,
  Period,
  Site
}


final case class Collection[T](entries: List[T])
object Collection:
  given [T: Reads]: Reads[Collection[T]] =
    Json.reads[Collection[T]]



final class DNPMConnectorProviderImpl extends DNPM.ConnectorProvider:
  override def getInstance: DNPM.Connector =
    BrokerConnector.instance



object BrokerConnector:

  final case class Config
  (
    baseURL: String,
    timeout: Option[Int],
    updatePeriod: Option[Long]
  )

  object Config:
    lazy val instance =
      Config(
        System.getProperty("dnpm.ccdn.broker.baseurl"),
        Option(System.getProperty("dnpm.ccdn.connector.timeout")).map(_.toInt),
        Option(System.getProperty("dnpm.ccdn.connector.update.period")).map(_.toLong)
      )


  final case class SiteEntry
  (
    id: String,
    name: String,
    virtualhost: String,
    useCases: Set[DNPM.UseCase]
  )

  final case class SiteConfig
  (
    sites: Set[SiteEntry]
  )

  object SiteConfig:

    given Reads[SiteEntry] =
      Json.reads[SiteEntry]

    given Reads[SiteConfig] =
      Json.reads[SiteConfig]


  lazy val instance =
    new BrokerConnector(
      HttpClient.instance,
      Config.instance
    )



import BrokerConnector._

final class BrokerConnector
(
  private val wsclient: WSClient,
  private val config: Config
)
extends DNPM.Connector
with Logging:

  import DNPM.{
    SubmissionReport,
    UseCase
  }


  private val timeout =
    config.timeout.getOrElse(10) seconds


  // Set-up for periodic auto-update of config

  import java.time.{Duration,LocalTime}
  import java.util.concurrent.Executors
  import java.util.concurrent.TimeUnit.MINUTES
  import java.util.concurrent.atomic.AtomicReference


  private val sitesConfig: AtomicReference[Map[Coding[Site],(String,Set[UseCase])]] =
    new AtomicReference(Map.empty)


  private def getSiteConfig: Unit = {

    import ExecutionContext.Implicits.global

    log.debug(s"Requesting peer connectivity config")

    request("/sites")
      .get()
      .map(_.body[JsValue].as[SiteConfig])
      .onComplete {
        case Success(SiteConfig(sites)) =>
          sitesConfig.set(
            sites.map {
              case SiteEntry(id,name,vhost,useCases) =>
                Coding[Site](id,Some(name)) -> (vhost,useCases)
            }
            .toMap
          )

        case Failure(t) =>
          log.error(s"Broker connection error: ${t.getMessage}")
      }

  }


  private lazy val executor =
    Executors.newSingleThreadScheduledExecutor

  config.updatePeriod match
    case Some(period) =>
      executor.scheduleAtFixedRate(
        () => getSiteConfig,
        0,
        period,
        MINUTES
      )
    case None =>
      getSiteConfig
  


  private def request(
    rawUri: String
  ): WSRequest =
    val uri =
      if (rawUri.startsWith("/")) rawUri.substring(1)
      else rawUri

    wsclient.url(s"${config.baseURL}/$uri")
      .withRequestTimeout(timeout)
  

  private def request(
    site: Coding[Site],
    rawUri: String
  ): WSRequest =
    request(rawUri)
      .withVirtualHost(sitesConfig.get()(site)._1)


  override def sites: ExecutionContext ?=> Future[List[Coding[Site]] | String] =
    Future.successful(
      sitesConfig.get
        .keys
        .toList
    )



  override def dataSubmissionReports(
    site: Coding[Site],
    period: Option[Period[LocalDateTime]] = None
  ): ExecutionContext ?=> Future[Seq[SubmissionReport] | String] =
    val useCases =
      sitesConfig.get()(site)._2

    Future.reduceLeft(
      useCases.map( 
        useCase =>
          request(
            site,
            s"/api/${useCase.toString.toLowerCase}/peer2peer/submission-reports"
          )
          .pipe(
            req =>
             period match
               case Some(Period(start,Some(end))) =>
                 req.withQueryStringParameters(
                   "created-after"  -> start.format(ISO_LOCAL_DATE_TIME),
                   "created-before" -> end.format(ISO_LOCAL_DATE_TIME),
                 )

               case Some(Period(start,None)) =>
                 req.withQueryStringParameters(
                   "created-after"  -> start.format(ISO_LOCAL_DATE_TIME)
                 )

               case None => req
          )
          .get()
          .map(
            _.body[JsValue]
             .as[Collection[SubmissionReport]]
             .entries
          )
          .recover {
            case t =>
              log.error(s"Connection error: ${t.getMessage}")
              Seq.empty[SubmissionReport]
          }
      )
    )(
      _ ++ _
    )
    .recover {
      case t => t.getMessage
    }

