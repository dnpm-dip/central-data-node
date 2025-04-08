package de.dnpm.ccdn.core.bfarm.onco


import java.time.LocalDate
import de.dnpm.dip.coding.{
  Code,
  Coding,
}
import de.dnpm.dip.coding.ops.OPS
import de.dnpm.dip.model.{
  Id,
  Medications,
  Recommendation,
  Study
}
import de.dnpm.dip.mtb.model.{
  LevelOfEvidence,
  MTBMedicationRecommendation,
  MTBProcedureRecommendation,
  Variant
}
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



final case class OncologyPlan
(
  carePlanOd: Option[OncologyPlan.CarePlan],
  recommendedSystemicTherapies: Option[List[OncologyPlan.SystemicTherapyRecommendation]],
  recommendedStudies: Option[List[OncologyPlan.StudyRecommendation]]
)

object OncologyPlan
{

  final case class InterventionRecommendation
  (
    interventionsIsRiskReducing: Boolean,
    interventionIsTherapeutic: Boolean,
    `type`: Coding[OPS],
  )

  object InterventionRecommendation
  {
    implicit val format: OFormat[InterventionRecommendation] =
      Json.format[InterventionRecommendation]
  }

  final case class CarePlan
  (
    molecularBoardDecisionDate: LocalDate,
    studyRecommended: Boolean,
    counsellingRecommended: Boolean,
    reEvaluationRecommended: Boolean,
    interventionRecommended: Boolean,
    suitableInterventions: Option[List[InterventionRecommendation]],
    otherRecommendations: Option[List[Code[MTBProcedureRecommendation.Category.Value]]]
  )

  object CarePlan
  {
    implicit val format: OFormat[CarePlan] =
      Json.format[CarePlan]
  }


  final case class StudyRecommendation
  (
    identifier: Id[StudyRecommendation],
    register: String,
    name: String,
    id: Id[Study],
    substances: Option[Set[Coding[Medications]]],
    evidenceLevel: Code[LevelOfEvidence.Grading.Value],
    evidenceLevelDetails: Set[Code[LevelOfEvidence.Addendum.Value]],
    priority: Code[Recommendation.Priority.Value],
    variants: Option[List[Id[Variant]]]
  )

  object StudyRecommendation
  { 
    implicit val format: OFormat[StudyRecommendation] =
      Json.format[StudyRecommendation]
  }


  final case class SystemicTherapyRecommendation
  (
    identifier: Id[SystemicTherapyRecommendation],
    `type`: SystemicTherapyRecommendation.Type.Value,
    substances: Set[Coding[Medications]],
    evidenceLevel: Code[LevelOfEvidence.Grading.Value],
    evidenceLevelDetails: Set[Code[LevelOfEvidence.Addendum.Value]],
    priority: Code[Recommendation.Priority.Value],
    variants: Option[List[Id[Variant]]],
    therapeuticStrategy: Code[MTBMedicationRecommendation.Category.Value]
  )

  object SystemicTherapyRecommendation
  { 

    object Type extends Enumeration
    {
      val InLabel       = Value("inLabel")
      val OffLabel      = Value("offLabel")
      val Compassionate = Value("compassionateUse")
      val SecPreventive = Value("secPreventive")
      val Unknown       = Value("unknown")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[SystemicTherapyRecommendation] =
      Json.format[SystemicTherapyRecommendation]
  }


  implicit val format: OFormat[OncologyPlan] =
    Json.format[OncologyPlan]
}
