package de.dnpm.ccdn.connector


import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.Date
import com.mongodb.{ErrorCategory, MongoCommandException, MongoWriteException}
import com.mongodb.client.{MongoClient, MongoClients, MongoDatabase}
import com.mongodb.client.model.{CreateCollectionOptions, Filters, IndexOptions, Indexes, ValidationOptions}
import com.mongodb.client.model.mql.MqlValues
import de.dnpm.ccdn.core.dip.Report
import org.bson.{BsonType, Document}
import org.bson.types.ObjectId
import org.bson.conversions.Bson
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.{
  ResponsivityReport,
  PersistenceService,
  PersistenceServiceProvider,
  EncryptionService
}
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import play.api.libs.json.{JsValue, Json}
import scala.jdk.CollectionConverters._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
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
  private val LARGE_BACKUP_PARTS_COLLECTION = "largeBackupParts"
  private val QUARTER_REPORT_COLLECTION = "quarter-reports"

  /**
   * Index names of [[QUARTER_REPORT_COLLECTION]]. When changing an index definition,
   * give it a new name (e.g. bump the version), since a changed definition under an existing
   * name makes createIndex fail.
   */
  private val QUARTER_REPORT_IDENTITY_INDEX   = "identity_v1"
  private val QUARTER_REPORT_BY_QUARTER_INDEX = "by_quarter_v1"

  /** MongoDB error code for creating a collection that already exists */
  private val NAMESPACE_EXISTS = 48

  /**
   * Reports carry German wall-clock time without zone information. They are stored as
   * "floating" UTC dates: the local date time is interpreted as if it were UTC. Thus MongoDB
   * date operators ($year, $month, ...), which default to UTC, yield the original wall-clock
   * values. The stored instants must not be compared with real instants (e.g. $$NOW).
   */
  private[connector] def toFloatingUtc(dateTime: LocalDateTime): Date =
    Date.from(dateTime.toInstant(ZoneOffset.UTC))

  private[connector] def quarterOf(dateTime: LocalDateTime): Int =
    (dateTime.getMonthValue - 1) / 3 + 1

  /**
   * Field names of documents in [[QUARTER_REPORT_COLLECTION]], shared by the document,
   * its validator, indexes and queries
   */
  private[connector] object QuarterReportField
  {
    val Id        = "id"
    val SiteCode  = "site.code"
    val UseCase   = "useCase"
    val CreatedAt = "createdAt"
    val Year      = "year"
    val Quarter   = "quarter"
  }

  /**
   * The report in plain JSON, with "createdAt" replaced by a floating UTC date, and
   * "year" and "quarter" derived from it.
   */
  private[connector] def quarterReportDocument(report: Report): Document = {
    val doc = Document.parse(Json.stringify(Json.toJson(report)))
    doc.put(QuarterReportField.CreatedAt, toFloatingUtc(report.createdAt)) // replaces the ISO string
    doc
      .append(QuarterReportField.Year, report.createdAt.getYear)
      .append(QuarterReportField.Quarter, quarterOf(report.createdAt))
  }

  /**
   * Requires the identifying fields, "createdAt" as date, and rejects documents whose
   * "year" and "quarter" do not match their "createdAt". The quarter check
   * 3*quarter-2 <= month <= 3*quarter also restricts "quarter" to 1..4.
   * Date parts are taken in UTC, matching the floating UTC dates, see [[toFloatingUtc]].
   */
  private val quarterReportValidator: Bson = {
    import QuarterReportField.{UseCase => UseCaseField, _}
    val doc     = MqlValues.current()
    val utc     = MqlValues.of("UTC")
    val month   = doc.getDate(CreatedAt).month(utc)
    val quarter = doc.getInteger(Quarter)
    Filters.and(
      Filters.`type`(Id, BsonType.STRING),
      Filters.`type`(SiteCode, BsonType.STRING),
      Filters.`type`(UseCaseField, BsonType.STRING),
      Filters.`type`(CreatedAt, BsonType.DATE_TIME),
      Filters.`type`(Year, BsonType.INT32),
      Filters.`type`(Quarter, BsonType.INT32),
      Filters.expr(
        doc.getInteger(Year).eq(doc.getDate(CreatedAt).year(utc))
          .and(quarter.multiply(3).subtract(2).lte(month))
          .and(quarter.multiply(3).gte(month))
      )
    )
  }

  /**
   * MongoDB rejects documents larger than 16 MiB. A ciphertext longer than this (in bytes,
   * equal to its length since base64 is ASCII) is split, leaving a safety margin for the
   * other fields of the backup document.
   */
  private[connector] val MAX_CIPHERTEXT_LENGTH = 15 * 1024 * 1024

  /**
   * Converts `encrypted` to the "content" of a backup document and, if its ciphertext is
   * longer than `maxCiphertextLength`, the documents to store in [[LARGE_BACKUP_PARTS_COLLECTION]].
   * In that case the ciphertext is cut into consecutive segments of at most `maxCiphertextLength`,
   * each stored as "ciphertext" of a part document (with "_id" and "index"), and "content"
   * contains, instead of "ciphertext", "ciphertextParts": the "_id"s of the parts in order.
   * The concatenation of the parts' "ciphertext" in this order is the original ciphertext.
   */
  private[connector] def splitEncrypted(
    encrypted: EncryptionService.Encrypted,
    maxCiphertextLength: Int = MAX_CIPHERTEXT_LENGTH
  ): (Document, Seq[Document]) =
    if (encrypted.ciphertext.length <= maxCiphertextLength)
      (Document.parse(Json.stringify(Json.toJson(encrypted))), Seq.empty)
    else {
      val parts =
        encrypted.ciphertext
          .grouped(maxCiphertextLength)
          .zipWithIndex
          .map { case (segment, index) =>
            new Document("_id", new ObjectId())
              .append("index", index)
              .append("ciphertext", segment)
          }
          .toSeq
      val content =
        Document.parse(Json.stringify(Json.toJsObject(encrypted) - "ciphertext"))
          .append("ciphertextParts", parts.map(_.getObjectId("_id")).asJava)
      (content, parts)
    }

  lazy val instance = new MongodbPersistenceServiceImpl(
    envOrNone(MONGODBURIENVVAR).orElse(propOrNone(MONGODBURIJVMPROP))
  )
}

/**
 * @param encryptionService by-name, so that a missing public key only fails
 *                          the backups, not the construction of this service
 * @param sharedClient      if defined, used by all operations (and not closed by them), see
 *                          [[withSession]]; otherwise each operation creates and closes its own client
 */
final class MongodbPersistenceServiceImpl private (
  val mongoUri: Option[String],
  encryptionService: => EncryptionService,
  sharedClient: Option[MongoClient]
) extends PersistenceService with Logging
{

  import MongodbPersistenceServiceImpl._

  def this(
    mongoUri: Option[String],
    encryptionService: => EncryptionService = RsaHybridEncryptionServiceImpl.instance
  ) = this(mongoUri, encryptionService, None)

  if (sharedClient.isEmpty) mongoUri match {
    case Some(uri) => log.debug(s"MongoDB URI: $uri")
    case None      => log.warn("MongoDB URI is not configured; site availability reports will not be persisted")
  }

  /**
   * Runs `f` with the shared client if there is one, otherwise with a new one closed afterwards
   */
  private def withClient[T](uri: String)(f: MongoClient => T): T =
    sharedClient match {
      case Some(client) => f(client)
      case None =>
        val client = MongoClients.create(uri)
        try f(client) finally client.close()
    }

  /**
   * Runs `f` with a view of this service whose operations all use one MongoClient (which is
   * thread safe), closed once the returned Future completes. If the client cannot be created,
   * `f` runs with this service, whose operations then each try (and report failures) on their own.
   */
  override def withSession[T](f: PersistenceService => Future[T])(implicit ec: ExecutionContext): Future[T] =
    (sharedClient, mongoUri) match {
      case (None, Some(uri)) =>
        Try(MongoClients.create(uri)) match {
          case Success(client) =>
            Future.delegate(f(new MongodbPersistenceServiceImpl(mongoUri, encryptionService, Some(client))))
              .andThen { case _ => client.close() }
          case Failure(exc) =>
            log.error(s"Failed to create MongoDB client for session: ${exc.getMessage}")
            f(this)
        }
      case _ => f(this)
    }

  override def writeSiteAvailabilityReports(
    reports: Iterable[ResponsivityReport],
    now: Instant
  ): Unit =
    if (reports.nonEmpty) mongoUri.foreach { uri =>
      try {
        withClient(uri) { client =>
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
   * Encrypts `content` and splits it if it is too large for a single document, see [[splitEncrypted]]
   */
  private def encryptContent(content: JsValue): (Document, Seq[Document]) =
    splitEncrypted(encryptionService.encrypt(content))

  /**
   * Common scheme of all backup documents: "tan", "site", "usecase", "type", "submittedAt"
   * in plain text, and `content` encrypted.
   * If the encrypted content is too large, its ciphertext is stored in parts in collection
   * [[LARGE_BACKUP_PARTS_COLLECTION]], see [[splitEncrypted]]. The parts carry the identifying
   * fields of their backup document, too, and are inserted before it; if inserting the backup
   * document fails, they are removed again.
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
                    withClient(uri) { client =>
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
                        val (encrypted, parts) = encryptContent(content)
                        val doc =
                          new Document("tan", tan)
                            .append("site", site.code.value)
                            .append("usecase", usecase.toString)
                            .append("type", documentType)
                            .append("content", encrypted)
                            .append("submittedAt", submittedAt.format(ISO_LOCAL_DATE_TIME))
                        if (parts.isEmpty) coll.insertOne(doc)
                        else {
                          val partsColl = largeBackupPartsCollection(client.getDatabase(DATABASE))
                          parts.foreach { part =>
                            part
                              .append("tan", tan)
                              .append("site", site.code.value)
                              .append("usecase", usecase.toString)
                              .append("type", documentType)
                          }
                          // one by one, since a single insert command is limited to 48 MB
                          parts.foreach(partsColl.insertOne)
                          try coll.insertOne(doc)
                          catch {
                            case exc: Exception =>
                              partsColl.deleteMany(Filters.in("_id", parts.map(_.getObjectId("_id")).asJava))
                              throw exc
                          }
                          log.info(s"Stored ciphertext of $context in ${parts.size} parts")
                        }
                        true
                      } else false
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
      else log.warn(s"Backup of $context already exists; skipped")


  /**
   * Ensures the index over the identifying fields of collection [[LARGE_BACKUP_PARTS_COLLECTION]]
   * (not unique, since a backup may consist of several parts)
   */
  private def largeBackupPartsCollection(db: MongoDatabase) = {
    val coll = db.getCollection(LARGE_BACKUP_PARTS_COLLECTION)
    coll.createIndex(Indexes.ascending("tan", "type", "site", "usecase"))
    coll
  }

  /**
   * Removes all backed up submissions and reports in collection [[BACKUP_COLLECTION]] whose
   * "tan", "site" and "usecase" match, and their parts in [[LARGE_BACKUP_PARTS_COLLECTION]], then backs up the deletion event itself via
   * [[backupDeletion]] (which skips the insert if it is already present).
   */
  override def applyDeletion(site: Coding[Site], usecase: UseCase.Value, deletionEvent: DeletionEvent): Either[String, Unit] = {
    val tan     = deletionEvent.tan.value
    val context = s"DeletionEvent $tan from site ${site.code}"
    for {
      uri     <- mongoUri.toRight(s"MongoDB URI is not configured; cannot apply $context")
      deleted <- Try {
        withClient(uri) { client =>
          val db = client.getDatabase(DATABASE)
          val filter =
            Filters.and(
              Filters.eq("tan", tan),
              Filters.eq("site", site.code.value),
              Filters.eq("usecase", usecase.toString),
              Filters.in("type", "submission", "report")
            )
          val deleted = db.getCollection(BACKUP_COLLECTION).deleteMany(filter).getDeletedCount
          // after the backup documents, so that none is left pointing to removed parts
          db.getCollection(LARGE_BACKUP_PARTS_COLLECTION).deleteMany(filter)
          deleted
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


  /**
   * Stores the report, unencrypted, in collection [[QUARTER_REPORT_COLLECTION]], see
   * [[quarterReportDocument]]. Like in [[storeBackup]], ("id", "site.code", "useCase")
   * identifies a report, and an already existing one is left untouched.
   * An index over ("year", "quarter") serves fetching the reports of a quarter.
   */
  //TODO should eventually replace ArchivingReportRepository
  override def backupForQuarterReport(report: Report): Either[String, Unit] = {
    val context = s"Report ${report.id.value} from site ${report.site.code} for quarter report"
    for {
      uri      <- mongoUri.toRight(s"MongoDB URI is not configured; cannot store $context")
      inserted <- Try {
                    withClient(uri) { client =>
                      val coll = quarterReportCollection(client.getDatabase(DATABASE))
                      val existing =
                        coll.find(
                          Filters.and(
                            Filters.eq(QuarterReportField.Id, report.id.value),
                            Filters.eq(QuarterReportField.SiteCode, report.site.code.value),
                            Filters.eq(QuarterReportField.UseCase, report.useCase.toString)
                          )
                        )
                        .first()
                      if (existing == null) {
                        coll.insertOne(quarterReportDocument(report))
                        true
                      } else false
                    }
                  }
                  .toEither
                  .left.map {
                    case exc: MongoWriteException if exc.getError.getCategory == ErrorCategory.DUPLICATE_KEY =>
                      s"Failed to store $context: report was inserted concurrently (${exc.getMessage})"
                    case exc =>
                      s"Failed to store $context: ${exc.getMessage}"
                  }
                  .left.map { msg => log.error(msg); msg }
    } yield
      if (inserted) log.debug(s"Stored $context")
      else log.warn(s"$context already exists; skipped")
  }

  /**
   * Creates collection [[QUARTER_REPORT_COLLECTION]] with [[quarterReportValidator]] if it
   * does not exist yet, and ensures its indexes (createIndex is a no-op if an index exists).
   */
  private def quarterReportCollection(db: MongoDatabase) = {
    try {
      db.createCollection(
        QUARTER_REPORT_COLLECTION,
        new CreateCollectionOptions().validationOptions(
          new ValidationOptions().validator(quarterReportValidator)
        )
      )
    } catch {
      case exc: MongoCommandException if exc.getErrorCode == NAMESPACE_EXISTS => ()
    }
    import QuarterReportField.{UseCase => UseCaseField, _}
    val coll = db.getCollection(QUARTER_REPORT_COLLECTION)
    coll.createIndex(
      Indexes.ascending(Id, SiteCode, UseCaseField),
      new IndexOptions().name(QUARTER_REPORT_IDENTITY_INDEX).unique(true)
    )
    coll.createIndex(
      Indexes.ascending(Year, Quarter),
      new IndexOptions().name(QUARTER_REPORT_BY_QUARTER_INDEX)
    )
    coll
  }
}