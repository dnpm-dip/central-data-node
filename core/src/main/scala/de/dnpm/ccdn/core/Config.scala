package de.dnpm.ccdn.core


import java.io.FileInputStream
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
import de.dnpm.ccdn.core.dip.UseCase
import de.dnpm.ccdn.core.bfarm.{
  CDN,
  GDC
}


final case class Config
(
  polling: Config.Polling,
  dataNodeIds: Map[UseCase.Value,Id[CDN]],
  genomicDataCenterIds: Map[Code[Site],Id[GDC]],
  submitterIds: Map[Code[Site],Id[Site]]
)


object Config extends Logging
{

  final case class Polling
  (
    period: Int,
    timeUnit: TimeUnit
  )

  implicit val readsTimeUnit: Reads[TimeUnit] =
    Reads.of[String].map(t => TimeUnit.valueOf(t.toUpperCase))

  implicit val readsPolling: Reads[Polling] =
    Json.reads[Polling]

  implicit val reads: Reads[Config] =
    Json.reads[Config]


  private lazy val prop =
    "dnpm.ccdn.config.file"

  lazy val instance: Config =
    Option( 
      getClass.getClassLoader.getResourceAsStream("config.json")
    )
    .orElse {
      log.warn("Couldn't load config file from class-path, attempting to load it from configured file path")
      Option(System.getProperty(prop))
        .map(new FileInputStream(_))
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
