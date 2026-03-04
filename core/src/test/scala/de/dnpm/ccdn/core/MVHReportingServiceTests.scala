package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._

import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{Duration, SECONDS}


final class MVHReportingServiceTests extends AsyncFlatSpec
{
  behavior of "MVHReportingService"



  private class TestFixture(nSubmissions:Int, bfarmUploadTakesTime:Boolean) {
    val reportStore = FakeReportRepository()
    //becomes instance of FakeDNPMConnector, through custom Service Provider config
    val dipConnector = FakeDIPConnector(nSubmissions)
    //becomes instance of FakeBfArMConnector
    val bfarmConnector = FakeBfArMConnector(bfarmUploadTakesTime)
    //actually setting to the actual object works just as well

    val service: MVHReportingService = {
      new MVHReportingService(
        Config.instance,
        reportStore,
        dipConnector,
        bfarmConnector
      )(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(MVHReportingService.nThreads)))//TODO absurd to copypaste this, probably have to use injection and use the "service" member of the companion object
    }
  }

  it must "pass a dummy test" in {
    val fixture = new TestFixture(4,false)

    val result = for {
      
      _ <- fixture.service.pollReports
      
      _ = fixture.reportStore.entries(_ => true) must not be (empty)

      _ <- fixture.service.uploadReports

      _ <- fixture.service.confirmSubmissions
      
    } yield fixture.reportStore.entries(_ => true) must be (empty)

    fixture.service.stop()
    result
  }

  it must "not process more submissions simultaneously than it has threads" in {
    //let n be the numThreads in MVHReportingService, let m be 8
    /*val n = MVHReportingService.nThreads
    val m = 8
*/
    //m should stay below nThreads so that no more than 2 sets of threads are run and no more time is wasted on this test than necessary
    //assert(n > m)

    //configure reportStore to have n+m submissions in it (via dip connector)
    //configure bfarmconnecteor to halt for 100 msec during upload
    val fixture = new TestFixture(1,true) //TODO tatsächlich gibts 40*39 uploads, weil es 39 Klinikusecases in der config.json

    val expectedNumReports = Config.instance.sites.flatMap(it => it._2.useCases).size //39
    assert(expectedNumReports > MVHReportingService.nThreads)
    //run
    Await.result(fixture.service.pollReports,Duration(5,SECONDS))
    Await.result(fixture.service.uploadReports,Duration(5,SECONDS))

    for(t <- fixture.bfarmConnector.uploadFinishTimings.get().sorted){
      println(t)
    }

    assertResult(expectedNumReports)(fixture.bfarmConnector.uploadFinishTimings.get.size)


    //verify that n submissions were transmitted at pretty much the same time (within 30 msec) and that m were at least 100 msec later


    //TODO implement
    fixture.service.stop()
    succeed
  }


}
