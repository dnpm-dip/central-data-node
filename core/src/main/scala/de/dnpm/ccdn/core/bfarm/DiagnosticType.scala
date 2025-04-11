package de.dnpm.ccdn.core.bfarm


import play.api.libs.json.{
  Json,
  Format
}


object DiagnosticType extends Enumeration
{
  val Array           = Value("array")
  val Single          = Value("single")
  val Karyotyping     = Value("karyotyping")
  val Panel           = Value("panel")
  val Exome           = Value("exome")
  val GenomeShortRead = Value("genomeShortRead")
  val GenomeLongRead  = Value("genomeLongRead")
  val Other           = Value("other")
  val NonePerformed   = Value("none")

  implicit val format: Format[Value] =
    Json.formatEnum(this)
}
