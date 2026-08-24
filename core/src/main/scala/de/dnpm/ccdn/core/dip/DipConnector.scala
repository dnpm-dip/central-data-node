package de.dnpm.ccdn.core.dip


import de.dnpm.ccdn.core.dip.Report.Filter

import scala.concurrent.{ExecutionContext, Future}
import de.dnpm.dip.util.{SPI, SPILoader}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{PatientRecord, Site}
import de.dnpm.dip.service.mvh.{Submission, UseCase}


trait DipConnectorOps[F[_],Env,Err]
{

  def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Filter
  )(
    implicit env: Env
  ): F[Either[Err,Seq[Report]]]


  def confirmSubmitted(
    report: Report
  )(
    implicit env: Env
  ): F[Either[Err,Report]]

  /**
   * Asks the given site what version it is and returns the version string
   * (extracted from json)
   */
  def getApiVersion(site:Code[Site])(implicit env: Env): F[Either[Err,String]]

  def downloadSubmission[T <: PatientRecord](
    report: Report
  )(
    implicit env: Env
  ): F[Either[Err,Submission[T]]]

}

trait DipConnector extends DipConnectorOps[Future,ExecutionContext,String]

trait DipConnectorProvider extends SPI[DipConnector]

object DipConnector extends SPILoader[DipConnectorProvider]

