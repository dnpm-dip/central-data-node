package de.dnpm.ccdn.connector


import org.scalatest.flatspec.AnyFlatSpec
import de.dnpm.ccdn.core.DNPM
import de.dnpm.ccdn.core.BfArM


final class Tests extends AnyFlatSpec:

  System.setProperty("dnpm.ccdn.broker.baseurl","http://localhost")
  System.setProperty("dnpm.ccdn.bfarm.baseurl","http://localhost/bfarm")


  val dnpmConnector =
    DNPM.Connector.getInstance

  val bfarmConnector =
    BfArM.Connector.getInstance


  "SPI Loaders" must "have worked" in {
    assert(dnpmConnector.isSuccess)

    assert(bfarmConnector.isSuccess)
  }


