package de.dnpm.ccdn.core


import java.net.URI
import play.api.libs.json.{
  Json,
  JsPath,
  Format,
  OFormat,
  OWrites,
  Writes,
  Reads
}


opaque type Code[+T] = String

object Code:

  def apply[T](code: String): Code[T] = code

  given [T](using Reads[String]): Reads[Code[T]] =
    Reads.of[String]//.map(Code[T](_))

  given [T](using Writes[String]): Writes[Code[T]] =
    Writes.of[String]//.contramap[Code[T]](_.value)


/*
final case class Code[+T](value: String) extends AnyVal:
  override def toString = value

object Code:

  given [T]: Reads[Code[T]] =
    Reads.of[String].map(Code[T](_))

  given [T]: Writes[Code[T]] =
    Writes.of[String].contramap[Code[T]](_.value)
*/


/*
opaque type System[T] = URI

object System:

  def apply[T](uri: URI): System[T] = uri

  def apply[T](using sys: System[T]): System[T] = sys

  extension[T](sys: System[T])
    def uri: URI = sys
*/



final case class Coding[+T]
(
  code: Code[T],
  display: Option[String],
  system: URI,
  version: Option[String]
)

object Coding:

  final class System[T] private (val uri: URI)

  object System:

    def apply[T](uri: String): System[T] =
      new System[T](URI.create(uri))
    
    def apply[T](using sys: System[T]) = sys
  end System
    
    
  def apply[T: System](
    code: String,
    display: Option[String] = None
  ): Coding[T] =
    Coding[T](
      Code[T](code),
      display,
      System[T].uri,
      None
    )


  import play.api.libs.functional.syntax._

  given [T: System]: Reads[Coding[T]] =
    (
      (JsPath \ "code").read[Code[T]] and
      (JsPath \ "display").readNullable[String] and
      (JsPath \ "version").readNullable[String]
    )(
      (code,display,version) =>
        Coding[T](
          code,
          display,
          System[T].uri,
          version
        )
    )


  given [T]: OWrites[Coding[T]] =
    Json.writes[Coding[T]]

  given readsAnyCoding: Reads[Coding[Any]] =
    Json.reads[Coding[Any]]
