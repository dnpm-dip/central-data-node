package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext



final class MVHReportingServiceTests extends AsyncFlatSpec
{
  override implicit def executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))
  val log = LoggerFactory.getLogger(MVHReportingServiceTests.super.getClass)

  behavior of "MVHReportingService"

  val fakeDipConnector = new FakeDIPConnector
  val fakeBfarmConnector = new FakeBfarmConnector

  val service = 
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      fakeDipConnector,
      fakeBfarmConnector
    )


  it must "handle multiple uploads from every DIP node in one go" in {
    log.info("FakeDipConnector sending "+fakeDipConnector.nSubmissions+ " submissions per site")
    for {
      
      _ <- service.pollReports
      
      _ = service.pollingQueue.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions

    } yield service.pollingQueue.entries(_ => true) must be (empty)
  }

  it must " not process more submissions simultaneously than it has threads (non-deterministic)" in {
    //configure bfarmconnecteor to halt for 100 msec during upload

    log.info("FakeDipConnector sending "+fakeDipConnector.nSubmissions+ " submissions per site")
    fakeDipConnector.confirmationsTakeTime = true
    fakeDipConnector.maxSimultaneousConfirmationWaits.set(0)

    // testing setup has 39 clinic-usecases, so there can be no less than 39 uploads to run
    val expectedNumReports = Config.instance.sites.flatMap(it => it._2.useCases).size * fakeDipConnector.nSubmissions
    //have more reports overall than nThreads
    assert(expectedNumReports > service.nSimultaneousSubmissionConfirmations)
    //but no more than twice as many, so that a boolean predicate works to split one set of timings in two
    assert(expectedNumReports <= service.nSimultaneousSubmissionConfirmations*fakeDipConnector.nSubmissions)

    //run
    for{

      _ <- service.pollReports

      _ <- service.uploadReports

      _ <- service.confirmSubmissions

    } yield{
      assertResult(service.nSimultaneousSubmissionConfirmations)(fakeDipConnector.maxSimultaneousConfirmationWaits.get())

    }
  }
}
