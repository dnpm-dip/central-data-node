package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.util.Random
import scala.concurrent.{
  Future,
  ExecutionContext
}
import de.dnpm.ccdn.util.Id
import SubmissionType.Initial
import SequencingType.WGS
import DNPM.UseCase._
//import DNPM.SubmissionReport.Domain
//import Domain._



final class FakeDNPMConnectorProvider extends DNPM.ConnectorProvider:

  override def getInstance: DNPM.Connector =
    FakeDNPMConnector




object FakeDNPMConnector extends DNPM.Connector:

  private val rnd = new Random


  private def oneOf[T, C[x] <: Iterable[x]](ts: C[T]): T =
    ts.toSeq(rnd.nextInt(ts.size))


  private val useCases =
    Set(
      MTB,
      RD
    )

  private def rndReport(
    site: Coding[Site],
  ): DNPM.SubmissionReport =
    DNPM.SubmissionReport(
      LocalDateTime.now,
      site,
      oneOf(useCases),
      Id[TTAN](randomUUID.toString),
      Initial,
      WGS,
      rnd.nextBoolean
    )


  private val siteUseCases: Map[Coding[Site],Set[DNPM.UseCase]] =
    Config.instance
      .submitterIds
      .keys
      .map(
        code =>
          Coding[Site](code.toString) -> useCases
      )
      .toMap



  override def sites: ExecutionContext ?=> Future[Either[String,List[Coding[Site]]]] =
    Future.successful(
      Right(
        siteUseCases.keys.toList
      )
    )


  override def dataSubmissionReports(
    site: Coding[Site],
    period: Option[Period[LocalDateTime]] = None
  ): ExecutionContext ?=> Future[Either[String,Seq[DNPM.SubmissionReport]]] =
    Future.successful(
      Right(
        Seq.fill(4)(rndReport(site))
      )
    )
