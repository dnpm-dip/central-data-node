package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import play.api.libs.json.JsValue

import java.time.Instant
import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}


/**
 * Supposed to longterm persist some data, for example whether DIP
 * sites were available. Single implementation writes into mongoDB
 */
trait PersistenceService
{
  def writeSiteAvailabilityReports(
    reports: Iterable[ResponsivityReport],
    now: Instant
  ): Unit

  def backupReport(report: Report): Either[String, Unit]

  def backupSubmission(report: Report, submission: JsValue): Either[String, Unit]

  def backupDeletion(site:Coding[Site], usecase:UseCase.Value, deletionEvent:DeletionEvent): Either[String, Unit]

  /**
   * Removes all backed up submissions and reports matching the deletion event's tan, site
   * and usecase, then backs up the deletion event itself (unless already present).
   */
  def applyDeletion(site:Coding[Site], usecase:UseCase.Value, deletionEvent:DeletionEvent): Either[String, Unit]

  def backupForQuarterReport(report: Report): Either[String, Unit]

  /**
   * Runs `f` with a PersistenceService whose operations all share the same underlying
   * resources (e.g. a database client), which are released once the returned Future completes.
   * By default, `f` is simply run with this service itself.
   */
  def withSession[T](f: PersistenceService => Future[T])(implicit @unused ec: ExecutionContext): Future[T] =
    f(this)
}

trait PersistenceServiceProvider extends SPI[PersistenceService]

object PersistenceService extends SPILoader[PersistenceServiceProvider]