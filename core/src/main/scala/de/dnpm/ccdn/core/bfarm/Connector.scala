package de.dnpm.ccdn.core.bfarm


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}


trait ConnectorOps[F[_],Env,Err]
{
  def upload(
    report: SubmissionReport
  )(
    implicit env: Env
  ): F[Either[Err,Unit]]

}


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

