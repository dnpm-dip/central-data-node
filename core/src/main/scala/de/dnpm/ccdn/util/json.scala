package de.dnpm.ccdn.util


import scala.reflect.Enum
import scala.deriving.Mirror
import scala.compiletime.{
  erasedValue,
  summonInline
}
import play.api.libs.json.{
  Json,
  JsonValidationError,
  Format,
  Reads,
  Writes
}


object json:

  private def invert[T <: Enum](m: Map[T,String]) =
    m.map { (k,v) => (v,k) }


  // Based on examples from:
  // - https://docs.scala-lang.org/scala3/reference/contextual/derivation.html
  // - https://alexn.org/blog/2023/05/25/scala-enums/
  private inline def summonInstances[ET <: Tuple, T]: List[T] =
    inline erasedValue[ET] match
      case EmptyTuple   => Nil
      case _: (t *: ts) => summonInline[ValueOf[t]].value.asInstanceOf[T] :: summonInstances[ts, T]

  private inline def defaultNames[T <: Enum](using m: Mirror.SumOf[T]): Map[T,String] =
    summonInstances[m.MirroredElemTypes, m.MirroredType]
      .map(t => t -> t.toString)
      .toMap


  def enumReads[T <: Enum](names: Map[String,T]): Reads[T] =
    Reads.of[String]
      .collect(
        JsonValidationError(s"Invalid enum value, expected one of {${names.values.mkString(",")}}")
      )(
        names
      )

  inline def enumReads[T <: Enum: Mirror.SumOf]: Reads[T] =
    enumReads[T](invert(defaultNames[T]))


  def enumWrites[T <: Enum](names: T => String): Writes[T] =
    Writes.of[String].contramap(names)

  inline def enumWrites[T <: Enum: Mirror.SumOf]: Writes[T] =
    enumWrites[T](defaultNames[T])


  def enumFormat[T <: Enum](names: Map[T,String]): Format[T] =
    Format(
      enumReads[T](invert(names)),
      enumWrites[T](names)
    )

  inline def enumFormat[T <: Enum: Mirror.SumOf]: Format[T] =
    enumFormat[T](defaultNames[T])

end json



/*
trait OpaqueTypeFormat[Pub,Op]:

  given(
    using r: Reads[Op]
  ): Reads[Pub] =
    r.asInstanceOf[Reads[Pub]]

  given(
    using w: Writes[Op]
  ): Writes[Pub] =
    w.asInstanceOf[Writes[Pub]]

trait OpaqueTypeFormatF[Pub[_],Op]:

  given[T](
    using r: Reads[Op]
  ): Reads[Pub[T]] =
    r.asInstanceOf[Reads[Pub[T]]]

  given[T](
    using w: Writes[Op]
  ): Writes[Pub[T]] =
    w.asInstanceOf[Writes[Pub[T]]]
*/


