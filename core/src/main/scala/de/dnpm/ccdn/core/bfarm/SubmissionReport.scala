package de.dnpm.ccdn.core.bfarm



import play.api.libs.json.{
  Json,
  Writes
}


final case class SubmissionReport[Case,MolSeq,Plan,FU]
(
  metadata: Metadata,
  `case`: Case,
  molecular: Option[MolSeq],
  plan: Option[Plan],
  followUp: Option[FU]
)


object SubmissionReport
{

  implicit def writes[
    Case: Writes,
    MolSeq: Writes,
    Plan: Writes,
    FU: Writes
  ]: Writes[SubmissionReport[Case,MolSeq,Plan,FU]] =
    Json.writes[SubmissionReport[Case,MolSeq,Plan,FU]]

}
