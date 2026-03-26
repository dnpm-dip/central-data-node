package de.dnpm.ccdn.connector

import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.Submission.Report.Status
import de.dnpm.dip.service.mvh.{Submission, UseCase}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.slf4j.{LoggerFactory}
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender

import java.io.{File, IOException}
import java.time.LocalDateTime
import scala.util.chaining.scalaUtilChainingOps

class RetainingFsBackedReportRepositoryTests extends AnyFlatSpec
  with BeforeAndAfter
{
  behavior of "RetainingFsBackedReportRepository"

  //the two folders used by the report repository
  val queueDir:File = new File(".","queue")
  val backupDir:File = new File(".","quarterReportBackup")

  private def makeFakeReport(creationDate:LocalDateTime = LocalDateTime.now,
                             status:Submission.Report.Status.Value = Status.Unsubmitted):Submission.Report = {
    Submission.Report(
      Id("1337"),
      creationDate,
      Id("42"),
      status,
      Coding[Site]("Uniklinik Tü","UKT"),
      UseCase.MTB,
      Submission.Type.Test,
      None,None,
      HealthInsurance.Type.SOZ,
      None,None
    )
  }
  private def makeThreeReports:Seq[Submission.Report] = List(
    makeFakeReport(),
    makeFakeReport(LocalDateTime.now().plusDays(120), Status.Unsubmitted),
    makeFakeReport(status=Status.Submitted))

  /**
   * For teardown
   */
  private def deleteFolder(file:File):Boolean = {
    if(file.isDirectory) {
      file.listFiles().map(it => deleteFolder(it)).forall(b => b)
    }
    file.delete()
  }

  private def makeFixture():RetainingFsBackedReportRepository = {
    new RetainingFsBackedReportRepository(queueDir,backupDir)
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
      makeFakeReport(LocalDateTime.of(2026,1,30,15,0)),
      makeFakeReport(LocalDateTime.of(2026,2,15,7,15)),
      makeFakeReport(LocalDateTime.of(2025,4,5,8,30)),
      makeFakeReport(LocalDateTime.of(2026,7,10,23,45)))
    toTest.saveIfAbsent(someReports)
    assertResult(0)(backupDir.listFiles().length)
    assertResult(4)(queueDir.listFiles().length)
    for (f <- someReports) {
      toTest.remove(f)
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
    val testReport = makeFakeReport()
    prepInstance.saveIfAbsent(testReport)
    prepInstance.remove(testReport)
  }

  it should "not load files in the quarter report directory" in {
    putOneIntoBackup()

    assert(queueDir.listFiles().isEmpty)
    assert(!backupDir.listFiles().isEmpty)
    new File(backupDir,"Q4_2028").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().length)
    }

    val toTest = makeFixture()
    assert(toTest.entries(_ => true).isEmpty)
  }


  it should "warn on file name collision" in {
    putOneIntoBackup()

    val logger = LoggerFactory.getLogger(classOf[RetainingFsBackedReportRepository]).asInstanceOf[Logger]
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)

    val toTest = makeFixture()
    //recreate the submission created during putOneIntoBackup()
    val collidingSubmission = makeFakeReport()
    toTest.saveIfAbsent(collidingSubmission)

    //file is already present. Should not take it out of the queue
    toTest.remove(collidingSubmission)

    val logEvents = appender.list
    assertResult(1)(toTest.entries(_ => true).length)
    assert(! queueDir.listFiles().isEmpty,
      "Since the removal was aborted, the file should still be in the queue directory")

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
