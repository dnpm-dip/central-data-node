package de.dnpm.ccdn.core.dip


import cats.Applicative
import cats.implicits.toTraverseOps

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Either
import de.dnpm.dip.util.{SPI, SPILoader}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{Submission, UseCase}


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

  def batchConfirmSubmitted(reports: List[Submission.Report],batchSize: Int = 50)
                           (implicit env: Env,applicative: Applicative[F]): F[List[Either[Err,Submission.Report]]] =
    reports.grouped(batchSize)
      .toList
      .map(batch => batch.traverse(curSubm => confirmSubmitted(curSubm)))
      .flatSequence
}


trait Connector extends ConnectorOps[Future,ExecutionContext,String]

trait ConnectorProvider extends SPI[Connector]

object Connector extends SPILoader[ConnectorProvider]

