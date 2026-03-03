package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm


final class MVHReportingServiceTests extends AsyncFlatSpec
{
  behavior of "MVHReportingService"



  private class TestFixture {
    val reportStore    = FakeReportRepository
    //becomes instance of FakeDNPMConnector, through custom Service Provider config
    val dipConnector   = dip.Connector.getInstance.get
    //becomes instance of FakeBfArMConnector
    val bfarmConnector = bfarm.Connector.getInstance.get
    //actually setting to the actual object works just as well

    val service = {
      new MVHReportingService(
        Config.instance,
        reportStore,
        dipConnector,
        bfarmConnector
      )
    }

  }

  it must "pass a dummy test" in {
    val fixture = new TestFixture

    for {
      
      _ <- fixture.service.pollReports
      
      _ = fixture.reportStore.entries(_ => true) must not be (empty)

      _ <- fixture.service.uploadReports

      _ <- fixture.service.confirmSubmissions
      
    } yield fixture.reportStore.entries(_ => true) must be (empty)

  }

  it must "not process more submissions simultaneously than it has threads" in {
    //let n be the numThreads in MVHReportingService, let m be 8
    val n = MVHReportingService.nThreads
    val m = 8
    assert(n > m) //m should stay below nThreads so that no more than 2 sets of threads are run and no more time is wasted on this test than necessary
    //configure reportStore to have n+m submissions in it (via dip connector
    //configure bfarmconnecteor to halt for 100 msec during upload
    //verify that n submissions were transmitted at pretty much the same time (within 30 msec) and that m were at least 100 msec later

    //TODO implement

    fail
  }

}
