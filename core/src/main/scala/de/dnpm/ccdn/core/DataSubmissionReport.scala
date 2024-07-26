package de.dnpm.ccdn.core


import java.time.{
  LocalDate,
  LocalDateTime
}
import play.api.libs.json.{
  Json,
  Format,
  Reads,
  Writes
}
import de.dnpm.ccdn.util.{
  Id,
  JsonFormatting
}


sealed trait TTAN
sealed trait DataNode
sealed trait Site
object Site:
  given Coding.System[Site] = Coding.System[Site]("dnpm/site")



enum SubmissionType:
  case Initial
  case Addition
  case Correction
  case Other

object SubmissionType extends JsonFormatting[SubmissionType]:
  val names =
    Map(
      Initial    -> "initial",
      Addition   -> "addition",  
      Correction -> "correction",
      Other      -> "other"
    )



enum SequencingType:
  case Panel
  case WES
  case WGS
  case WGSLr
  case None

object SequencingType extends JsonFormatting[SequencingType]:
  val names =
    Map(
      Panel -> "panel",
      WES   -> "wes",  
      WGS   -> "wgs",
      WGSLr -> "wgs_lr",
      None  -> "none"
    )

