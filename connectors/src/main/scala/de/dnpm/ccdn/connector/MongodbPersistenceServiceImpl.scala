package de.dnpm.ccdn.connector


import java.time.Instant
import com.mongodb.client.MongoClients
import org.bson.Document
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.{
  ResponsivityReport,
  PersistenceService,
  PersistenceServiceProvider
}
import scala.util.Properties.{envOrNone, propOrNone}


final class PersistenceServiceProviderImpl extends PersistenceServiceProvider
{
  override def getInstance: PersistenceService =
    MongodbPersistenceServiceImpl.instance
}

object MongodbPersistenceServiceImpl
{
  lazy val instance = new MongodbPersistenceServiceImpl
}

final class MongodbPersistenceServiceImpl extends PersistenceService with Logging
{

  private val mongoUri: Option[String] =
    envOrNone("CCDN_MONGODB_URI").orElse(propOrNone("ccdn.mongodb.uri"))

  override def writeSiteAvailabilityReports(
    reports: Iterable[ResponsivityReport],
    now: Instant
  ): Unit =
    mongoUri match {
      case Some(uri) =>
        try {
          val client = MongoClients.create(uri)
          try {
            val coll = client.getDatabase("ccdn").getCollection("siteAvailabilityReports")
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
      case None =>
        log.warn("CCDN_MONGODB_URI is not configured; site availability " +
          "reports will not be persisted")
    }

}