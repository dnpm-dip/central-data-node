package de.dnpm.ccdn.core.bfarm


import java.time.{
  LocalDate,
  YearMonth
}
import play.api.libs.json.{
  Json,
  JsObject,
  Format,
  OFormat
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Gender,
  HealthInsurance,
  Id,
  Patient,
  Site
}
import de.dnpm.dip.service.mvh
import de.dnpm.dip.service.mvh.TransferTAN



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


sealed trait GDC  // Genomic Data Center
sealed trait CDN  // Clinical Data Node


final case class Metadata
(
  submission: Metadata.Submission,
  coverageType: Code[HealthInsurance.Type.Value],
  mvConsent: Metadata.MVConsent,
  researchConsents: Option[List[Metadata.ResearchConsent]],
  tanC: Id[TransferTAN],
  localCaseId: Id[Patient],
  gender: Gender.Value,
  birthDate: YearMonth,
  addressAGS: String,
  decisionToInclude: Boolean,
  inclusionBoardDecisionDate: LocalDate,
  rejectionJustification: Option[Metadata.RejectionJustification.Value]
)

object Metadata
{

  object DiseaseType extends Enumeration
  {
    val Oncological = Value("oncological")
    val Rare        = Value("rare")
 
    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }


  object RejectionJustification extends Enumeration
  {
    val TargetDiagnosisRecommended   = Value("targetDiagnosisRecommended")
    val ProbablyPsychosomaticIllness = Value("probablyPsychosomaticIllness")
    val ProbablyCommonDisease        = Value("probablyCommonDisease")
    val ProbablyNotGeneticCause      = Value("probablyNotGeneticCause")
    val OtherReason                  = Value("otherReason")
    
    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }


  final case class Submission
  (
    date: LocalDate,
    `type`: mvh.Submission.Type.Value,
    submitterId: Id[Site],
    clinicalDataNodeId: Id[CDN],
    genomicDataCenterId: Option[Id[GDC]],
    diseaseType: DiseaseType.Value, 
  )

  object Submission
  {
    implicit val format: OFormat[Submission] =
      Json.format[Submission]
  }

  final case class MVConsent
  (
    presentationDate: Option[LocalDate],
    version: String,
    scope: List[MVConsent.Scope]
  )

  object MVConsent
  {

    final case class Scope
    (
      date: LocalDate,
      domain: Scope.Domain.Value,
      `type`: Scope.Type.Value
    )

    object Scope
    { 
      object Type extends Enumeration 
      {
        val Permit = Value("permit")
        val Deny   = Value("deny")

        implicit val format: Format[Value] =
          Json.formatEnum(this)
      }

      object Domain extends Enumeration 
      {
        val MvSequencing       = Value("mvSequencing")
        val ReIdentification   = Value("reIdentification")
        val CaseIdentification = Value("caseIdentification")

        implicit val format: Format[Value] =
          Json.formatEnum(this)
      }

      implicit val format: OFormat[Scope] =
        Json.format[Scope]
    }

    implicit val format: OFormat[MVConsent] =
      Json.format[MVConsent]
  }

  final case class ResearchConsent
  (
    schemaVersion: String,
    presentationDate: Option[LocalDate],
    scope: JsObject
  )

  object ResearchConsent
  {
    implicit val format: OFormat[ResearchConsent] =
      Json.format[ResearchConsent]
  }


  import de.dnpm.dip.util.json.{
    readsYearMonth,
    writesYearMonth
  }


  implicit val formatGender: Format[Gender.Value] =
    Json.formatEnum(Gender)

  implicit val formatHealthInsurance: Format[HealthInsurance.Type.Value] =
    Json.formatEnum(HealthInsurance.Type)

  implicit val format: OFormat[Metadata] =
    Json.format[Metadata]

}
