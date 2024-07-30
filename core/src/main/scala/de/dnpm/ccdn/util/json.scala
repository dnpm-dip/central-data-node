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


  extension[T <: Enum](m: Map[T,String])
    def invert: Map[String,T] =
      m.map { (k,v) => (v,k) }



  // Based on example from: https://alexn.org/blog/2023/05/25/scala-enums/
  private inline def allInstances[ET <: Tuple, T]: List[T] =
    inline erasedValue[ET] match
      case EmptyTuple => Nil
      case _: (t *: ts)  =>
        summonInline[ValueOf[t]].value.asInstanceOf[T] :: allInstances[ts, T]


  private inline def defaultNames[T <: Enum](using m: Mirror.SumOf[T]): Map[T,String] =
    allInstances[m.MirroredElemTypes, m.MirroredType]
      .map(t => t -> t.toString)
      .toMap



  inline def enumReads[T <: Enum: Mirror.SumOf]: Reads[T] =
    val names = defaultNames[T]
    Reads.of[String]
      .collect(
        JsonValidationError(s"Invalid enum value, expected one of {${names.values.mkString(",")}}")
      )(
        names.invert
      )

  def enumReads[T <: Enum](names: Map[String,T]): Reads[T] =
    Reads.of[String]
      .collect(
        JsonValidationError(s"Invalid enum value, expected one of {${names.values.mkString(",")}}")
      )(
        names
      )


  inline def enumWrites[T <: Enum: Mirror.SumOf]: Writes[T] =
    Writes.of[String].contramap(defaultNames[T])

  def enumWrites[T <: Enum](names: T => String): Writes[T] =
    Writes.of[String].contramap(names)


  inline def enumFormat[T <: Enum: Mirror.SumOf]: Format[T] =
    Format(
      enumReads[T],
      enumWrites[T]
    )

  def enumFormat[T <: Enum](names: Map[T,String]): Format[T] =
    Format(
      enumReads[T](names.invert),
      enumWrites[T](names)
    )

