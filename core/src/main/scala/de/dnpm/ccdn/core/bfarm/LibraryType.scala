package de.dnpm.ccdn.core.bfarm


import play.api.libs.json.{
  Json,
  Format
}

object LibraryType extends Enumeration
{
  val Panel = Value("panel")
  val WES   = Value("wes")
  val WGS   = Value("wgs")
  val WGSLr = Value("wgs_lr")
  val None  = Value("none")
  
  implicit val format: Format[LibraryType.Value] =
    Json.formatEnum(this)
}

