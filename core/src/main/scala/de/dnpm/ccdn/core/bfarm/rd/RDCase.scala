package de.dnpm.ccdn.core.bfarm.rd


import java.time.{ 
  LocalDate,
  YearMonth
}
import cats.data.NonEmptyList
import de.dnpm.dip.util.json.{
  readsNel,
  writesNel,
  readsYearMonth,
  writesYearMonth
}
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.rd.model.{
  GMFCS,
  HPO,
  RDDiagnosis
}
import de.dnpm.ccdn.core.bfarm.DiagnosticType
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


final case class RDCase
(
  diagnosisRd: Option[RDCase.Diagnosis],
  priorRd: Option[RDCase.PriorRD]
)


object RDCase
{

  final case class Diagnosis
  (
    phenotypes: NonEmptyList[Coding[HPO]],
    hpoVersion: String,
    symptomOnsetDate: YearMonth,
    molecularBoardDecisionDate: LocalDate,
    diagnosticExtent: Diagnosis.Extent.Value,
    diagnosticAssessment: Option[Diagnosis.Status.Value],
    diagnoses: NonEmptyList[Coding[RDDiagnosis.Systems]],
    noMatchingCodeExists: Option[Boolean],
    diagnosisGmfcs: Option[Code[GMFCS.Value]]
  )


  object Diagnosis
  {

    object Extent extends Enumeration
    {
      val SingleGenome = Value("singleGenome")
      val DuoGenome    = Value("duoGenome")
      val TrioGenome   = Value("trioGenome")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    object Status extends Enumeration
    {
      val NoGeneticDiagnosis                 = Value("noGeneticDiagnosis")
      val SuspectedGeneticDiagnosis          = Value("suspectedGeneticDiagnosis")
      val FurtherGeneticDiagnosisRecommended = Value("furtherGeneticDiagnosisRecommended")
      val ConfirmedGeneticDiagnosis          = Value("confirmedGeneticDiagnosis")
      val PartialGeneticDiagnosis            = Value("partialGeneticDiagnosis")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[Diagnosis] =
      Json.format[Diagnosis]
  }


  final case class PriorRD
  (
    genomicTestType: DiagnosticType.Value,
    diagnosticDate: Option[LocalDate],
    diagnosticResult: PriorRD.DiagnosticAssessment.Value,
    hospitalizationPeriods: PriorRD.Hospitalizations.Value,
    hospitalizationDuration: PriorRD.HospitalizationDays.Value,
    zseContactDate: YearMonth
  )

  object PriorRD
  {

    object DiagnosticAssessment extends Enumeration
    {

      val PhenotypeNotCompletelyClarified        = Value("phenotypeNotCompletelyClarified")
      val StructuralVariantWithUnclearBreakpoint = Value("structuralVariantWithUnclearBreakpoint")
      val UnclearVariantInGeneticDisease         = Value("unclearVariantInGeneticDisease")
      val NoPathogenicVariantDetected            = Value("noPathogenicVariantDetected")
      val Other                                  = Value("other")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }


    object Hospitalizations  extends Enumeration
    {
      val Zero            = Value("none")
      val UpToFive        = Value("upToFive")
      val UpToTen         = Value("upToTen")
      val UpToFifteen     = Value("upToFifteen")
      val MoreThanFifteen = Value("moreThanFifteen")
      val Unknown         = Value("unknown")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }


    object HospitalizationDays  extends Enumeration
    {
      val Zero          = Value("none")
      val UpToFive      = Value("upToFive")
      val UpToFifteen   = Value("upToFifteen")
      val UpToFifty     = Value("upToFifty")
      val MoreThanFifty = Value("moreThanFifty")
      val Unknown       = Value("unknown")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[PriorRD] =
      Json.format[PriorRD]
  }

}
