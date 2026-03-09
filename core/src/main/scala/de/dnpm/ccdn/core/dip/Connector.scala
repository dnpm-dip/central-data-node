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
import cats.Applicative
import cats.syntax.traverse._


trait ConnectorOps[F[_],Env,Err]
{

  def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Submission.Report.Filter
  )(
    implicit env: Env
  ): F[Either[Err,Seq[Submission.Report]]]

  
  def confirmSubmitted(
    report: Submission.Report
  )(
    implicit env: Env
  ): F[Either[Err,Submission.Report]]

  /**
   * Default implementation of batch submission confirmation,
   * in terms of the "primitive" confirmSubmitted method above
   *
   * @param reports Submission.Reports for which to confirm submission
   * @param batchSize Size of batches into which to subdivide reports (default: 50)
   */
  def batchConfirmSubmitted(
    reports: List[Submission.Report],
    batchSize: Int = 50
  )(
    implicit
    env: Env,
    applicative: Applicative[F]
  ): F[List[Either[Err,Submission.Report]]] =
    reports.grouped(batchSize)
      .toList
      .map(_.traverse(confirmSubmitted))
      .flatSequence

}


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

