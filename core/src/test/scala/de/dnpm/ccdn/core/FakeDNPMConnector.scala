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
import DNPM.UseCase._
import DNPM.SequencingType._



final class FakeDNPMConnectorProvider extends DNPM.ConnectorProvider:

  override def getInstance: DNPM.Connector =
    FakeDNPMConnector




object FakeDNPMConnector extends DNPM.Connector:

  private val rnd = new Random


  private def oneOf[T, C[x] <: Iterable[x]](ts: C[T]): T =
    ts.toSeq(rnd.nextInt(ts.size))


  private val useCases =
    Set(MTB,RD)

  private val seqTypes =
    Set(Panel,Exome,Genome,GenomeLr)

  private def rndReport(
    site: Coding[Site],
  ): DNPM.SubmissionReport =
    DNPM.SubmissionReport(
      LocalDateTime.now,
      site,
      oneOf(useCases),
      Id[TTAN](randomUUID.toString),
      Initial,
      Some(oneOf(seqTypes)),
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



  override def sites: ExecutionContext ?=> Future[List[Coding[Site]] | String] =
    Future.successful(
      siteUseCases.keys.toList
    )


  override def dataSubmissionReports(
    site: Coding[Site],
    period: Option[Period[LocalDateTime]] = None
  ): ExecutionContext ?=> Future[Seq[DNPM.SubmissionReport] | String] =
    Future.successful(
      Seq.fill(4)(rndReport(site))
    )

