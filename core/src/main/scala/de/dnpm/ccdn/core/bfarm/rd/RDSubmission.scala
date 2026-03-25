package de.dnpm.ccdn.core.bfarm.rd


import de.dnpm.ccdn.core.bfarm.{
  Metadata,
  AbstractBfarmSubmission
}
import play.api.libs.json.{
  Json,
  OWrites
}

final case class RDSubmission
(
  metadata: Metadata,
  `case`: RDCase,
  molecular: Option[RDMolecular],
  plan: Option[RDPlan],
  followUp: Option[RDFollowUps]
)
extends AbstractBfarmSubmission[
  RDCase,
  RDMolecular,
  RDPlan,
  RDFollowUps
]


object RDSubmission
{
  implicit val format: OWrites[RDSubmission] =
    Json.writes[RDSubmission]
}
