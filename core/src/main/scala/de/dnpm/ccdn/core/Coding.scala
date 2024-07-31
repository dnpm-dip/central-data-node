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

  given [T](using r: Reads[String]): Reads[Code[T]] = r

  given [T](using w: Writes[String]): Writes[Code[T]] = w



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
