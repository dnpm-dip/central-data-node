package de.dnpm.ccdn.core.bfarm.rd


import java.time.LocalDate
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Id,
  Study
}
import de.dnpm.dip.rd.model.{
  RDTherapy,
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


final case class RDPlan
(
  carePlanOd: Option[RDPlan.CarePlan],
  recommendedStudies: Option[List[RDPlan.StudyRecommendation]],
  recommendedTherapies: Option[List[RDPlan.TherapyRecommendation]]
)

object RDPlan
{

  final case class CarePlan
  (
    molecularBoardDecisionDate: LocalDate,
    studyRecommended: Boolean,
    counsellingRecommended: Boolean,
    reEvaluationRecommended: Boolean,
    therapyRecommended: Boolean,
    clinicalManagementRecommended: Boolean,
    clinicalManagementDescriptions: Option[Set[CarePlan.ClinicalManagementDescription.Value]]
  )

  object CarePlan
  {

    object ClinicalManagementDescription extends Enumeration
    {
      val DiseaseSpecificAmbulatoryCare = Value("diseaseSpecificAmbulatoryCare")
      val UniversityAmbulatoryCare      = Value("universityAmbulatoryCare")
      val LocalCrd                      = Value("localCrd")
      val OtherCrd                      = Value("otherCrd")
      val OtherAmbulatoryCare           = Value("otherAmbulatoryCare")
      val GP                            = Value("gp")
      val Specialist                    = Value("specialist")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[CarePlan] =
      Json.format[CarePlan]
  }


  final case class StudyRecommendation
  (
    identifier: Id[StudyRecommendation],
    register: String,
    name: String,
    id: Id[Study],
    variants: Option[List[Id[Variant]]]
  )

  object StudyRecommendation
  { 
    implicit val format: OFormat[StudyRecommendation] =
      Json.format[StudyRecommendation]
  }


  final case class TherapyRecommendation
  (
    identifier: Id[TherapyRecommendation],
    `type`: Code[RDTherapy.Type.Value],
    strategy: TherapyRecommendation.Strategy.Value,
    strategyOther: Option[String],
    variantReferences: Option[List[Id[Variant]]]
  )

  object TherapyRecommendation
  { 

    object Strategy extends Enumeration
    {
      val SystemicMedication   = Value("systemicMedication")
      val TargetedMedication   = Value("targetedMedication")
      val PreventionMedication = Value("preventionMedication")
      val Genetic              = Value("genetic")
      val Prophylactic         = Value("prophylactic")
      val EarlyDetection       = Value("earlyDetection")
      val Combination          = Value("combination")
      val Nutrition            = Value("nutrition")
      val Other                = Value("other")

      implicit val format: Format[Value] =
        Json.formatEnum(this)
    }

    implicit val format: OFormat[TherapyRecommendation] =
      Json.format[TherapyRecommendation]
  }


  implicit val format: OFormat[RDPlan] =
    Json.format[RDPlan]
}
