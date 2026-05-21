package de.dnpm.ccdn.core


import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site


object Responsivity extends Enumeration {
  val success      = Value("fully")
  val mixedSuccess = Value("partial")
  val failure      = Value("offline")
}

case class ResponsivityReport(
  site: Code[Site],
  responsivity: Responsivity.Value,
  versionString: Option[String] = None
)