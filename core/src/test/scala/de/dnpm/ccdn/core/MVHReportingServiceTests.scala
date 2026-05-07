package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

import java.time.{Clock, Instant, ZoneOffset}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{Submission, UseCase}



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

  val sites = Config.instance.sites.keys.toSeq

  it must "handle multiple uploads from every DIP node in one go" in {
    log.info("FakeDipConnector sending "+fakeDipConnector.nSubmissions+ " submissions per site")
    for {
      
      _ <- service.pollReports(sites,ListBuffer.empty)
      
      _ = service.pollingQueue.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions(ListBuffer.empty)

    } yield service.pollingQueue.entries(_ => true) must be (empty)
  }

  // Helper to create a DipConnector that returns a fixed version string and empty reports
  private def connectorReturningVersion(version: String) = new dip.DipConnector {
    override def getApiVersion(site: Code[Site])(implicit env: ExecutionContext)
    : Future[Either[String, String]] =
      Future.successful(Right(version))
    override def submissionReports(site: Code[Site], useCase: UseCase.Value, filter: Submission.Report.Filter)
        (implicit ec: ExecutionContext): Future[Either[String, Seq[Submission.Report]]] =
      Future.successful(Right(Seq.empty))
    override def confirmSubmitted(report: Submission.Report)(implicit ec: ExecutionContext)
    : Future[Either[String, Submission.Report]] =
      Future.successful(Right(report))
  }

  it must "record Responsivity.success for every reachable site in conductPollingCycle and capture the correct timestamp" in {
    // Responsivity reflects connectivity (Right response), not version acceptance.
    // Any version yields success before the cutover date.
    val fixedInstant = Instant.parse("2026-04-30T12:00:00Z")

    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("0.9.0"),
      fakeBfarmConnector
    )
    testService.clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    val capturedReports = ListBuffer.empty[testService.ResponsivityReport]
    var capturedInstant: Option[Instant] = None
    testService.responsivitySink = { (reports, now) =>
      capturedReports ++= reports
      capturedInstant = Some(now)
    }

    for {
      _ <- testService.conductReportingWorkflow()
    } yield {
      capturedInstant must be(Some(fixedInstant))
      capturedReports must not be empty
      assert(capturedReports.forall(_.responsivity == testService.Responsivity.success))
    }
  }

  it must "include all reachable sites as valid before the version-check cutover date, regardless of version" in {
    val preCutover = Instant.parse("2026-05-31T23:59:59Z")
    val testService = new MVHReportingService(
      Config.instance, FakeReportRepository(), connectorReturningVersion("0.9.0"),
      fakeBfarmConnector
    )
    testService.clock = Clock.fixed(preCutover, ZoneOffset.UTC)

    testService.checkSiteApiVersion(ListBuffer.empty).map { validSites =>
      assertResult(Config.instance.sites.size)(validSites.size)
    }
  }

  it must "include sites with a 1.3.x version as valid on and after the cutover date" in {
    val onCutover = Instant.parse("2026-06-01T00:00:00Z")
    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("1.3.0-RELEASE_5"),
      fakeBfarmConnector
    )
    testService.clock = Clock.fixed(onCutover, ZoneOffset.UTC)

    testService.checkSiteApiVersion(ListBuffer.empty).map { validSites =>
      assertResult(Config.instance.sites.size)(validSites.size)
    }
  }

  it must "exclude sites with a 1.2.x version from valid sites on and after the cutover date" in {
    val onCutover = Instant.parse("2026-06-01T00:00:00Z")
    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("1.2.4-BETA5-HOTFIX#133742"),
      fakeBfarmConnector
    )
    testService.clock = Clock.fixed(onCutover, ZoneOffset.UTC)

    testService.checkSiteApiVersion(ListBuffer.empty).map { validSites =>
      validSites must be(empty)
    }
  }

  it must "produce Responsivity.mixedSuccess when version check succeeds but data requests fail" in {
    val preCutover = Instant.parse("2026-04-30T12:00:00Z")

    val failingDataConnector = new dip.DipConnector {
      override def getApiVersion(site: Code[Site])(implicit env: ExecutionContext)
      :Future[Either[String, String]] =
        Future.successful(Right("1.3.0"))
      override def submissionReports(site: Code[Site], useCase: UseCase.Value,
                                     filter: Submission.Report.Filter)
          (implicit ec: ExecutionContext): Future[Either[String, Seq[Submission.Report]]] =
        Future.successful(Left("simulated data request failure"))
      override def confirmSubmitted(report: Submission.Report)(implicit ec: ExecutionContext)
      : Future[Either[String, Submission.Report]] =
        Future.successful(Right(report))
    }

    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      failingDataConnector,
      fakeBfarmConnector
    )
    testService.clock = Clock.fixed(preCutover, ZoneOffset.UTC)

    val capturedResponsivityReports = ListBuffer.empty[testService.ResponsivityReport]
    testService.responsivitySink = { (reports, _) =>
      capturedResponsivityReports ++= reports
    }

    for {
      _ <- testService.conductReportingWorkflow()
    } yield {
      val expectedSites = Config.instance.sites.keys.toSet
      //conductReportingWorkflow would have created more than one report, but
      // they would be coalesced into one each
      capturedResponsivityReports.map(_.site).toSet mustEqual expectedSites
      assert(capturedResponsivityReports
        .forall(_.responsivity == testService.Responsivity.mixedSuccess))
    }
  }

  it must " not process more submissions simultaneously than it has threads (non-deterministic)" in {
    //configure bfarmconnecteor to halt for 100 msec during upload

    log.info("FakeDipConnector sending "+fakeDipConnector.nSubmissions+ " submissions per site")
    fakeDipConnector.confirmationsTakeTime = true
    fakeDipConnector.maxSimultaneousConfirmationWaits.set(0)

    // testing setup has 39 clinic-usecases, so there can be no less than 39 uploads to run
    val expectedNumReports = Config.instance.sites
      .flatMap(it => it._2.useCases).size * fakeDipConnector.nSubmissions
    //have more reports overall than nThreads
    assert(expectedNumReports > service.nSimultaneousSubmissionConfirmations,
      "Setup assertion 1 failed")

    //run
    for{

      _ <- service.pollReports(sites, ListBuffer.empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions(ListBuffer.empty)

    } yield{

      assertResult(service.nSimultaneousSubmissionConfirmations)(
        fakeDipConnector.maxSimultaneousConfirmationWaits.get())

    }
  }
}
