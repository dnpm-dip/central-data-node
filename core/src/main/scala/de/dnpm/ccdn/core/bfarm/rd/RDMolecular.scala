package de.dnpm.ccdn.core.bfarm.rd



import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.coding.hgnc.HGNC
import de.dnpm.dip.coding.hgvs.HGVS
import de.dnpm.dip.model.{
  Id,
  Publication
}
import de.dnpm.dip.rd.model.{
  ACMG,
  CopyNumberVariant => CNV,
  ISCN
}
import de.dnpm.dip.rd.model.Variant.Significance
import de.dnpm.ccdn.core.bfarm.Chromosome
import play.api.libs.json.{
  Json,
  Format,
  OFormat,
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


final case class RDMolecular
(
  smallVariants: Option[List[RDMolecular.SmallVariant]],
  structuralVariants: Option[List[RDMolecular.StructuralVariant]],
  copyNumberVariants: Option[List[RDMolecular.CopyNumberVariant]]
)

object RDMolecular
{

  sealed abstract class Variant
  {
    val id: Id[Variant]
    val genes: Option[Set[Coding[HGNC]]]
    val localization: Option[Variant.Localization.Value]
    val cdnaChange: Option[Code[HGVS.DNA]]
    val gdnaChange: Option[Code[HGVS.DNA]]
    val proteinChange: Option[Code[HGVS.Protein]]
    val acmgClass: ACMG.Class.Value
    val acmgCriteria: Option[Set[ACMGCriterion]]
    val zygosity: Variant.Zygosity.Value
    val segregationAnalysis: Option[Variant.SegregationAnalysis.Value]
    val modeOfInheritance: Option[Variant.ModeOfInheritance.Value]
    val diagnosticSignificance: Option[Significance.Value]
    val externalId: Option[Id[Variant]]
    val publications: Option[Set[Id[Publication]]]
  }


  final case class ACMGCriterion
  (
    value: ACMG.Criterion.Type.Value,
    modifier: Option[ACMG.Criterion.Modifier.Value]
  )

  object ACMGCriterion
  {
    implicit val formatType: Format[ACMG.Criterion.Type.Value] =
      Json.formatEnum(ACMG.Criterion.Type)

    implicit val formatModifier: Format[ACMG.Criterion.Modifier.Value] =
      Json.formatEnum(ACMG.Criterion.Modifier)

    implicit val format: OFormat[ACMGCriterion] =
      Json.format[ACMGCriterion]
  }


  object Variant
  {

    object Localization extends Enumeration
    {
      val CodingRegion       = Value("codingRegion")
      val SplicingRegion     = Value("splicingRegion")
      val RegulatoryRegion   = Value("regulatoryRegion")
      val IntronicIntergenic = Value("intronicIntergenic")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    object Zygosity extends Enumeration
    {
      val Heterozygous  = Value("heterozygous")
      val Homozygous    = Value("homozygous")
      val CompHet       = Value("compHet")
      val Hemi          = Value("hemi")
      val Homoplasmic   = Value("homoplasmic")
      val Heteroplasmic = Value("heteroplasmic")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    object ModeOfInheritance extends Enumeration
    {
      val Dominant      = Value("dominant")
      val Recessive     = Value("recessive")
      val Mitochondrial = Value("mitochondrial")
      val Xlinked       = Value("XLinked")
      val Unclear       = Value("unclear")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    object SegregationAnalysis extends Enumeration
    {
      val NotPerformed = Value("notPerformed")
      val DeNovo       = Value("deNovo")
      val FromFather   = Value("fromFather")
      val FromMother   = Value("fromMother")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

  }


  final case class SmallVariant
  (
    id: Id[SmallVariant],
    genes: Option[Set[Coding[HGNC]]],
    chromosome: Chromosome.Value,
    position: Int,
    ref: String,
    alt: String,
    localization: Option[Variant.Localization.Value],
    cdnaChange: Option[Code[HGVS.DNA]],
    gdnaChange: Option[Code[HGVS.DNA]],
    proteinChange: Option[Code[HGVS.Protein]],
    acmgClass: ACMG.Class.Value,
    acmgCriteria: Option[Set[ACMGCriterion]],
    zygosity: Variant.Zygosity.Value,
    segregationAnalysis: Option[Variant.SegregationAnalysis.Value],
    modeOfInheritance: Option[Variant.ModeOfInheritance.Value],
    diagnosticSignificance: Option[Significance.Value],
    externalId: Option[Id[Variant]],
    publications: Option[Set[Id[Publication]]]
  )
  extends Variant


  final case class StructuralVariant
  (
    id: Id[SmallVariant],
    genes: Option[Set[Coding[HGNC]]],
    localization: Option[Variant.Localization.Value],
    cdnaChange: Option[Code[HGVS.DNA]],
    gdnaChange: Option[Code[HGVS.DNA]],
    proteinChange: Option[Code[HGVS.Protein]],
    description: Option[Code[ISCN]],
    acmgClass: ACMG.Class.Value,
    acmgCriteria: Option[Set[ACMGCriterion]],
    zygosity: Variant.Zygosity.Value,
    segregationAnalysis: Option[Variant.SegregationAnalysis.Value],
    modeOfInheritance: Option[Variant.ModeOfInheritance.Value],
    diagnosticSignificance: Option[Significance.Value],
    externalId: Option[Id[Variant]],
    publications: Option[Set[Id[Publication]]]
  )
  extends Variant


  final case class CopyNumberVariant
  (
    id: Id[SmallVariant],
    genes: Option[Set[Coding[HGNC]]],
    chromosome: Chromosome.Value,
    startPosition: Int,
    endPosition: Int,
    `type`: CNV.Type.Value,
    localization: Option[Variant.Localization.Value],
    cdnaChange: Option[Code[HGVS.DNA]],
    gdnaChange: Option[Code[HGVS.DNA]],
    proteinChange: Option[Code[HGVS.Protein]],
    acmgClass: ACMG.Class.Value,
    acmgCriteria: Option[Set[ACMGCriterion]],
    zygosity: Variant.Zygosity.Value,
    segregationAnalysis: Option[Variant.SegregationAnalysis.Value],
    modeOfInheritance: Option[Variant.ModeOfInheritance.Value],
    diagnosticSignificance: Option[Significance.Value],
    externalId: Option[Id[Variant]],
    publications: Option[Set[Id[Publication]]]
  )
  extends Variant



  implicit val formatACMGClass: Format[ACMG.Class.Value] =
    Json.formatEnum(ACMG.Class)

  implicit val formatSignificance: Format[Significance.Value] =
    Json.formatEnum(Significance)

  implicit val formatCNVType: Format[CNV.Type.Value] =
    Json.formatEnum(CNV.Type)

  implicit val formatSNV: OFormat[SmallVariant] =
    Json.format[SmallVariant]

  implicit val formatCNV: OFormat[CopyNumberVariant] =
    Json.format[CopyNumberVariant]

  implicit val formatSV: OFormat[StructuralVariant] =
    Json.format[StructuralVariant]

  implicit val format: OFormat[RDMolecular] =
    Json.format[RDMolecular]

}
