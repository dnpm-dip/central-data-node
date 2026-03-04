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
    //configure bfarmconnecteor to halt for 100 msec during upload
    //testing setup has 39 clinic-usecases, so there can be no less than 39 uploads to run
    val fixture = new TestFixture(1,true) //TODO tatsächlich gibts 40*39 uploads, weil es 39 Klinikusecases in der config.json

    //have more reports overall than nThreads
    val expectedNumReports = Config.instance.sites.flatMap(it => it._2.useCases).size //39
    assert(expectedNumReports > MVHReportingService.nThreads)
    //but no more than twice as many
    assert(expectedNumReports <= MVHReportingService.nThreads*2)

    //run
    val startTime = System.currentTimeMillis()
    Await.result(fixture.service.pollReports,Duration(5,SECONDS))
    Await.result(fixture.service.uploadReports,Duration(5,SECONDS))

    val allUploadTimings = fixture.bfarmConnector.uploadFinishTimings.get()
    //expect that theres a contingent of uploads that all happened within 50msec, 100msec after the simulation started
    def firstUploadPredicate:Long=>Boolean = _ - startTime - FakeBfArMConnector.uploadDelayMsec < 50
    val firstUploads = allUploadTimings.filter(firstUploadPredicate)
    val remainingUploads = allUploadTimings.filter(!firstUploadPredicate(_))

    //verifying basics
    assertResult(expectedNumReports)(allUploadTimings.size)
    assertResult(expectedNumReports)(firstUploads.size+remainingUploads.size)

    //verify that <nThreads> submissions were transmitted at pretty much the same time (within 30 msec) and that m were at least 100 msec later
    assertResult(MVHReportingService.nThreads)(firstUploads.size)


    //TODO implement
    fixture.service.stop()
    succeed
  }


}
