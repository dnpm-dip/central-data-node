package de.dnpm.ccdn.connector

import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.UseCase
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import de.dnpm.ccdn.core.dip.Report
import de.dnpm.ccdn.core.dip.Report.Status
import de.dnpm.dip.service.mvh.Submission.Type

import java.io.{File, IOException}
import java.time.LocalDateTime
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

class ArchivingReportRepositoryTests extends AnyFlatSpec
  with BeforeAndAfter
{
  behavior of "ArchivingFsBackedReportRepository"

  //the two folders used by the report repository
  val queueDir:File = new File(".","queue")
  val backupDir:File = new File(".","quarterReportBackup")
  private def dateIn(year:Int,month:Int) = LocalDateTime.of(year,month,15,12,0)

  private def makeFakeReport(transferTan:Int = Random.nextInt(),
                             creationDate:LocalDateTime = LocalDateTime.now,
                             status:Report.Status.Value = Status.Unsubmitted):Report = {
    Report(
      Id(transferTan.toString),
      creationDate,
      Id("42"),
      None,
      status,
      Coding[Site]("Uniklinik Tü","UKT"),
      UseCase.MTB,
      Type.Test,
      None,None,None,
      HealthInsurance.Type.SOZ,
      None,None,None
    )
  }
  private def makeThreeReports:Seq[Report] = List(
    makeFakeReport(transferTan = 12),
    makeFakeReport(transferTan = 3, creationDate = LocalDateTime.now().plusDays(120), status = Status.Unsubmitted),
    makeFakeReport(transferTan = 54, status = Status.SubmittedToBfarm))

  /**
   * For teardown
   */
  private def deleteFolder(file:File):Boolean = {
    if(file.isDirectory) {
      file.listFiles().map(it => deleteFolder(it)).forall(b => b)
    }
    file.delete()
  }

  private def makeFixture():ArchivingReportRepository = {
    new ArchivingReportRepository(queueDir,backupDir)
  }

  it should "save new reports into it's queue directory" in {

    assertResult(0)(queueDir.listFiles().length)

    val toTest = makeFixture()
    toTest.saveIfAbsent(makeThreeReports)

    assertResult(3)(queueDir.listFiles().length)
  }

  it should "load preexisting files in the queue directory on startup" in {
    val prepInstance = makeFixture()
    prepInstance.saveIfAbsent(makeThreeReports)

    val toTest = makeFixture()

    assertResult(3)(toTest.entries(_ => true).length)
  }

  it should "move files into the appropriate backup folder on deletion" in {

    assert(backupDir.listFiles().isEmpty)
    assert(queueDir.listFiles().isEmpty)

    val toTest = makeFixture()
    val someReports = List(
      makeFakeReport(creationDate=dateIn(2026,1)),
      makeFakeReport(creationDate=dateIn(2026,2)),
      makeFakeReport(creationDate=dateIn(2025,4)),
      makeFakeReport(creationDate=dateIn(2026,7)))
    toTest.saveIfAbsent(someReports)
    assertResult(0)(backupDir.listFiles().length)
    assertResult(4)(queueDir.listFiles().length)
    for (f <- someReports) {
      toTest.removeFromQueue(f)
    }
    assertResult(0)(queueDir.listFiles().length)
    assertResult(3)(backupDir.listFiles().length)

    new File(backupDir,"Q2_2025").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().length)
    }
    new File(backupDir,"Q1_2026").tap {it =>
      assert(it.exists())
      assertResult(2)(it.listFiles().length)
    }
    new File(backupDir,"Q3_2026").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().length)
    }
  }

  private def putOneIntoBackup() = {
    val prepInstance = makeFixture()
    val testReport = makeFakeReport(transferTan=1, creationDate=dateIn(2028,3))
    prepInstance.saveIfAbsent(testReport)
    prepInstance.removeFromQueue(testReport)
  }

  it should "not load files in the quarter report directory" in {
    putOneIntoBackup()

    assert(queueDir.listFiles().isEmpty)
    assert(!backupDir.listFiles().isEmpty)
    new File(backupDir,"Q1_2028").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().length)
    }

    val toTest = makeFixture()
    assert(toTest.entries(_ => true).isEmpty)
  }


  it should "warn on file name collision and remove the report from the queue" in {
    val logger = LoggerFactory.getLogger(classOf[ArchivingReportRepository]).asInstanceOf[Logger]
    val logAppender = new ListAppender[ILoggingEvent]()
    logAppender.start()
    logger.addAppender(logAppender)

    try {
      putOneIntoBackup()

      val toTest = makeFixture()
      //recreate the submission created during putOneIntoBackup()
      val collidingSubmission = makeFakeReport(1, creationDate=dateIn(2028,3))
      toTest.saveIfAbsent(collidingSubmission)

      //file is already present in the backup. Should keep that and just delete it from the queue
      val removalResult = toTest.removeFromQueue(collidingSubmission)

      assert(removalResult.isRight)
      assert(logAppender.list.stream().anyMatch(_.getLevel == Level.WARN),
        "The collision should have been logged as warning")
      assert(!logAppender.list.stream().anyMatch(_.getLevel == Level.ERROR))
      assert(toTest.entries(_ => true).isEmpty)
      assert(queueDir.listFiles().isEmpty,
        "The file should have been deleted from the queue directory")
      new File(backupDir,"Q1_2028").tap {it =>
        assertResult(1)(it.listFiles().length)
      }
    } finally {
      logger.detachAppender(logAppender): Unit
    }
  }

  before{
    for (dir <- List(queueDir,backupDir)){
      if(dir.exists){
        throw new IOException(s"Test data folder ${dir.getAbsolutePath} should not yet exist, but does")
      }
      else{
        dir.mkdir()
      }
    }
  }

  after{
    for (dir <- List(queueDir,backupDir)){
      if(!deleteFolder(dir)){
        throw new IOException(s"Failed to delete test data folder ${dir.getAbsolutePath}")
      }
    }
  }


}
