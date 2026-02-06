package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm


final class MVHReportingServiceTests extends AsyncFlatSpec
{

  val reportStore    = FakeReportRepository
  val dipConnector   = dip.Connector.getInstance.get
  val bfarmConnector = bfarm.Connector.getInstance.get

  val service =
    new MVHReportingService(
      Config.instance,
      reportStore,
      dipConnector, 
      bfarmConnector
    )

  "Dummy test" must "succeed" in {

    for {
      
      _ <- service.pollReports
      
      _ = reportStore.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions
      
    } yield reportStore.entries(_ => true) must be (empty)

  }

}
