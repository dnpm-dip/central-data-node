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

  object SubmissionReport:

    enum Domain:
      case Oncology
      case RareDiseases

    object Domain extends JsonFormatting[Domain]:
      val names =
        Map(
          Oncology     -> "oncology",
          RareDiseases -> "rare-diseases"
        )

    given Format[SubmissionReport] =
      Json.format[SubmissionReport]


  final case class SubmissionReport
  (
    createdOn: LocalDateTime,
    site: Coding[Site],
    domain: SubmissionReport.Domain,
    transferTAN: Id[TTAN],
    submissionType: SubmissionType,
    sequencingType: SequencingType,
    qcPassed: Boolean
  )



  type SiteDomains = (Coding[Site],Set[SubmissionReport.Domain])

  trait ConnectorOps[F[_],Env,Err]:

    def siteInfos: Env ?=> F[Either[Err,List[SiteDomains]]]

    def dataSubmissionReports(
      site: Coding[Site],
      domains: Set[SubmissionReport.Domain],
      period: Option[Period[LocalDateTime]] = None
    ): Env ?=> F[Either[Err,Seq[SubmissionReport]]]



  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]


