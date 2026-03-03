package de.dnpm.ccdn.core.bfarm


import play.api.libs.json.{
  Json,
  Format
}

/**
 * In what way genome is read from DNA and stored
 */
object LibraryType extends Enumeration
{
  /**
   * Only sequence an interesting subset of the genome
   */
  val Panel = Value("panel")
  /**
   * Whole Exome Sequencing
   */
  val WES   = Value("wes")
  /**
   * Whole Genome Sequencing
   */
  val WGS   = Value("wgs")
  /**
   * WGS Long read
   */
  val WGSLr = Value("wgs_lr")
  val None  = Value("none")
  
  implicit val format: Format[LibraryType.Value] =
    Json.formatEnum(this)
}

