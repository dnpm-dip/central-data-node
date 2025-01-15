package de.dnpm.ccdn.core


import java.time.LocalDateTime
import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{
  Id,
  Site
}
import play.api.libs.json.{
  Json,
  Format
}


object DNPM
{

  object UseCase extends Enumeration
  {
    val MTB = Value("MTB")
    val RD  = Value("RD")

    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }

  object SequencingType extends Enumeration
  {
    val Panel    = Value("panel")
    val Exome    = Value("exome")
    val Genome   = Value("genome-short-read")
    val GenomeLr = Value("genome-long-read")

    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }


  final case class SubmissionReport
  (
    createdOn: LocalDateTime,
    site: Coding[Site],
    useCase: UseCase.Value,
    transferTAN: Id[TTAN],
    submissionType: SubmissionType.Value,
    sequencingType: Option[SequencingType.Value],
    qcPassed: Boolean
  )

  object SubmissionReport
  {
    implicit val format: Format[SubmissionReport] =
      Json.format[SubmissionReport]
  }


  trait ConnectorOps[F[_],Env,Err]
  {

    def sites(implicit env: Env): F[Either[Err,List[Coding[Site]]]]

    def dataSubmissionReports(
      site: Coding[Site],
      period: Option[Period[LocalDateTime]] = None
    )(
      implicit env: Env
    ): F[Either[Err,Seq[SubmissionReport]]]
  }


  type Connector = ConnectorOps[Future,ExecutionContext,String]

  trait ConnectorProvider extends SPI[Connector]

  object Connector extends SPILoader[ConnectorProvider]

}
