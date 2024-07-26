package de.dnpm.ccdn.core


import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS
import scala.util.chaining._
import play.api.libs.json.{
  Json,
  Reads
}
import de.dnpm.ccdn.util.{
  Id,
  Logging
}



final case class Config
(
  polling: Config.Polling,
  dataNodeId: Id[DataNode],
  submitterIds: Map[Code[Site],Id[Site]]
)


object Config extends Logging:

  final case class Polling
  (
    period: Int,
    timeUnit: TimeUnit
  )

  given Reads[TimeUnit] =
    Reads.of[String].map(t => TimeUnit.valueOf(t.toUpperCase))

  given Reads[Polling] =
    Json.reads[Polling]

  given Reads[Config] =
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


