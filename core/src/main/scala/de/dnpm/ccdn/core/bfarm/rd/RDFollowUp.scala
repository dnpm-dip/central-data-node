package de.dnpm.ccdn.core.bfarm.rd


import java.time.LocalDate
import cats.data.NonEmptyList
import de.dnpm.dip.util.json.{
  readsNel,
  writesNel
}
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.ccdn.core.bfarm.VitalStatus
import de.dnpm.dip.rd.model.{
  GMFCS,
  HPO
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


final case class RDFollowUp
(
  followUpDate: LocalDate,
  phenotypes: Option[List[RDFollowUp.PhenotypeChange]],
  gmfcs: Option[Code[GMFCS.Value]],
  diagnosisEstablished: Boolean,
  diseaseProgression: Option[String],
  vitalStatus: VitalStatus.Value,
  deathDate: Option[LocalDate]
)

object RDFollowUp
{

  final case class PhenotypeChange
  (
    code: Coding[HPO],
    change: PhenotypeChange.Value
  )

  object PhenotypeChange extends Enumeration
  {

    val NewlyAdded       = Value("newlyAdded")
    val Improved         = Value("improved")
    val Degraded         = Value("degraded")
    val NoLongerObserved = Value("noLongerObserved")
    val Unchanged        = Value("unchanged")

    implicit val formatValue: Format[Value] =
      Json.formatEnum(this)

    implicit val format: OFormat[PhenotypeChange] =
      Json.format[PhenotypeChange]
  }

  implicit val format: OFormat[RDFollowUp] =
    Json.format[RDFollowUp]
}


final case class RDFollowUps
(
  followUpOds: NonEmptyList[RDFollowUp]
)

object RDFollowUps
{
  implicit val format: OFormat[RDFollowUps] =
    Json.format[RDFollowUps]
}
