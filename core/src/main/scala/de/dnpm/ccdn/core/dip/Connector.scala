package de.dnpm.ccdn.core.dip


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{
  Submission,
  UseCase
}


trait ConnectorOps[F[_],Env,Err]
{

  def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Submission.Report.Filter
  )(
    implicit env: Env
  ): F[Either[Err,Seq[Submission.Report]]]

/*
  def submissionReports(
    site: Code[Site],
    useCases: Set[UseCase.Value],
    filter: Submission.Report.Filter
  )(
    implicit env: Env
  ): F[Either[Err,Seq[Submission.Report]]]
*/


  def confirmSubmitted(
    report: Submission.Report
  )(
    implicit env: Env
  ): F[Either[Err,Unit]]

}


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

