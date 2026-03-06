package de.dnpm.ccdn.core.bfarm


import scala.concurrent.{
  Future,
  ExecutionContext
}
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}


trait BfarmConnectorOps[F[_],Env,Err]
{
  def upload(
    report: SubmissionReport
  )(
    implicit env: Env
  ): F[Either[Err,Unit]]

}

trait BfarmConnector extends BfarmConnectorOps[Future,ExecutionContext,String]

trait BfarmConnectorProvider extends SPI[BfarmConnector]

object BfarmConnector extends SPILoader[BfarmConnectorProvider]

