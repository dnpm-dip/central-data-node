package de.dnpm.ccdn.core


import java.time.{
  LocalDate,
  LocalDateTime
}
import play.api.libs.json.{
  Format,
  Reads,
  Writes
}
import de.dnpm.ccdn.util.{
  Id,
  json,
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


object SubmissionType:
  given Format[SubmissionType] =
    json.enumFormat(
      Map(
        Initial    -> "initial",
        Addition   -> "addition",  
        Correction -> "correction",
        Other      -> "other"
      )
    )

/*
enum SequencingType:
  case Panel
  case WES
  case WGS
  case WGSLr
  case None

object SequencingType:
  given Format[SequencingType] =
    json.enumFormat[SequencingType](
      Map(
        Panel -> "panel",
        WES   -> "wes",  
        WGS   -> "wgs",
        WGSLr -> "wgs_lr",
        None  -> "none"
      )
  )
*/
