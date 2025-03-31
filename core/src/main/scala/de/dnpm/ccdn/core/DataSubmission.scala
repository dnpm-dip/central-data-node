package de.dnpm.ccdn.core


import play.api.libs.json.{
  Json,
  Format
}


sealed trait TTAN

sealed trait DataNode


object SubmissionType extends Enumeration
{
  
  val Initial    = Value("initial")
  val Addition   = Value("addition")
  val Correction = Value("correction")
  val FollowUp   = Value("followup")

  implicit val format: Format[Value] =
    Json.formatEnum(this)
}
