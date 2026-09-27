package de.dnpm.ccdn.connector


import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.time.{Instant, LocalDate, LocalDateTime}
import com.mongodb.{ErrorCategory, MongoWriteException}
import com.mongodb.client.MongoClients
import com.mongodb.client.model.{Filters, IndexOptions, Indexes}
import de.dnpm.ccdn.core.dip.Report
import org.bson.Document
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.{EncryptionService, PersistenceService, PersistenceServiceProvider, ResponsivityReport}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import play.api.libs.json.{JsValue, Json}

import scala.util.Try
import scala.util.Properties.{envOrNone, propOrNone}


final class PersistenceServiceProviderImpl extends PersistenceServiceProvider
{
  override def getInstance: PersistenceService =
    MongodbPersistenceServiceImpl.instance
}

object MongodbPersistenceServiceImpl
{
  private val MONGODBURIJVMPROP = "ccdn.mongodb.uri"
  private val MONGODBURIENVVAR  = "CCDN_MONGODB_URI"

  private val DATABASE          = "ccdn"
  private val BACKUP_COLLECTION = "backup"

  lazy val instance = new MongodbPersistenceServiceImpl(
    envOrNone(MONGODBURIENVVAR).orElse(propOrNone(MONGODBURIJVMPROP))
  )
}

/**
 * @param encryptionService by-name, so that a missing public key only fails
 *                          the backups, not the construction of this service
 */
final class MongodbPersistenceServiceImpl(
  val mongoUri: Option[String],
  encryptionService: => EncryptionService = RsaHybridEncryptionServiceImpl.instance
) extends PersistenceService with Logging
{

  import MongodbPersistenceServiceImpl.{DATABASE, BACKUP_COLLECTION}

  mongoUri match {
    case Some(uri) => log.debug(s"MongoDB URI: $uri")
    case None      => log.warn("MongoDB URI is not configured; site availability reports will not be persisted")
  }

  override def writeSiteAvailabilityReports(
    reports: Iterable[ResponsivityReport],
    now: Instant
  ): Unit =
    if (reports.nonEmpty) mongoUri.foreach { uri =>
      try {
        val client = MongoClients.create(uri)
        try {
          val coll = client.getDatabase(DATABASE).getCollection("siteAvailabilityReports")
          val docs = new java.util.ArrayList[Document]()
          reports.foreach { r =>
            docs.add(
              new Document("site", r.site.value)
                .append("responsivity", r.responsivity.toString)
                .append("apiVersion", r.versionString.getOrElse("???"))
                .append("timestamp", java.util.Date.from(now))
            )
          }
          coll.insertMany(docs)
          log.debug("Successfully persisted responsivity logs")
        } finally {
          client.close()
        }
      } catch {
        case exc: Exception =>
          log.warn(s"Failed to persist responsivity logs. Exception: ${exc.getMessage}")
      }
    }
    else log.warn("Empty set of reports passed to writeSiteAvailabilityReports")

  /**
   * Stores the report, encrypted, in collection [[BACKUP_COLLECTION]].
   */
  override def backupReport(report: Report): Either[String, Unit] =
    storeBackup(
      report,
      "report",
      Json.toJson(report),
      s"Report ${report.id.value} from site ${report.site.code}"
    )

  /**
   * Stores the submission, encrypted, in collection [[BACKUP_COLLECTION]].
   */
  override def backupSubmission(report: Report, submission: JsValue): Either[String, Unit] =
    storeBackup(
      report,
      "submission",
      submission,
      s"Submission ${report.id.value} from site ${report.site.code}"
    )

  /**
   * Stores the deletion event, encrypted, in collection [[BACKUP_COLLECTION]].
   * Its "submittedAt" is the time of deletion.
   */
  override def backupDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] =
    storeBackup(
      deletionEvent.tan.value,
      site,
      usecase,
      deletionEvent.dateTime,
      "deletion",
      Json.toJson(deletionEvent),
      s"DeletionEvent ${deletionEvent.tan.value} from site ${site.code}"
    )

  private def storeBackup(report: Report,documentType: String,content: JsValue,context: String
  ): Either[String, Unit] =
    storeBackup(report.id.value, report.site, report.useCase, report.createdAt, documentType, content, context)

  /**
   * Common scheme of all backup documents: "tan", "site", "usecase", "type", "submittedAt"
   * in plain text, and `content` encrypted.
   * ("tan", "type", "site", "usecase") identifies a backup, whose data never changes, so if
   * such a document already exists, nothing is encrypted or inserted.
   * A unique index over these fields is ensured on every connect (createIndex is a no-op if it
   * already exists) for safety against concurrent inserts; a resulting duplicate key error
   * is a failure like any other database error, and logged as error.
   */
  private def storeBackup(tan: String,site: Coding[Site],usecase: UseCase.Value,
                          submittedAt: LocalDateTime,documentType: String,
                          content: JsValue, context: String
  ): Either[String, Unit] =
    for {
      uri      <- mongoUri.toRight(s"MongoDB URI is not configured; cannot back up $context")
      inserted <- Try {
                    val client = MongoClients.create(uri)
                    try {
                      val coll = client.getDatabase(DATABASE).getCollection(BACKUP_COLLECTION)
                      coll.createIndex(
                        Indexes.ascending("tan", "type", "site", "usecase"),
                        new IndexOptions().unique(true)
                      )
                      val existing =
                        coll.find(
                          Filters.and(
                            Filters.eq("tan", tan),
                            Filters.eq("type", documentType),
                            Filters.eq("site", site.code.value),
                            Filters.eq("usecase", usecase.toString)
                          )
                        )
                        .first()
                      if (existing == null) {
                        val doc =
                          new Document("tan", tan)
                            .append("site", site.code.value)
                            .append("usecase", usecase.toString)
                            .append("type", documentType)
                            .append("content", Document.parse(Json.stringify(Json.toJson(encryptionService.encrypt(content)))))
                            .append("submittedAt", submittedAt.format(ISO_LOCAL_DATE_TIME))
                        coll.insertOne(doc)
                        true
                      } else false
                    } finally {
                      client.close()
                    }
                  }
                  .toEither
                  .left.map {
                    case exc: MongoWriteException if exc.getError.getCategory == ErrorCategory.DUPLICATE_KEY =>
                      s"Failed to back up $context: backup was inserted concurrently (${exc.getMessage})"
                    case exc =>
                      s"Failed to back up $context: ${exc.getMessage}"
                  }
                  .left.map { msg => log.error(msg); msg }
    } yield
      if (inserted) log.debug(s"Backed up $context")
      else log.info(s"Backup of $context already exists; skipped")


  /**
   * Removes all backed up submissions and reports in collection [[BACKUP_COLLECTION]] whose
   * "tan", "site" and "usecase" match, then backs up the deletion event itself via
   * [[backupDeletion]] (which skips the insert if it is already present).
   */
  override def applyDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = {
    val tan     = deletionEvent.tan.value
    val context = s"DeletionEvent $tan from site ${site.code}"
    for {
      uri     <- mongoUri.toRight(s"MongoDB URI is not configured; cannot apply $context")
      deleted <- Try {
        val client = MongoClients.create(uri)
        try {
          client.getDatabase(DATABASE).getCollection(BACKUP_COLLECTION)
            .deleteMany(
              Filters.and(
                Filters.eq("tan", tan),
                Filters.eq("site", site.code.value),
                Filters.eq("usecase", usecase.toString),
                Filters.in("type", "submission", "report")
              )
            )
            .getDeletedCount
        } finally {
          client.close()
        }
      }
        .toEither
        .left.map { exc =>
          val msg = s"Failed to apply $context: ${exc.getMessage}"
          log.error(msg)
          msg
        }
      _        = log.debug(s"Removed $deleted backup document(s) for $context")
      _       <- backupDeletion(site, usecase, deletionEvent)
    } yield ()
  }


  //TODO should eventually replace ArchivingReportRepository
  override def backupForQuarterReport(report:Report):Either[String,Unit] = ???
}