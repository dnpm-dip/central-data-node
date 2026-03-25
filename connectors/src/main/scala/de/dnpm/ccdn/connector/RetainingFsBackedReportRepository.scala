package de.dnpm.ccdn.connector

import de.dnpm.ccdn.core.{ReportRepository, ReportRepositoryProvider}
import de.dnpm.dip.service.mvh.Submission
import de.dnpm.dip.util.Logging

import java.io.File
import java.time.LocalDateTime
import scala.util.Properties.{envOrNone, propOrNone}
import scala.util.{Failure, Try}

final class RetainingReportRepositoryProviderImpl extends ReportRepositoryProvider
{
  override def getInstance: ReportRepository =
    RetainingFsBackedReportRepository.instance
}


object RetainingFsBackedReportRepository extends Logging {
  private val QUEUE_PROP = "ccdn.queue.dir"
  private val QUEUE_ENV = "CCDN_QUEUE_DIR"

  private val QUARTERREPORTBACKUP_DIR_PROP = "ccdn.quarterBackup.dir"
  private val QUARTERREPORTBACKUP_DIR_ENV = "CCDN_QUARTERBACKUP_DIR"

  lazy val instance: RetainingFsBackedReportRepository = {
    for {
      queueDir:File <- Try(envOrNone(QUEUE_ENV)
        .orElse(propOrNone(QUEUE_PROP)).get)
        .map(new File(_))
        .filter(it => it.isDirectory)
        .recoverWith {
          case t => log.error(s"Couldn't retrieve directory path for queue")
          Failure(t)
        }
      quarterReportDir:File <- Try(envOrNone(QUARTERREPORTBACKUP_DIR_ENV)
        .orElse(propOrNone(QUARTERREPORTBACKUP_DIR_PROP)).get)
        .map(new File(_))
        .filter(it => it.isDirectory)
        .recoverWith {
          case t => log.error(s"Couldn't retrieve directory path for quarter report backup directory")
            Failure(t)
        }
    } yield new RetainingFsBackedReportRepository(queueDir,quarterReportDir)
  }.get

}


class RetainingFsBackedReportRepository(queueDir:File, val quarterRepoDir:File)
  extends FSBackedReportRepository(queueDir) {

  private object Quarter extends Enumeration {
    val Q1,Q2,Q3,Q4 = Value
    def fromDate(date:LocalDateTime):Quarter.Value = {
      val month:Int = date.getMonthValue //[1,12]
      if(month <= 3) Quarter.Q1
      else if(month <= 6) Quarter.Q2
      else if(month <= 9) Quarter.Q3
      else Quarter.Q4
    }
  }

  override protected def reportDisposer(report: Submission.Report):Try[Boolean] ={
    val toMove = this.queueFile(report)
    val into = getBackupFolder(report.createdAt)
    val nuPlace = new File(into,filenameOf(report))
    if(nuPlace.exists()) {
      Try(false)
    }else{
      Try(toMove.renameTo(nuPlace))
    }
  }

  private def getBackupFolder(creationDate:LocalDateTime): File = {
    val creationYear = creationDate.getYear
    val creationQuarter:Quarter.Value = Quarter.fromDate(creationDate)
    val targetSubFolderName = s"${creationQuarter}_${creationYear}"

    val targetSubFolder = new File(this.quarterRepoDir,targetSubFolderName)
    if(!targetSubFolder.exists()) {
      targetSubFolder.mkdir()
    }
    targetSubFolder
  }
}
