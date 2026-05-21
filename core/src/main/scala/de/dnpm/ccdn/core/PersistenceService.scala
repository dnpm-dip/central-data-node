package de.dnpm.ccdn.core


import java.time.Instant
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
}

trait PersistenceServiceProvider extends SPI[PersistenceService]

object PersistenceService extends SPILoader[PersistenceServiceProvider]