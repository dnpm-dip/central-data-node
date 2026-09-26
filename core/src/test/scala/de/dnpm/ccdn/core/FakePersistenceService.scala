package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{MVHService, UseCase}
import play.api.libs.json.JsValue

import java.time.Instant


final class FakePersistenceServiceProvider extends PersistenceServiceProvider
{
  override def getInstance: PersistenceService =
    new FakePersistenceService
}

class FakePersistenceService extends PersistenceService
{
  override def writeSiteAvailabilityReports(
    reports: Iterable[ResponsivityReport],
    now: Instant
  ): Unit = ()

  override def backup(report: Report): Either[String, Unit] = ???

  override def backup(report: Report, submission: JsValue): Either[String, Unit] = ???

  override def backupForQuarterReport(report: Report): Either[String, Unit] = ???

  override def backup(site: Coding[Site], usecase: UseCase.Value, deletionEvent: MVHService.DeletionEvent): Either[String, Unit] = ???
}