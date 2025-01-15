package de.dnpm.ccdn.core


import java.time.LocalDate
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.model.{
  Id,
  Site
}
import play.api.libs.json.{
  Json,
  Format,
  OWrites
}


object BfArM
{

  object SequencingType extends Enumeration
  {
    val Panel = Value("panel")
    val WES   = Value("wes")
    val WGS   = Value("wgs")
    val WGSLr = Value("wgs_lr")
    val None  = Value("none")

    implicit val format: Format[SequencingType.Value] =
      Json.formatEnum(this)
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

    implicit val warites: OWrites[SubmissionReport] =
      Json.writes[SubmissionReport]
        .transform(js => Json.obj("SubmittedCase" -> js))

  }


  trait ConnectorOps[F[_],Env,Err]
  {
    def upload(
      report: SubmissionReport
    )(
      implicit env: Env
    ): F[Either[Err,SubmissionReport]]
  }


  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]

}
