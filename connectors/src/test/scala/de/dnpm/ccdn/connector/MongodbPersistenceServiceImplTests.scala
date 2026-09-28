package de.dnpm.ccdn.connector


import java.time.{Instant, LocalDateTime}
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.UseCase
import de.dnpm.dip.service.mvh.Submission.Type
import org.bson.Document


final class MongodbPersistenceServiceImplTests extends AnyFlatSpec
{

  behavior of "MongodbPersistenceServiceImpl"

  it must "log a warning when called with an empty set of reports" in {
    val impl = new MongodbPersistenceServiceImpl(Some("somewhere"))
    val logger =
      LoggerFactory.getLogger(classOf[MongodbPersistenceServiceImpl])
        .asInstanceOf[Logger]
    val appender = new ListAppender[ILoggingEvent]
    appender.start()
    logger.addAppender(appender)

    try {
      impl.writeSiteAvailabilityReports(Seq.empty, Instant.now())
    } finally {
      val _ = logger.detachAppender(appender) // value discard is compile error
    }

    appender.list.asScala.filter(_.getLevel == Level.WARN) must not be empty
  }

  private def reportCreatedAt(createdAt: LocalDateTime): Report =
    Report(
      Id("123"),
      createdAt,
      Id("42"),
      None,
      Report.Status.ConfirmedToSource,
      Coding[Site]("UKT"),
      UseCase.MTB,
      Type.Initial,
      None, None, None,
      HealthInsurance.Type.GKV,
      None, None, None
    )

  it must "store createdAt as floating UTC date, keeping the wall-clock time" in {
    val createdAt = LocalDateTime.of(2026, 3, 29, 2, 30) // non-existent hour in Germany (DST change)
    val doc = MongodbPersistenceServiceImpl.quarterReportDocument(reportCreatedAt(createdAt))

    doc.get("createdAt") mustBe a [java.util.Date]
    doc.getDate("createdAt").toInstant mustBe Instant.parse("2026-03-29T02:30:00Z")
  }

  it must "derive year and quarter from createdAt at the quarter boundaries" in {
    def yearAndQuarter(createdAt: LocalDateTime): (Int, Int) = {
      val doc = MongodbPersistenceServiceImpl.quarterReportDocument(reportCreatedAt(createdAt))
      (doc.getInteger("year").intValue, doc.getInteger("quarter").intValue)
    }

    yearAndQuarter(LocalDateTime.of(2026, 1, 1, 0, 0))           mustBe (2026, 1)
    yearAndQuarter(LocalDateTime.of(2026, 3, 31, 23, 59, 59))    mustBe (2026, 1)
    yearAndQuarter(LocalDateTime.of(2026, 4, 1, 0, 0))           mustBe (2026, 2)
    yearAndQuarter(LocalDateTime.of(2026, 6, 30, 23, 59))        mustBe (2026, 2)
    yearAndQuarter(LocalDateTime.of(2026, 7, 1, 0, 0))           mustBe (2026, 3)
    yearAndQuarter(LocalDateTime.of(2026, 10, 1, 0, 0))          mustBe (2026, 4)
    yearAndQuarter(LocalDateTime.of(2026, 12, 31, 23, 59, 59))   mustBe (2026, 4)
  }

  it must "contain the fields the quarter report indexes are built on" in {
    val doc = MongodbPersistenceServiceImpl.quarterReportDocument(reportCreatedAt(LocalDateTime.now))

    doc.getString("id") mustBe "123"
    doc.get("site", classOf[Document]).getString("code") mustBe "UKT"
    doc.getString("useCase") mustBe UseCase.MTB.toString
  }

}
