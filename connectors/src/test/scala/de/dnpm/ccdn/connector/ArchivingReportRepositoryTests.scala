package de.dnpm.ccdn.connector

import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.Submission.Report.Status
import de.dnpm.dip.service.mvh.{Submission, UseCase}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.FilterReply

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
                             status:Submission.Report.Status.Value = Status.Unsubmitted):Submission.Report = {
    Submission.Report(
      Id(transferTan.toString),
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
    makeFakeReport(transferTan = 12),
    makeFakeReport(transferTan = 3, creationDate = LocalDateTime.now().plusDays(120), status = Status.Unsubmitted),
    makeFakeReport(transferTan = 54, status = Status.Submitted))

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


  it should "warn on file name collision" in {
    val logger = LoggerFactory.getLogger(classOf[ArchivingReportRepository]).asInstanceOf[Logger]
    val logAppender = new ListAppender[ILoggingEvent]()
    logAppender.addFilter((event: ILoggingEvent) =>
      if (event.getLevel == Level.ERROR) {
        FilterReply.ACCEPT
      }
      else {
        FilterReply.DENY
      }
    )
    logAppender.start()
    logger.addAppender(logAppender)

    putOneIntoBackup()


    val toTest = makeFixture()
    //recreate the submission created during putOneIntoBackup()
    val collidingSubmission = makeFakeReport(1, creationDate=dateIn(2028,3))
    toTest.saveIfAbsent(collidingSubmission)

    //file is already present. Should not take it out of the queue
    val removalResult = toTest.removeFromQueue(collidingSubmission)

    assert(removalResult.isLeft)
    assert(! logAppender.list.isEmpty) //given filter, some error must have happened
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
