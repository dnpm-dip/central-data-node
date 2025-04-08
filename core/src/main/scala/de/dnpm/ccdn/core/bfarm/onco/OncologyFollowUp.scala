package de.dnpm.ccdn.core.bfarm.onco


import java.time.LocalDate
import cats.data.NonEmptyList
import de.dnpm.dip.coding.{
  Code,
  Coding,
}
import de.dnpm.dip.coding.icd.ICD10GM
import de.dnpm.dip.util.json.{
  readsNel, 
  writesNel
}
import de.dnpm.dip.model.{
  Id,
  Medications,
  VitalStatus
}
import de.dnpm.dip.mtb.model.{
  ECOG,
  RECIST
}
import play.api.libs.json.{
  Json,
  OFormat,
}


final case class FollowUp
(
  followUpDate: LocalDate,
  metachroneDiagnoses: Boolean,
  additionalDiagnoses: Option[NonEmptyList[CodingWithDate[ICD10GM]]],
  ecogPerformanceStatusScore: Code[ECOG.Value],
  vitalStatus: Code[VitalStatus.Value],
  lastContactDate: Option[LocalDate],
  deathDate: Option[LocalDate],
  therapies: Option[List[FollowUp.Therapy]]
  // preventiveMeasures not relevant for MTB
)

object FollowUp
{

  final case class Therapy
  (
    identifier: Id[Therapy],
    therapyStartDate: LocalDate,
    therapyEndDate: Option[LocalDate],
    terminationReasonOBDS: Option[TerminationReason.Value],
    substances: Option[Set[Coding[Medications]]],
    therapyResponseDate: Option[LocalDate],
    therapyResponse: Option[Code[RECIST.Value]]
  )

  object Therapy
  { 
    implicit val format: OFormat[Therapy] =
      Json.format[Therapy]
  }

  // PreventiveMeasure not relevant for MTB


  implicit val format: OFormat[FollowUp] =
    Json.format[FollowUp]

}


final case class OncologyFollowUps
(
  followUpOds: NonEmptyList[FollowUp]
)

object OncologyFollowUps
{
  implicit val format: OFormat[OncologyFollowUps] =
    Json.format[OncologyFollowUps]
}
