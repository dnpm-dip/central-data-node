package de.dnpm.ccdn.core.bfarm


import java.time.LocalDate
import play.api.libs.json.{
  Json,
  Format,
  OWrites
}
import de.dnpm.dip.model.{
  Id,
  Site
}
import de.dnpm.ccdn.core.{
  DataNode,
  SubmissionType,
  TTAN
}


final case class SubmissionReport
(
  submissionDate: LocalDate,
  submissionType: SubmissionType.Value,
  localCaseId: Id[TTAN],
  submitterId: Id[Site],
  dataNodeId: Id[DataNode],
  dataCategory: SubmissionReport.DataCategory.Value,
  diseaseType: SubmissionReport.DiseaseType.Value,
  libraryType: SequencingType.Value,
  dataQualityCheckedPassed: Boolean
)

object SubmissionReport
{

  object DataCategory extends Enumeration
  {
    val Clinical = Value("clinical")

    implicit val format: Format[DataCategory.Value] =
      Json.formatEnum(this)
  }


  object DiseaseType extends Enumeration
  {
    val Oncological = Value("oncological")
    val Rare        = Value("rare")

    implicit val format: Format[DiseaseType.Value] =
      Json.formatEnum(this)
  }


  implicit val writes: OWrites[SubmissionReport] =
    Json.writes[SubmissionReport]
//      .transform(js => Json.obj("SubmittedCase" -> js))

}

