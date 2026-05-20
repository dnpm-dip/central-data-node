package de.dnpm.ccdn.connector


import java.time.Instant
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._


final class MongodbPersistenceServiceImplTests extends AnyFlatSpec
{

  behavior of "MongodbPersistenceServiceImpl"

  it must "log a warning when called with an empty set of reports" in {
    val impl = new MongodbPersistenceServiceImpl
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

}