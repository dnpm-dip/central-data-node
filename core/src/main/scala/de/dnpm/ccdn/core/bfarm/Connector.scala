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
import play.api.libs.json.Writes


trait ConnectorOps[F[_],Env,Err]
{
/*  
  def upload(
    report: SubmissionReport
  )(
    implicit env: Env
  ): F[Either[Err,SubmissionReport]]
*/

  def upload[Case,MolSeq,Plan,FU](
    report: SubmissionReport[Case,MolSeq,Plan,FU]
  )(
    implicit
    env: Env,
    w: Writes[SubmissionReport[Case,MolSeq,Plan,FU]]
  ): F[Either[Err,SubmissionReport[Case,MolSeq,Plan,FU]]]
}


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

