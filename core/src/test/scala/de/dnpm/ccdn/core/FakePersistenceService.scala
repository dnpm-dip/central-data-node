package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.model.PatientRecord
import de.dnpm.dip.service.mvh.Submission

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

  override def backup[T <: PatientRecord](report: Report, submission: Submission[T]): Either[String, Unit] = ???
}