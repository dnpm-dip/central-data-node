package de.dnpm.ccdn.core.bfarm.onco


import de.dnpm.ccdn.core.bfarm.{
  Metadata,
  AbstractBfarmSubmission
}
import play.api.libs.json.{
  Json,
  OWrites
}

final case class OncologySubmission
(
  metadata: Metadata,
  `case`: OncologyCase,
  molecular: Option[OncologyMolecular],
  plan: Option[OncologyPlan],
  followUp: Option[OncologyFollowUps]
)
extends AbstractBfarmSubmission[
  OncologyCase,
  OncologyMolecular,
  OncologyPlan,
  OncologyFollowUps
]


object OncologySubmission
{
  implicit val format: OWrites[OncologySubmission] =
    Json.writes[OncologySubmission]
}
