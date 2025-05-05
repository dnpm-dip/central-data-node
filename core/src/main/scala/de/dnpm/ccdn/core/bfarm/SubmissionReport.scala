package de.dnpm.ccdn.core.bfarm


import java.time.LocalDate
import de.dnpm.dip.model.{
  HealthInsurance,
  Id,
  Site
}
import de.dnpm.dip.service.mvh.Submission.Type
import de.dnpm.dip.service.mvh.TransferTAN
import play.api.libs.json.{
  Json,
  Format,
  Writes
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


final case class SubmissionReport
(
  SubmittedCase: SubmissionReport.Case  // Not my idea to name the field CapitalizedCase "SubmittedCase" (see disclaimer above)
)


object SubmissionReport
{

  object DiseaseType extends Enumeration
  { 
    val Oncological = Value("oncological")
    val Rare        = Value("rare")

    implicit val format: Format[Value] = 
      Json.formatEnum(this)
  }


  object DataCategory extends Enumeration
  { 
    val Clinical = Value("clinical")

    implicit val format: Format[Value] = 
      Json.formatEnum(this)
  }


  final case class Case
  (
    submissionDate: LocalDate,
    submissionType: Type.Value,
    tan: Id[TransferTAN],
    submitterId: Id[Site],
    dataNodeId: Id[CDN],
    diseaseType: DiseaseType.Value,
    coverageType: HealthInsurance.Type.Value,
    dataQualityCheckPassed: Boolean
  )

  implicit val formatHealthInsuranceType: Format[HealthInsurance.Type.Value] =
    Json.formatEnum(HealthInsurance.Type)

  implicit val formatCase: Format[Case] =
    Json.format[Case]

  implicit val format: Format[SubmissionReport] =
    Json.format[SubmissionReport]

}




final case class Submission[Case,MolSeq,Plan,FU]
(
  metadata: Metadata,
  `case`: Case,
  molecular: Option[MolSeq],
  plan: Option[Plan],
  followUp: Option[FU]
)


object Submission
{

  implicit def writes[
    Case: Writes,
    MolSeq: Writes,
    Plan: Writes,
    FU: Writes
  ]: Writes[Submission[Case,MolSeq,Plan,FU]] =
    Json.writes[Submission[Case,MolSeq,Plan,FU]]

}
