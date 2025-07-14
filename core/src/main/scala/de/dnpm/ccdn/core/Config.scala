package de.dnpm.ccdn.core


import java.io.FileInputStream
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import scala.util.chaining._
import play.api.libs.json.{
  Json,
  Reads
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Id,
  Site
}
import de.dnpm.dip.util.Logging
import de.dnpm.dip.service.mvh.UseCase
import de.dnpm.ccdn.core.bfarm.{
  CDN,
  GDC
}


final case class Config
(
  polling: Config.Polling,
  dataNodeIds: Map[UseCase.Value,Id[CDN]],
  sites: Map[Code[Site],Config.SiteInfo]
){

  def activeUseCases =
    dataNodeIds.keySet

  def submitterId(site: Code[Site]): Id[Site] =
    sites(site).submitterId

  def gdcId(site: Code[Site]): Id[GDC] =
    sites(site).gdcId
}


object Config extends Logging
{

  final case class SiteInfo
  (
    submitterId: Id[Site],
    gdcId: Id[GDC],
    useCases: Set[UseCase.Value]
  )


  final case class Polling
  (
    period: Long,
    timeUnit: TimeUnit,
    startTime: Option[LocalTime]
  )

  implicit val readsTimeUnit: Reads[TimeUnit] =
    Reads.of[String].map(t => TimeUnit.valueOf(t.toUpperCase))

  implicit val readsSiteInfo: Reads[SiteInfo] =
    Json.reads[SiteInfo]

  implicit val readsPolling: Reads[Polling] =
    Json.reads[Polling]

  implicit val reads: Reads[Config] =
    Json.reads[Config]


  private lazy val ENV  = "CCDN_CONFIG_FILE"
  private lazy val PROP = "dnpm.ccdn.config.file"

  lazy val instance: Config =
    Option( 
      getClass.getClassLoader.getResourceAsStream("config.json")
    )
    .orElse {
      log.debug(s"Loading Config from file configured via ENV variable $ENV")
      Option(System.getenv(ENV)).map(new FileInputStream(_))
    }
    .orElse {
      log.warn(s"Couldn't load config file, attempting to load it from file configured via system property $PROP")
      Option(System.getProperty(PROP)).map(new FileInputStream(_))
    }
    .map(
      Json.parse(_) pipe (
        Json.fromJson[Config](_)
          .fold(
            errs => {
              log.error(errs.toString)
              throw new Exception(errs.toString)
            },
            identity
          )
      )
    )
    .get

}
