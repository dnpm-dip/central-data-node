package de.dnpm.ccdn.connector


import java.time.{Instant, LocalDateTime}
import java.util.Date
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
import de.dnpm.ccdn.core.Encryptor.Encrypted
import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.{Code, Coding}
import de.dnpm.ccdn.core.{Responsivity, ResponsivityReport}
import play.api.libs.json.Json
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.UseCase
import de.dnpm.dip.service.mvh.Submission.Type
import org.bson.Document


final class MongodbLongTermStoreTests extends AnyFlatSpec
{

  behavior of "MongodbLongTermStore"

  it must "log a warning when called with an empty set of reports" in {
    // While SLF4J is being initialized (possibly concurrently by another suite),
    // it hands out SubstituteLoggers, so wait for the actual logback Logger
    val logger =
      Iterator.continually(LoggerFactory.getLogger(classOf[MongodbLongTermStore]))
        .collectFirst { case l: Logger => l }
        .get
    // created afterwards, so that it doesn't get a SubstituteLogger itself
    val impl = new MongodbLongTermStore("mongodb://somewhere")
    val appender = new ListAppender[ILoggingEvent]
    appender.start()
    logger.addAppender(appender)

    try {
      impl.writeSiteAvailabilityReports(Seq.empty)
    } finally {
      val _ = logger.detachAppender(appender) // value discard is compile error
    }

    appender.list.asScala.filter(_.getLevel == Level.WARN) must not be empty
  }

  it must "serialize a ResponsivityReport with the timestamp as date" in {
    import MongodbLongTermStore.responsivityReportWrites
    val timestamp = Instant.parse("2026-04-30T12:00:00.123Z")
    val doc =
      Document.parse(Json.stringify(Json.toJson(
        ResponsivityReport(Code[Site]("UKT"), Responsivity.success, timestamp)
      )))

    doc.getString("site") mustBe "UKT"
    doc.getString("responsivity") mustBe Responsivity.success.toString
    doc.getString("apiVersion") mustBe "???"
    doc.getDate("timestamp") mustBe Date.from(timestamp)
  }

  it must "reject a malformed MongoDB URI" in {
    an [IllegalArgumentException] must be thrownBy new MongodbLongTermStore("somewhere")
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
    val doc = MongodbLongTermStore.quarterReportDocument(reportCreatedAt(createdAt))

    doc.get("createdAt") mustBe a [java.util.Date]
    doc.getDate("createdAt").toInstant mustBe Instant.parse("2026-03-29T02:30:00Z")
  }

  it must "derive year and quarter from createdAt at the quarter boundaries" in {
    def yearAndQuarter(createdAt: LocalDateTime): (Int, Int) = {
      val doc = MongodbLongTermStore.quarterReportDocument(reportCreatedAt(createdAt))
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
    val doc = MongodbLongTermStore.quarterReportDocument(reportCreatedAt(LocalDateTime.now))

    doc.getString("id") mustBe "123"
    doc.get("site", classOf[Document]).getString("code") mustBe "UKT"
    doc.getString("useCase") mustBe UseCase.MTB.toString
  }

  private def encryptedWithCiphertextLength(length: Int): Encrypted =
    Encrypted("RSA/AES", "key", "iv", "A" * length)

  it must "keep a ciphertext of the maximum length in the backup document" in {
    val max = MongodbLongTermStore.MAX_CIPHERTEXT_LENGTH
    val (content, parts) = MongodbLongTermStore.splitEncrypted(encryptedWithCiphertextLength(max))

    parts mustBe empty
    content.getString("ciphertext").length mustBe max
    content.containsKey("ciphertextParts") mustBe false
  }

  it must "split a ciphertext exceeding the maximum length into parts pointed to by the backup document" in {
    val max       = MongodbLongTermStore.MAX_CIPHERTEXT_LENGTH
    val encrypted = encryptedWithCiphertextLength(max + 1)
    val (content, parts) = MongodbLongTermStore.splitEncrypted(encrypted)

    content.containsKey("ciphertext") mustBe false
    content.getString("algorithm") mustBe encrypted.algorithm
    content.getString("encryptedKey") mustBe encrypted.encryptedKey
    content.getString("iv") mustBe encrypted.iv

    parts.map(_.getString("ciphertext").length) mustBe Seq(max, 1)
    parts.map(_.getInteger("index").intValue) mustBe Seq(0, 1)
    content.getList("ciphertextParts", classOf[org.bson.types.ObjectId]).asScala mustBe parts.map(_.getObjectId("_id"))
    parts.map(_.getString("ciphertext")).mkString mustBe encrypted.ciphertext
  }

}
