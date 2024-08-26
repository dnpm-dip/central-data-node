package de.dnpm.ccdn.util


import play.api.libs.json.{
  Json,
  Reads,
  Writes
}


opaque type Id[T] = String

object Id:

  def apply[T](id: String): Id[T] = id

  given [T](using r: Reads[String]): Reads[Id[T]] = r

  given [T](using w: Writes[String]): Writes[Id[T]] = w

