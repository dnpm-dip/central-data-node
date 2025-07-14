package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm


final class Tests extends AsyncFlatSpec
{

  val reportQueue    = FakeReportQueue
  val dipConnector   = dip.Connector.getInstance.get
  val bfarmConnector = bfarm.Connector.getInstance.get

  val service =
    new MVHReportingService(
      Config.instance,
      reportQueue,
      dipConnector, 
      bfarmConnector
    )

  "Dummy test" must "succeed" in {

    for {
      
      _ <- service.pollReports
      
      _ = reportQueue.queue must not be (empty)

      _ <- service.uploadReports
      
    } yield reportQueue.queue must be (empty)

  }

}
