package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import play.api.libs.json.JsValue

import java.time.Instant
import de.dnpm.dip.util.{SPI, SPILoader}


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

  def backup(report: Report): Either[String, Unit]

  def backup(report: Report, submission: JsValue): Either[String, Unit]

  def backup(site:Coding[Site],usecase:UseCase.Value,deletionEvent:DeletionEvent): Either[String, Unit]

  def backupForQuarterReport(report: Report): Either[String, Unit]
}

trait PersistenceServiceProvider extends SPI[PersistenceService]

object PersistenceService extends SPILoader[PersistenceServiceProvider]