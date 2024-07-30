package de.dnpm.ccdn.core

import java.time.LocalDate
import scala.concurrent.{
  Future,
  ExecutionContext
}
import de.dnpm.ccdn.util.{
  Id,
  json,
  SPI,
  SPILoader
}
import play.api.libs.json.{
  Json,
  Writes
}


object BfArM:

  object SubmissionReport:

    enum DataCategory:
      case Clinical


    enum DiseaseType:
      case Oncological
      case Rare

    object DataCategory:
      given Writes[DataCategory] =
        json.enumWrites[DataCategory](
          Map(Clinical -> "clinical")
        )

    object DiseaseType:
      given Writes[DiseaseType] =
        json.enumWrites[DiseaseType](
          Map(
            Oncological -> "oncological",
            Rare        -> "rare"
          )
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

