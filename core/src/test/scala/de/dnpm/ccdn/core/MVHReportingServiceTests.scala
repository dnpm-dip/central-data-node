package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.dip.Report
import de.dnpm.ccdn.core.dip.Report.Status
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory

import java.util.concurrent.{ConcurrentLinkedQueue, Executors}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import de.dnpm.dip.coding.{Code, Coding}
import de.dnpm.dip.model.{HealthInsurance, Id, Patient, Site}
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.Submission.Type
import de.dnpm.dip.service.mvh.{Consent, TransferTAN, UseCase}
import play.api.libs.json.{JsValue, Json}



// Fakes only implement the methods the respective test needs, the rest is left as ???
//noinspection NotImplementedCode
final class MVHReportingServiceTests extends AsyncFlatSpec
{
  override implicit def executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))
  private val log = LoggerFactory.getLogger(MVHReportingServiceTests.super.getClass)

  behavior of "MVHReportingService"

  val fakeDipConnector = new FakeDIPConnector
  val fakeBfarmConnector = new FakeBfarmConnector

  val service =
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      fakeDipConnector,
      fakeBfarmConnector,
      new FakePersistenceService
    )

  private val sites = Config.instance.sites.keys.toSeq

  it must "handle multiple uploads from every DIP node in one go" in {
    log.info("FakeDipConnector sending "+fakeDipConnector.nSubmissions+ " submissions per site")
    for {

      responseLog <- Future.successful(new ConcurrentLinkedQueue[ResponsivityReport])
      validSites <- service.getApiCompatibleDipSites(responseLog)
      // Start by draining the report queue, if non-empty (in case the service
      // had been interrupted) and it thus contains reports whose upload hasn't
      // been confirmed to the origin DIP), in order to avoid polling them again
      _ <- if (service.pollingQueue.exists(_.status == Status.Unsubmitted)) service.uploadReports else Future.unit
      oldConfirmations <- if (service.pollingQueue.exists(_.status == Status.SubmittedToBfarm)) service.confirmReports(responseLog) else Future.successful(Seq.empty)
      _ <- service.pollReports(validSites,responseLog)
      _ <- service.uploadReports
      freshConfirmations <- service.confirmReports(responseLog)
      _ <- service.backupSubmissions(freshConfirmations.concat(oldConfirmations).count(_.isRight),validSites,responseLog) //should process as at least as many submissions
      _ = service.backupReports
      _ = service.flushReportQueue()
      _ <- service.syncDeletions(validSites,responseLog)

    } yield service.pollingQueue.entries(_ => true) must be (empty)
  }

  // Helper to create a DipConnector that returns a fixed version string and empty reports
  private def connectorReturningVersion(version: String) = new dip.DipConnector {
    override def getApiVersion(site: Code[Site])(implicit env: ExecutionContext)
    : Future[Either[String, String]] =
      Future.successful(Right(version))
    override def submissionReports(site: Code[Site], useCase: UseCase.Value, filter: Report.Filter)
        (implicit ec: ExecutionContext): Future[Either[String, Seq[Report]]] =
      Future.successful(Right(Seq.empty))
    override def confirmSubmitted(report: Report)(implicit ec: ExecutionContext)
    : Future[Either[String, Report]] =
      Future.successful(Right(report))

    override def deletionEvents(site: Code[Site], useCase: UseCase.Value, since: Option[LocalDateTime])
        (implicit ec: ExecutionContext): Future[Either[String, Seq[DeletionEvent]]] =
      Future.successful(Right(Seq.empty))

    override def downloadSubmission(report: Report)(implicit env: ExecutionContext): Future[Either[String, JsValue]] = ???
  }

  it must "record Responsivity.success for every reachable site in conductPollingCycle and capture the correct timestamp" in {
    // Responsivity reflects connectivity (Right response), not version acceptance.
    // Any version yields success before the cutover date.
    val fixedInstant = Instant.parse("2026-04-30T12:00:00Z")

    val capturedReports = ListBuffer.empty[ResponsivityReport]
    var capturedInstant: Option[Instant] = None
    val capturingReporter = new PersistenceService {
      override def writeSiteAvailabilityReports(reports: Iterable[ResponsivityReport], now: Instant): Unit = {
        capturedReports ++= reports
        capturedInstant = Some(now)
      }

      override def backupReport(report: Report): Either[String, Unit] = ???
      override def backupSubmission(report: Report, submission: JsValue): Either[String, Unit] = ???
      override def backupDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = ???
      override def applyDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = ???
      override def backupForQuarterReport(report: Report): Either[String, Unit] = ???
    }

    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("0.9.0"),
      fakeBfarmConnector,
      capturingReporter
    )
    testService.clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    for {
      _ <- testService.conductReportingWorkflow()
    } yield {
      capturedInstant must be(Some(fixedInstant))
      capturedReports must not be empty
      assert(capturedReports.forall(_.responsivity == Responsivity.success))
    }
  }

  it must "include all reachable sites as valid before the version-check cutover date, regardless of version" in {
    val preCutover = Instant.parse("2026-05-31T23:59:59Z")
    val testService = new MVHReportingService(
      Config.instance, FakeReportRepository(), connectorReturningVersion("0.9.0"),
      fakeBfarmConnector, new FakePersistenceService
    )
    testService.clock = Clock.fixed(preCutover, ZoneOffset.UTC)

    testService.getApiCompatibleDipSites(new ConcurrentLinkedQueue()).map { validSites =>
      assertResult(Config.instance.sites.size)(validSites.size)
    }
  }

  it must "include sites with a 1.3.x version as valid on and after the cutover date" in {
    val onCutover = Instant.parse("2026-06-01T00:00:00Z")
    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("1.3.0-RELEASE_5"),
      fakeBfarmConnector,
      new FakePersistenceService
    )
    testService.clock = Clock.fixed(onCutover, ZoneOffset.UTC)

    testService.getApiCompatibleDipSites(new ConcurrentLinkedQueue()).map { validSites =>
      assertResult(Config.instance.sites.size)(validSites.size)
    }
  }

  it must "exclude sites with a 1.2.x version from valid sites on and after the cutover date" in {
    val onCutover = Instant.parse("2026-06-01T00:00:00Z")
    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      connectorReturningVersion("1.2.4-BETA5-HOTFIX#133742"),
      fakeBfarmConnector,
      new FakePersistenceService
    )
    testService.clock = Clock.fixed(onCutover, ZoneOffset.UTC)

    testService.getApiCompatibleDipSites(new ConcurrentLinkedQueue()).map { validSites =>
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
                                     filter: Report.Filter)
          (implicit ec: ExecutionContext): Future[Either[String, Seq[Report]]] =
        Future.successful(Left("simulated data request failure"))
      override def confirmSubmitted(report: Report)(implicit ec: ExecutionContext)
      : Future[Either[String, Report]] =
        Future.successful(Right(report))

      override def deletionEvents(site: Code[Site], useCase: UseCase.Value, since: Option[LocalDateTime])
          (implicit ec: ExecutionContext): Future[Either[String, Seq[DeletionEvent]]] =
        Future.successful(Right(Seq.empty))

      override def downloadSubmission(report: Report)(implicit env: ExecutionContext): Future[Either[String, JsValue]] = ???
    }

    val capturedResponsivityReports = ListBuffer.empty[ResponsivityReport]
    val capturingReporter = new PersistenceService {
      override def writeSiteAvailabilityReports(reports: Iterable[ResponsivityReport], now: Instant): Unit =
        capturedResponsivityReports ++= reports

      override def backupReport(report: Report): Either[String, Unit] = ???
      override def backupSubmission(report: Report, submission: JsValue): Either[String, Unit] = ???

      override def backupForQuarterReport(report: Report): Either[String, Unit] = ???

      override def backupDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = ???
      override def applyDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = ???
    }

    val testService = new MVHReportingService(
      Config.instance,
      FakeReportRepository(),
      failingDataConnector,
      fakeBfarmConnector,
      capturingReporter
    )
    testService.clock = Clock.fixed(preCutover, ZoneOffset.UTC)

    for {
      _ <- testService.conductReportingWorkflow()
    } yield {
      val expectedSites = Config.instance.sites.keys.toSet
      //conductReportingWorkflow would have created more than one report, but
      // they would be coalesced into one each
      capturedResponsivityReports.map(_.site).toSet mustEqual expectedSites
      assert(capturedResponsivityReports
        .forall(_.responsivity == Responsivity.mixedSuccess))
    }
  }

  it must "coalesce versionString: pick the version if any report for the site has one" in {
    val site = sites.head
    val version = "1.3.0"
    val reports = ListBuffer(
      ResponsivityReport(site, Responsivity.success, Some(version)),
      ResponsivityReport(site, Responsivity.failure, None)
    )
    val coalesced = service.coalesceResponsivityReports(reports)
    Future.successful {
      assert(coalesced.exists(_.versionString.contains(version)))
    }
  }

  it must "coalesce versionString: report None if no report for the site had one" in {
    val site = sites.head
    val reports = ListBuffer(
      ResponsivityReport(site, Responsivity.failure, None),
      ResponsivityReport(site, Responsivity.failure, None)
    )
    val coalesced = service.coalesceResponsivityReports(reports)
    Future.successful {
      assert(coalesced.forall(_.versionString.isEmpty))
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

      _ <- service.pollReports(sites, new ConcurrentLinkedQueue())

      _ <- service.uploadReports

      _ <- service.confirmReports(new ConcurrentLinkedQueue())

    } yield{

      assertResult(service.nSimultaneousSubmissionConfirmations)(
        fakeDipConnector.maxSimultaneousConfirmationWaits.get())
    }
  }

  it must "not download more submissions simultaneously than the download batch size" in {
    val batchSize = 4
    val downloadDurationMsec = 1000L
    val site = sites.head

    // Mocks a DIP node whose download connections each stay open for 1 second
    // and records the maximum number of simultaneously open connections
    val nOpenDownloads = new AtomicInteger(0)
    val maxSimultaneousDownloads = new AtomicInteger(0)
    val nDownloads = new AtomicInteger(0)
    val countingConnector = new dip.DipConnector {
      override def getApiVersion(site: Code[Site])(implicit env: ExecutionContext)
      : Future[Either[String, String]] = ???
      override def submissionReports(site: Code[Site], useCase: UseCase.Value, filter: Report.Filter)
          (implicit ec: ExecutionContext): Future[Either[String, Seq[Report]]] = ???
      override def confirmSubmitted(report: Report)(implicit ec: ExecutionContext)
      : Future[Either[String, Report]] = ???
      override def deletionEvents(site: Code[Site], useCase: UseCase.Value, since: Option[LocalDateTime])
          (implicit ec: ExecutionContext): Future[Either[String, Seq[DeletionEvent]]] = ???

      override def downloadSubmission(report: Report)(implicit env: ExecutionContext)
      : Future[Either[String, JsValue]] =
        Future {
          val nOpen = nOpenDownloads.incrementAndGet()
          maxSimultaneousDownloads.accumulateAndGet(nOpen, Math.max)
          Thread.sleep(downloadDurationMsec)
          nOpenDownloads.decrementAndGet()
          nDownloads.incrementAndGet()
          Right(Json.obj("tan" -> report.id.value))
        }(env)
    }

    def report(tan: String) =
      Report(
        Id[TransferTAN](tan),
        LocalDateTime.of(2026, 7, 1, 12, 0),
        Id[Patient]("42"),
        None,
        Status.ConfirmedToSource,
        Coding[Site](site.value),
        UseCase.MTB,
        Type.Initial,
        None, None, None,
        HealthInsurance.Type.GKV,
        Some(Map(Consent.Category.ModelProject -> true)),
        None, None
      )

    // Enough reports for several batches
    val nReports = 3 * batchSize
    val queue = FakeReportRepository()
    queue.saveIfAbsent((1 to nReports).map(i => report(s"tan-$i")))

    val testService = new MVHReportingService(
      Config.instance,
      queue,
      countingConnector,
      fakeBfarmConnector,
      new FakePersistenceService {
        override def backupSubmission(report: Report, submission: JsValue): Either[String, Unit] =
          Right(())
      }
    ) {
      override private[core] val nSimultaneousSubmissionDownloads: Int = batchSize
    }

    for {
      _ <- testService.backupSubmissions(nReports, Seq(site), new ConcurrentLinkedQueue())
    } yield {
      assertResult(nReports, "Setup assertion failed: not all submissions were downloaded")(nDownloads.get)
      assert(maxSimultaneousDownloads.get <= batchSize)
    }
  }

  it must "only remove reports from the queue that were stored for the quarter report" in {
    def report(tan: String) =
      Report(
        Id[TransferTAN](tan),
        LocalDateTime.of(2026, 7, 1, 12, 0),
        Id[Patient]("42"),
        None,
        Status.ConfirmedToSource,
        Coding[Site](sites.head.value),
        UseCase.MTB,
        Type.Initial,
        None, None, None,
        HealthInsurance.Type.GKV,
        None, None, None
      )
    val stored = report("stored")
    val failing = report("failing")

    val queue = FakeReportRepository()
    queue.saveIfAbsent(Seq(stored, failing))
    val testService = new MVHReportingService(
      Config.instance,
      queue,
      fakeDipConnector,
      fakeBfarmConnector,
      new FakePersistenceService {
        override def backupForQuarterReport(report: Report): Either[String, Unit] =
          if (report.id == failing.id) Left("simulated storage failure") else Right(())
      }
    )

    testService.flushReportQueue()

    Future.successful {
      queue.entries(_ => true).map(_.id) must contain only failing.id
    }
  }
}
