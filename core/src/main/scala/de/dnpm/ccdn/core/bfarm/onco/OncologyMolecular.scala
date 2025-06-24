package de.dnpm.ccdn.core.bfarm.onco


import de.dnpm.dip.coding.{
  Code,
  Coding,
  SequenceOntology
}
import de.dnpm.dip.coding.hgnc.HGNC
import de.dnpm.dip.coding.hgvs.HGVS
import de.dnpm.dip.mtb.model.{
  Transcript
}
import de.dnpm.dip.model.{
  Id
}
import de.dnpm.ccdn.core.bfarm.Chromosome
import play.api.libs.json.{
  Json,
  Format,
  OFormat
}
import OncologyMolecular._



/*
 * NOTE:
 * StructuralVariant, ExpressionVariant, SbsSignature not represented/relevant in MTB Core Data Set
 */

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
    val localization: Localization.Value
  }


  final case class SmallVariant
  (
    identifier: Id[SmallVariant],
    genomicSource: GenomicSource.Value,
    gene: Coding[HGNC],
    localization: Localization.Value,
    startPosition: Long,
    endPosition: Long,
    ref: String,
    alt: String,
    dnaChange: Code[HGVS],
    proteinChange: Option[Code[HGVS]],
    transcriptId: Coding[Transcript.Systems],
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
    chromosome: Option[Chromosome.Value],
    startPosition: Option[Long],
    endPosition: Option[Long],
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
