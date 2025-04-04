package de.dnpm.ccdn.core.bfarm.onco


import java.time.LocalDate
import de.dnpm.dip.coding.Coding
//import de.dnpm.dip.coding.atc.ATC
import play.api.libs.json.{
  Json,
  Format,
//  OFormat,
  OWrites,
  Reads
}


final case class CodingWithDate[T]
(
  coding: Coding[T],
  date: LocalDate
)

object CodingWithDate
{

  implicit def reads[T: Coding.System]: Reads[CodingWithDate[T]] =
    Reads {
      js =>
        for {
          coding <- js.validate[Coding[T]]
          date <- (js \ "date").validate[LocalDate]
        } yield CodingWithDate(
          coding,
          date
        )
    }

  implicit def writes[T]: OWrites[CodingWithDate[T]] =
    OWrites {
      cwd => Json.toJsObject(cwd.coding) + ("date" -> Json.toJson(cwd.date))
    }
}


object TerminationReason extends Enumeration
{
  val E, R, W, A, P, S, V, T, U = Value

  implicit val format: Format[Value] =
    Json.formatEnum(this)
}


/*
sealed trait Substance

final case class CodedSubstance(code: Coding[ATC]) extends Substance

final case class NamedSubstance(name: String) extends Substance

object Substance
{

  implicit val formatCodedSubstance: OFormat[CodedSubstance] =
    Json.format[CodedSubstance]

  implicit val formatNamedSubstance: OFormat[NamedSubstance] =
    Json.format[NamedSubstance]

  implicit def reads: Reads[Substance] =
    Reads(
      js =>
        js.validate[CodedSubstance]
          .orElse(js.validate[NamedSubstance])
    )

  implicit def writes: OWrites[Substance] =
    OWrites { 
      case sub: CodedSubstance => Json.toJsObject(sub)
      case sub: NamedSubstance => Json.toJsObject(sub)
    }
}
*/
