/*
package de.dnpm.ccdn.core


import play.api.libs.json.{
  Json,
  JsPath,
  OWrites,
  Reads
}


final case class Coding[+T]
(
  code: String,
  display: Option[String],
  system: String,
  version: Option[String]
)

object Coding
{

  final class System[T] private (val uri: String)

  object System
  {
    def apply[T](uri: String): System[T] =
      new System[T](uri)
    
    def apply[T](implicit sys: System[T]) = sys
  }
    
  
  def apply[T: System](
    code: String,
    display: Option[String] = None
  ): Coding[T] =
    Coding[T](
      code,
      display,
      System[T].uri,
      None
    )


  import play.api.libs.functional.syntax._

  implicit def reads[T: System]: Reads[Coding[T]] =
    (
      (JsPath \ "code").read[String] and
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

  implicit val readsAnyCoding: Reads[Coding[Any]] =
    Json.reads[Coding[Any]]

  implicit def writes[T]: OWrites[Coding[T]] =
    Json.writes[Coding[T]]

}
*/
