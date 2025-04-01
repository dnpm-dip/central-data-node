package de.dnpm.ccdn.core.bfarm.onco


import de.dnpm.dip.coding.{
  Code,
  Coding,
  SequenceOntology
}
import de.dnpm.dip.coding.hgnc.HGNC
import de.dnpm.dip.coding.hgvs.HGVS
import de.dnpm.dip.mtb.model.{
  Chromosome,
  Transcript
}
import de.dnpm.dip.model.{
  Id
}
import play.api.libs.json.{
  Json,
  Format,
  OFormat
}


/*
 * DISCLAIMER:
 *
 * These DTOs are implemented to match the JSON specifications for the MVGenomSeq Submission API.
 *
 * This author hereby wishes to make it clear that many of the anti-patterns
 * and design flaws noticeable in the DTO structure are NOT of his design,
 * but originate from the specification these DTO must conform to.
 *
 */

import OncologyMolecular._


final case class OncologyMolecular
(
  smallVariants: Option[List[SmallVariant]],
  copyNumberVariants: Option[List[CopyNumberVariant]],
  complexBiomarkers: Option[List[ComplexBiomarker]]
)

object OncologyMolecular
{

  object GenomicSource extends Enumeration
  { 
    val Somatic  = Value("somatic")
    val Germline = Value("germline")

    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }

  object Localization extends Enumeration
  {
    val Coding     = Value("coding")
    val Regulatory = Value("inRegulatoryElements")
    val Neither    = Value("notInCodingAndNotInRegulatoryElements")

    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }

  sealed trait Variant
  {
    val identifier: Id[Variant]
    val genomicSource: GenomicSource.Value
//    val gene: Coding[HGNC]
    val localization: Localization.Value
  }


  final case class SmallVariant
  (
    identifier: Id[SmallVariant],
    genomicSource: GenomicSource.Value,
    gene: Coding[HGNC],
    localization: Localization.Value,
    transcriptId: Id[Transcript],
    dnaChange: Coding[HGVS],
    proteinChange: Option[Coding[HGVS]],
    variantTypes: Option[List[Coding[SequenceOntology]]],
    loh: Option[Boolean]
  )
  extends Variant

  object SmallVariant
  { 
    implicit val format: OFormat[SmallVariant] =
      Json.format[SmallVariant]
  }


  final case class CopyNumberVariant
  (
    identifier: Id[SmallVariant],
    genomicSource: GenomicSource.Value,
    gene: Option[Coding[HGNC]],
    localization: Localization.Value,
    cnvType: CopyNumberVariant.Type.Value,
    chromosome: Option[Code[Chromosome.Value]],
    startPosition: Option[Int],
    endPosition: Option[Int],
  )
  extends Variant

  object CopyNumberVariant
  { 

    object Type extends Enumeration
    { 
      val CompleteLoss     = Value("completeLoss")
      val HeterozygousLoss = Value("heterozygousLoss")
      val Loss             = Value("loss")
      val LowLevelGain     = Value("lowLevelGain")
      val HighLevelGain    = Value("highLevelGain")
      val Gain             = Value("gain")
    
      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[CopyNumberVariant] =
      Json.format[CopyNumberVariant]
  }


  /*
   * NOTE:
   * StructuralVariant, ExpressionVariant, SbsSignature not represented/relevant in MTB Core Data Set
   *
   */


  final case class ComplexBiomarker
  (
    identifier: Id[ComplexBiomarker],
    ploidy: Option[Int],
    tmb: Option[Double],
    hrdHigh: Option[Boolean],
    lstHigh: Option[Boolean],
    taiHigh: Option[Boolean]
  )

  object ComplexBiomarker
  { 
    implicit val format: OFormat[ComplexBiomarker] =
      Json.format[ComplexBiomarker]
  }



  implicit val format: OFormat[OncologyMolecular] =
    Json.format[OncologyMolecular]
}
