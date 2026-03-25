package de.dnpm.ccdn.connector

import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{HealthInsurance, Id, Site}
import de.dnpm.dip.service.mvh.Submission.Report.Status
import de.dnpm.dip.service.mvh.{Submission, UseCase}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

import java.io.{File, IOException}
import java.time.LocalDateTime
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

class RetainingFsBackedReportRepositoryTests extends AnyFlatSpec
  with BeforeAndAfter
{
  behavior of "RetainingFsBackedReportRepository"

  val queueDir:File = new File(".","queue")
  val backupDir:File = new File(".","quarterReportBackup")

  private def makeFakeReport(creationDate:LocalDateTime = LocalDateTime.now,
                             status:Submission.Report.Status.Value = Status.Unsubmitted):Submission.Report = {
    Submission.Report(
      Id(Random.nextInt().toString),
      creationDate,
      Id(Random.nextInt().toString),
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

  private def makeFixture():RetainingFsBackedReportRepository = {
    new RetainingFsBackedReportRepository(queueDir,backupDir)
  }

  it should "save new reports into it's queue directory" in {

    assertResult(0)(queueDir.listFiles().size)

    val toTest = makeFixture()
    toTest.saveIfAbsent(makeThreeReports)

    assertResult(3)(queueDir.listFiles().size)
  }

  it should "load preexisting files in the queue directory into it's cache" in {
    val saverInstance = makeFixture()
    saverInstance.saveIfAbsent(makeThreeReports)

    val toTest = makeFixture()

    assertResult(3)(toTest.entries(_ => true).size)
  }

  it should "move files into the appropriate backup folder on deletion" in {

    assertResult(0)(backupDir.listFiles().size)
    assertResult(0)(queueDir.listFiles().size)

    val toTest = makeFixture()
    val someReports = List(
      makeFakeReport(LocalDateTime.of(2026,1,30,15,0)),
      makeFakeReport(LocalDateTime.of(2026,2,15,7,15)),
      makeFakeReport(LocalDateTime.of(2025,4,5,8,30)),
      makeFakeReport(LocalDateTime.of(2026,7,10,23,45)))
    toTest.saveIfAbsent(someReports)
    assertResult(0)(backupDir.listFiles().size)
    assertResult(4)(queueDir.listFiles().size)
    for (f <- someReports) {
      toTest.remove(f)
    }
    assertResult(0)(queueDir.listFiles().size)
    assertResult(3)(backupDir.listFiles().size)

    new File(backupDir,"Q2_2025").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().size)
    }
    new File(backupDir,"Q1_2026").tap {it =>
      assert(it.exists())
      assertResult(2)(it.listFiles().size)
    }
    new File(backupDir,"Q3_2026").tap {it =>
      assert(it.exists())
      assertResult(1)(it.listFiles().size)
    }
  }

  it should "not load files in the quarter report directory" in {
    fail()
  }


  it should "fail on file name collision" in {
    fail()
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

  private def deleteFolder(file:File):Boolean = {
    if(file.isDirectory) {
      file.listFiles().map(it => deleteFolder(it)).forall(b => b)
    }
    file.delete()
  }

  after{
    for (dir <- List(queueDir,backupDir)){
      if(!deleteFolder(dir)){
        throw new IOException(s"Failed to delete test data folder ${dir.getAbsolutePath}")
      }
    }
  }

}
