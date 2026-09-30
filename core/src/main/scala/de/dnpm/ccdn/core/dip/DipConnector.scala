package de.dnpm.ccdn.core.dip


import de.dnpm.ccdn.core.dip.Report.Filter

import java.time.LocalDateTime
import scala.concurrent.{
  Future,
  ExecutionContext
}
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import play.api.libs.json.JsValue


trait DipConnectorOps[F[_],Env,Err]
{

  def submissionReports(
    site: Code[Site],
    useCase: UseCase.Value,
    filter: Filter
  )(
    implicit env: Env
  ): F[Either[Err,Seq[Report]]]


  /**
   * Queries the given site for [[DeletionEvent]]s that occurred for the given
   * UseCase. If since is defined, only events at or after that point in time
   * are returned, else the site's complete history of deletions is returned.
   */
  def deletionEvents(
    site: Code[Site],
    useCase: UseCase.Value,
    since: Option[LocalDateTime]
  )(
    implicit env: Env
  ): F[Either[Err,Seq[DeletionEvent]]]


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

  /**
   * Downloads the Submission belonging to the given Report as raw JSON
   */
  def downloadSubmission(
    report: Report
  )(
    implicit env: Env
  ): F[Either[Err,JsValue]]

}

trait DipConnector extends DipConnectorOps[Future,ExecutionContext,String]

trait DipConnectorProvider extends SPI[DipConnector]

object DipConnector extends SPILoader[DipConnectorProvider]

