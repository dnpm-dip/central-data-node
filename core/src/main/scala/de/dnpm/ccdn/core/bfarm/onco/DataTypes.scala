package de.dnpm.ccdn.core.bfarm.onco


import java.net.URI
import java.time.LocalDate
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.model.Medications
import play.api.libs.json.{
  Json,
  Format,
  OFormat,
  Writes,
  OWrites,
  Reads,
  JsPath
}
import play.api.libs.functional.syntax._


/*
 * Disclaimer: Not my idea, this object semantically identical to a normal Coding
 * but with field "key" instead" of "code". Hence the custom DTO to conform to the upstream API specs
 */
final case class KeyCoding[T]
(
  key: Code[T],
  system: URI
)

object KeyCoding
{
  implicit def writes[S]: OWrites[KeyCoding[S]] =
    Json.writes[KeyCoding[S]]

  implicit def reads[S]: Reads[KeyCoding[S]] =
    Json.reads[KeyCoding[S]]
}

/*
 * Disclaimer: Not my idea to have this JSON representation of a Codings extended with another field, e.g. "date",
 * {
 *   "code": "...",
 *   "display": "...",
 *   ...
 *   "date": "..."
 * }
 * which violates the "composition over inheritance" principle,
 * but hence the custom DTOs with Reads/Writes
 */
sealed trait CodingWithExtension[T,V]
{
  val coding: Coding[T]
  val extension: V
}

sealed trait CodingWithOptExtension[T,V]
{
  val coding: Coding[T]
  val extension: Option[V]
}

object CodingWithExtension
{

  type ReadsCoding[T] = ({ type R[x] = Reads[Coding[x]]})#R[T]


  def reads[T: ReadsCoding, V: Reads, C <: CodingWithExtension[T,V]](
    fieldName: String,
    f: (Coding[T],V) => C
  ): Reads[C] =
    (
      JsPath.read[Coding[T]] and
      (JsPath \ fieldName).read[V]
    )(
      f
    )

  def writes[T, V: Writes, C <: CodingWithExtension[T,V]](
    fieldName: String,
    f: C => (Coding[T],V)
  ): OWrites[C] =
    (
      JsPath.write[Coding[T]] and
      (JsPath \ fieldName).write[V]
    )(
      f
    )


  def readsOpt[T: ReadsCoding, V: Reads, C <: CodingWithOptExtension[T,V]](
    fieldName: String,
    f: (Coding[T],Option[V]) => C
  ): Reads[C] =
    (
      JsPath.read[Coding[T]] and
      (JsPath \ fieldName).readNullable[V]
    )(
      f
    )

  def writesOpt[T, V: Writes, C <: CodingWithOptExtension[T,V]](
    fieldName: String,
    f: C => (Coding[T],Option[V])
  ): OWrites[C] =
    (
      JsPath.write[Coding[T]] and
      (JsPath \ fieldName).writeNullable[V]
    )(
      f
    )

}


final case class CodingWithDate[T]
(
  coding: Coding[T],
  extension: LocalDate
)
extends CodingWithExtension[T,LocalDate]

object CodingWithDate
{

  implicit def reads[T: CodingWithExtension.ReadsCoding]: Reads[CodingWithDate[T]] =
    CodingWithExtension.reads("date",CodingWithDate[T](_,_))

  implicit def writes[T]: OWrites[CodingWithDate[T]] =
    CodingWithExtension.writes("date",unlift(CodingWithDate.unapply[T](_)))

}

final case class CodingWithText[T]
(
  coding: Coding[T],
  extension: Option[String]
)
extends CodingWithOptExtension[T,String]

object CodingWithText
{

  implicit def reads[T: CodingWithExtension.ReadsCoding]: Reads[CodingWithText[T]] =
    CodingWithExtension.readsOpt("text",CodingWithText[T](_,_))

  implicit def writes[T]: OWrites[CodingWithText[T]] =
    CodingWithExtension.writesOpt("text",unlift(CodingWithText.unapply[T](_)))

}



object TerminationReason extends Enumeration
{
  val E, R, W, A, P, S, V, T, U = Value

  implicit val format: Format[Value] =
    Json.formatEnum(this)
}



/*
 * Disclaimer: Not my idea to have this weird distinction of coded vs. named (i.e. non-coded) substances,
 * instead of using Codings consistently. Hence the custom DTO to conform to the upstream API specs
 */
sealed trait Substance

final case class CodedSubstance(code: Coding[Medications]) extends Substance

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
