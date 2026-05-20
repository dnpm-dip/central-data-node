package de.dnpm.ccdn.core


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
}