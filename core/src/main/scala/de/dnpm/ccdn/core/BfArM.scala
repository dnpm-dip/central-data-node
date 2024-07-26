package de.dnpm.ccdn.core

import java.time.LocalDate
import scala.concurrent.{
  Future,
  ExecutionContext
}
import de.dnpm.ccdn.util.{
  Id,
  JsonFormatting,
  SPI,
  SPILoader
}
import play.api.libs.json.{
  Json,
  Format,
  Reads,
  Writes
}


object BfArM:

  object SubmissionReport:

    enum DataCategory:
      case Clinical

    object DataCategory extends JsonFormatting[DataCategory]:
      val names =
        Map(Clinical -> "clinical")

    enum DiseaseType:
      case Oncological
      case Rare

    object DiseaseType extends JsonFormatting[DiseaseType]:
      val names =
        Map(
          Oncological -> "oncological",
          Rare        -> "rare"
        )

    given Writes[SubmissionReport] =
      Json.writes[SubmissionReport]


  final case class SubmissionReport
  (
    submissionDate: LocalDate,
    submissionType: SubmissionType,
    localCaseId: Id[TTAN],
    submitterId: Id[Site],
    dataNodeId: Id[DataNode],
    diseaseType: SubmissionReport.DiseaseType,
    dataCategory: SubmissionReport.DataCategory,
    libraryType: SequencingType,
    dataQualityCheckedPassed: Boolean
  )



  trait ConnectorOps[F[_],Env,Err]:

    type Executable[T] = Env ?=> F[Either[Err,T]]

    def upload(report: SubmissionReport): Executable[SubmissionReport]



  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]

