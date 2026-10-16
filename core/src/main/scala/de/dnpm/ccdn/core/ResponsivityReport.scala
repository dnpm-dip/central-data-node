package de.dnpm.ccdn.core


import java.time.Instant
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site


object Responsivity extends Enumeration {
  val success      = Value("fully")
  val mixedSuccess = Value("partial")
  val failure      = Value("offline")
}

/**
 * @param timestamp when the response (or failure) was observed
 */
case class ResponsivityReport(
  site: Code[Site],
  responsivity: Responsivity.Value,
  timestamp: Instant,
  versionString: Option[String] = None
)