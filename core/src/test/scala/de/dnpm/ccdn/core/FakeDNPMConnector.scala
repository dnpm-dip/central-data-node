package de.dnpm.ccdn.core


import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.util.{
  Either,
  Random
}
import scala.concurrent.{
  Future,
  ExecutionContext
}
import cats.syntax.either._
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{
  Id,
  Site
}
import SubmissionType.Initial
import DNPM.UseCase._
import DNPM.SequencingType._



final class FakeDNPMConnectorProvider extends DNPM.ConnectorProvider
{
  override def getInstance: DNPM.Connector =
    FakeDNPMConnector
}


object FakeDNPMConnector extends DNPM.Connector
{

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
      rnd.nextBoolean()
    )


  private val siteUseCases: Map[Coding[Site],Set[DNPM.UseCase.Value]] =
    Config.instance
      .submitterIds
      .keys
      .map(
        code =>
          Coding[Site](code.toString) -> useCases
      )
      .toMap



  override def sites(implicit ec: ExecutionContext): Future[Either[String,List[Coding[Site]]]] =
    Future.successful(
      siteUseCases.keys.toList.asRight
    )


  override def dataSubmissionReports(
    site: Coding[Site],
    period: Option[Period[LocalDateTime]] = None
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,Seq[DNPM.SubmissionReport]]] =
    Future.successful(
      Seq.fill(4)(rndReport(site)).asRight
    )

}
