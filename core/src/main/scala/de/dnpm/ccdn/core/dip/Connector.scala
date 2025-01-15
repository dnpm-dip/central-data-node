package de.dnpm.ccdn.core.dip


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
import de.dnpm.dip.model.Site
import de.dnpm.ccdn.core.Period


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


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

