package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.Encryptor.Encrypted
import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}


/**
 * Supposed to longterm persist some data, for example whether DIP
 * sites were available or backup of submission data
 */
trait LongTermStore
{
  def writeSiteAvailabilityReports(reports: Iterable[ResponsivityReport]): Unit

  /*
   * The backup methods take their content already encrypted, so that backups never
   * depend on the implementation to encrypt them. Only the identifying metadata
   * (taken from the report or deletion event) may be stored in plain text.
   * The content is by-name, so that implementations only evaluate (i.e. encrypt) it
   * if they actually store it, not e.g. if the backup already exists.
   */

  /**
   * @param content the report, encrypted
   */
  def backupReport(report: Report, content: => Encrypted): Either[String, Unit]

  /**
   * @param content the submission of the report, encrypted
   */
  def backupSubmission(report: Report, content: => Encrypted): Either[String, Unit]

  /**
   * @param content the deletion event, encrypted
   */
  def backupDeletion(site:Coding[Site], usecase:UseCase.Value, deletionEvent:DeletionEvent, content: => Encrypted): Either[String, Unit]

  /**
   * Removes all backed up submissions and reports matching the deletion event's tan, site
   * and usecase, then backs up the deletion event itself (unless already present).
   *
   * @param content the deletion event, encrypted, see [[backupDeletion]]
   */
  def applyDeletion(site:Coding[Site], usecase:UseCase.Value, deletionEvent:DeletionEvent, content: => Encrypted): Either[String, Unit]

  def backupForQuarterReport(report: Report): Either[String, Unit]

  /**
   * Runs `f` with a LongTermStore whose operations all share the same underlying
   * resources (e.g. a database client), which are released once the returned Future completes.
   * By default, `f` is simply run with this store itself.
   */
  def withSession[T](f: LongTermStore => Future[T])(implicit @unused ec: ExecutionContext): Future[T] =
    f(this)
}

trait LongTermStoreProvider extends SPI[LongTermStore]

object LongTermStore extends SPILoader[LongTermStoreProvider]