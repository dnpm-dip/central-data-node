package de.dnpm.ccdn.util


import play.api.libs.json.{
  Json,
  Reads,
  Writes,
  Format
}


/*
final case class Id[+T](value: String) extends AnyVal

object Id:

  given [T]: Reads[Id[T]] = 
    Reads.of[String].map(Id[T](_))

  given [T]: Writes[Id[T]] = 
    Writes.of[String].contramap[Id[T]](_.value)
*/


opaque type Id[T] = String

object Id:

  def apply[T](id: String): Id[T] = id

  given [T](using Reads[String]): Reads[Id[T]] = 
    Reads.of[String]

  given [T](using Writes[String]): Writes[Id[T]] = 
    Writes.of[String]
