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

  private val QUARTERREPORT_DIR_PROP = "ccdn.quarterBackup.dir"
  private val QUARTERREPORT_DIR_ENV = "CCDN_QUARTERBACKUP_DIR"

  private def findFolder(env:String,prop:String,errorMsg:String):Try[File] = {
    Try(envOrNone(env).filter(_.nonEmpty).orElse(propOrNone(prop).filter(_.nonEmpty)).get)
      .map(new File(_))
      .filter(it => it.isDirectory)
      .recoverWith {
        case t => log.error(errorMsg)
          Failure(t)
      }
  }

  lazy val instance: RetainingFsBackedReportRepository = {
    for {
      queueDir:File <- findFolder(QUEUE_ENV,QUEUE_PROP,
        "Couldn't retrieve directory path for queue")
      quarterReportDir:File <- findFolder(QUARTERREPORT_DIR_ENV,QUARTERREPORT_DIR_PROP,
        "Couldn't retrieve directory path for quarter report backup directory")
    } yield new RetainingFsBackedReportRepository(queueDir,quarterReportDir)
  }.get

}

/**
 * Extends the behavior of the superclass by not deleting completely processed
 * submissions, but moving them into a different folder. This serves the purpose of
 * holding these submissions so that they can be combined into a quarter year
 * report to the BfArM later.
 *
 * The backup folder organizes stored submissions into year quarters,
 * based on the creation date of the submission.
 *
 * Should there be file collisions in the backup folder, an error message logged
 * and the deletion is rejected. Manual intervention would be required.
 * @param queueDir a handle for storing reports that are being processed
 *                 (received from DIP node, to be sent to BfArM and subsequently
 *                 confirmed as submitted back to it's DIP node). This is directly
 *                 passed to the superclass and only used there
 * @param quarterRepoDir a handle to the folder where report files are backed up.
 *                       They are not stored there directly, but organized into
 *                       subfolders, see [[getBackupFolder]]
 */
class RetainingFsBackedReportRepository(queueDir:File, val quarterRepoDir:File)
  extends FSBackedReportRepository(queueDir) {

  /**
   * A representation of a quarter of any year. It can be created from the
   * creationDate of a [[Submission.Report]]
   */
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
    val reportFileName = filenameOf(report)
    val moveTarget = new File(into,reportFileName)

    if(moveTarget.exists()) {
      log.error(s"File ${reportFileName} already exists in backup folder")
      Try(false)
    }else{
      val retu = Try(toMove.renameTo(moveTarget))
      if(retu.get) {
        log.debug(s"Moved ${reportFileName} into backup folder ${into}")
      }else{
        log.error(s"Failed to move ${reportFileName} into backup folder ${into}")
      }
      retu
    }
  }

  /**
   * @return a filehandle to the folder where a report with the given time
   *         should be placed into. The returned folders are labeled like "Q4_2026"
   */
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
