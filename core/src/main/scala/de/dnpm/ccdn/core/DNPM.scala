package de.dnpm.ccdn.core


import java.time.LocalDateTime
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
  Format,
  Reads,
  Writes
}


object DNPM:

  enum UseCase:
    case MTB, RD

  object UseCase:
    given Format[UseCase] =
      json.enumFormat[UseCase](
        Map(
          MTB -> "MTB",
          RD  -> "RD"
        )
      )

  enum SequencingType:
    case Panel
    case Exome
    case Genome
    case GenomeLr

  object SequencingType:
    given Format[SequencingType] =
      json.enumFormat[SequencingType](
        Map(
          Panel    -> "panel",
          Exome    -> "exome",
          Genome   -> "genome-short-read",
          GenomeLr -> "genome-long-read"
        )
      )



  object SubmissionReport:

    inline given Format[SubmissionReport] =
      Json.format[SubmissionReport]


  final case class SubmissionReport
  (
    createdOn: LocalDateTime,
    site: Coding[Site],
    useCase: UseCase,
    transferTAN: Id[TTAN],
    submissionType: SubmissionType,
    sequencingType: Option[SequencingType],
    qcPassed: Boolean
  )



  trait ConnectorOps[F[_],Env,Err]:

    def sites: Env ?=> F[List[Coding[Site]] | Err]

    def dataSubmissionReports(
      site: Coding[Site],
      period: Option[Period[LocalDateTime]] = None
    ): Env ?=> F[Seq[SubmissionReport] | Err]



  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]


