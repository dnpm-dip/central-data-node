package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._



final class MVHReportingServiceTests extends AsyncFlatSpec
{
  behavior of "MVHReportingService"

    //cant instantiate proper fixtures, because MVHReportingService is a singleton
    // and I don't want to copypaste from the companion object into testcode
    val service = MVHReportingService.service
    val fakeDipConnector:FakeDIPConnector = service.dipConnector.asInstanceOf[FakeDIPConnector]
    val fakeBfarmConnector:FakeBfarmConnector = service.bfarmConnector.asInstanceOf[FakeBfarmConnector]



  it must "handle multiple uploads from every DIP node in one go" in {
    fakeDipConnector.nSubmissions = 4
    fakeDipConnector.confirmationsTakeTime = false

    for {

      _ <- service.pollReports

      _ = service.queue.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions

    } yield service.queue.entries(_ => true) must be (empty)
  }

  it must " not process more submissions simultaneously than it has threads (non-deterministic)" in {
    //configure bfarmconnecteor to halt for 100 msec during upload
    fakeDipConnector.nSubmissions = 1
    fakeDipConnector.confirmationsTakeTime = true
    fakeDipConnector.maxSimultaneousConfirmationWaits.set(0)

    // testing setup has 39 clinic-usecases, so there can be no less than 39 uploads to run
    val expectedNumReports = Config.instance.sites.flatMap(it => it._2.useCases).size
    //have more reports overall than nThreads
    assert(expectedNumReports > MVHReportingService.nConfirmationThreads)
    //but no more than twice as many, so that a boolean predicate works to split one set of timings in two
    assert(expectedNumReports <= MVHReportingService.nConfirmationThreads*2)

    //run
    for{

      _ <- service.pollReports

      _ <- service.uploadReports

      _ <- service.confirmSubmissions

    } yield{
      assertResult(MVHReportingService.nConfirmationThreads)(fakeDipConnector.maxSimultaneousConfirmationWaits.get())

    }
  }

}
