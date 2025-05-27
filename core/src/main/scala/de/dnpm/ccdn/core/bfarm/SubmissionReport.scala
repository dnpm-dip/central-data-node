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
  SubmittedCase: SubmissionReport.Case  // Not my idea to name the field CapitalizedCase as "SubmittedCase" (see disclaimer above)
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


  def apply(
    submissionDate: LocalDate,
    submissionType: Type.Value,
    tan: Id[TransferTAN],
    submitterId: Id[Site],
    dataNodeId: Id[CDN],
    diseaseType: DiseaseType.Value,
    coverageType: HealthInsurance.Type.Value,
    dataQualityCheckPassed: Boolean
  ): SubmissionReport =
    SubmissionReport(
      Case(
        submissionDate,
        submissionType,
        tan,
        submitterId,
        dataNodeId,
        diseaseType,
        coverageType,
        dataQualityCheckPassed
      )
    )

  implicit val formatHealthInsuranceType: Format[HealthInsurance.Type.Value] =
    Json.formatEnum(HealthInsurance.Type)

  implicit val formatCase: Format[Case] =
    Json.format[Case]

  implicit val format: Format[SubmissionReport] =
    Json.format[SubmissionReport]

}



trait Submission[Case,MolSeq,Plan,FU]
{
  val metadata: Metadata
  val `case`: Case
  val molecular: Option[MolSeq]
  val plan: Option[Plan]
  val followUp: Option[FU]
}

