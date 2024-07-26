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
import DNPM.SubmissionReport.Domain
import Domain._



final class FakeDNPMConnectorProvider extends DNPM.ConnectorProvider:

  override def getInstance: DNPM.Connector =
    FakeDNPMConnector




object FakeDNPMConnector extends DNPM.Connector:

  private val rnd = new Random


  private def oneOf[T, C[x] <: Iterable[x]](ts: C[T]): T =
    ts.toSeq(rnd.nextInt(ts.size))


  private def rndReport(
    site: Coding[Site],
    domain: Domain
  ): DNPM.SubmissionReport =
    DNPM.SubmissionReport(
      LocalDateTime.now,
      site,
      domain,
      Id[TTAN](randomUUID.toString),
      Initial,
      WGS,
      rnd.nextBoolean
    )


  def siteInfos: ExecutionContext ?=> Future[Either[String,List[DNPM.SiteDomains]]] =
    Future.successful(
      Right(
        List(
          (
            Coding[Site](oneOf(Config.instance.submitterIds.keys).toString),
            Set(Oncology,RareDiseases)
          )
        )
      )
    )


  def dataSubmissionReports(
    site: Coding[Site],
    domains: Set[Domain],
    period: Option[Period[LocalDateTime]] = None
  ): ExecutionContext ?=> Future[Either[String,Seq[DNPM.SubmissionReport]]] =
    Future.successful(
      Right(
        Seq.fill(4)(rndReport(site,oneOf(domains)))
      )
    )


