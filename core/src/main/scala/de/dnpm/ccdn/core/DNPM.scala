package de.dnpm.ccdn.core


import java.time.LocalDateTime
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


object DNPM:

  enum UseCase:
    case MTB, RD

  object UseCase extends JsonFormatting[UseCase]:
    override val names =
      Map(
        MTB -> "MTB",
        RD  -> "RD"
      )


  object SubmissionReport:

    given Format[SubmissionReport] =
      Json.format[SubmissionReport]


  final case class SubmissionReport
  (
    createdOn: LocalDateTime,
    site: Coding[Site],
    domain: UseCase,
    transferTAN: Id[TTAN],
    submissionType: SubmissionType,
    sequencingType: SequencingType,
    qcPassed: Boolean
  )



  trait ConnectorOps[F[_],Env,Err]:

    def sites: Env ?=> F[Either[Err,List[Coding[Site]]]]

    def dataSubmissionReports(
      site: Coding[Site],
      period: Option[Period[LocalDateTime]] = None
    ): Env ?=> F[Either[Err,Seq[SubmissionReport]]]



  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]


