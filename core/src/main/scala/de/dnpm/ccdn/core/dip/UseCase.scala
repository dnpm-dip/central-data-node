package de.dnpm.ccdn.core.dip


import play.api.libs.json.{
  Json,
  Format
}


object UseCase extends Enumeration
{
  val MTB = Value("MTB")
  val RD  = Value("RD")

  implicit val format: Format[Value] =
    Json.formatEnum(this)
}

