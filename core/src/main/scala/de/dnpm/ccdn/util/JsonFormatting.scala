package de.dnpm.ccdn.util


import scala.reflect.Enum
import play.api.libs.json.{
  Json,
  Format,
  Reads,
  Writes
}


trait JsonFormatting[T <: Enum]:

  val names: Map[T,String]

  given Reads[T] =
    Reads.of[String].map(names.invert)

  given Writes[T] =
    Writes.of[String].contramap(names)


