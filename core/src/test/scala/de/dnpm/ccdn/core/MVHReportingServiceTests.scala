package de.dnpm.ccdn.core


import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._



final class MVHReportingServiceTests extends AsyncFlatSpec with BeforeAndAfterEach
{
  behavior of "MVHReportingService"


  val reportStore    = FakeReportRepository
  //becomes instance of FakeDNPMConnector, through custom Service Provider config
  val dipConnector   = dip.Connector.getInstance.get
  //becomes instance of FakeBfArMConnector
  val bfarmConnector = bfarm.Connector.getInstance.get
  //actually setting to the actual object works just as well

  val service =
    new MVHReportingService(
      Config.instance,
      reportStore,
      dipConnector, 
      bfarmConnector
    )

  it must "handle multiple uploads from every DIP node in one go" in {

    for {
      
      _ <- service.pollReports
      
      _ = reportStore.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions
      
    } yield reportStore.entries(_ => true) must be (empty)

  }

}