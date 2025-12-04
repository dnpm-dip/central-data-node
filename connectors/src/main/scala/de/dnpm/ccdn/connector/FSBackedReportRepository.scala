package de.dnpm.ccdn.connector


import java.io.{
  File,
  FileInputStream,
  FileWriter
}

import scala.collection.concurrent.{
  Map,
  TrieMap
}
import scala.util.{
  Try,
  Failure,
  Using
}
import scala.util.chaining._
import cats.data.{
  EitherNel,
  NonEmptyList
}
import cats.syntax.either._
import scala.util.Properties.{
  envOrNone,
  propOrNone
}
import play.api.libs.json.{
  Json,
  Writes
}
import de.dnpm.dip.util.Logging
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Id,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN
}
import de.dnpm.ccdn.core.{
  ReportRepository,
  ReportRepositoryProvider
}


final class ReportRepositoryProviderImpl extends ReportRepositoryProvider
{
  override def getInstance: ReportRepository =
    FSBackedReportRepository.instance
}


object FSBackedReportRepository extends Logging
{

  private val PROP = "ccdn.queue.dir"
  private val ENV = "CCDN_QUEUE_DIR"

  lazy val instance =
    Try(
      envOrNone(ENV).orElse(propOrNone(PROP)).get
    )
    .map(new File(_))
    .map(new FSBackedReportRepository(_))
    .recoverWith { 
      case t =>
        log.error(s"Couldn't set up Report Repository, most likely due to undefined property '$ENV'",t)
        Failure(t)
    }
    .get
}   


final class FSBackedReportRepository(
  val dir: File
)
extends ReportRepository
with Logging
{
  private val filePrefix = "Report"

  dir.mkdirs

  private val queue: Map[(Code[Site],Id[TransferTAN]),Submission.Report] =
    TrieMap.from(
      dir.listFiles(
        (_,name) => name.startsWith(filePrefix) && name.endsWith(".json")
      )
      .to(LazyList)
      .map(new FileInputStream(_))
      .map(Json.parse)
      .map(Json.fromJson[Submission.Report](_))
      .map(_.get)
      .map(
        report => (report.site.code,report.id) -> report
      )
    )


  private def file(
    report: Submission.Report
  ): File =
    new File(dir,s"${filePrefix}_${report.site.code}_${report.id}.json")
    
    
  private def save[T: Writes](
    t: T,
    file: File
  ): Unit = {
    Using(new FileWriter(file)){
      w =>
        Json.toJson(t)
          .pipe(Json.prettyPrint)
          .tap(w.write)
    }
    ()
  }
    

  override def save(
    report: Submission.Report
  ): Either[String,Unit] =
    Try(save(report,file(report)))
      .map(_ => queue += (report.site.code,report.id) -> report)
      .fold(
        _.getMessage.asLeft,
        _ => ().asRight
      )


  override def save(
    reports: Seq[Submission.Report]
  ): EitherNel[Submission.Report,Unit] =
    NonEmptyList.fromList(
      reports.foldLeft(List.empty[Submission.Report])(
        (failures,report) =>
          save(report) match {
            case Right(_) => failures
            case Left(_)  => report :: failures
          }
      )
    )
    .toLeft(())


  override def entries(f: Submission.Report => Boolean): Seq[Submission.Report] =
    queue.values
      .filter(f)
      .toSeq


  override def remove(
    report: Submission.Report
  ): Either[String,Unit] =
    Try(file(report).delete)
      .collect {
        case true => queue -= (report.site.code -> report.id) 
      }
      .fold(
        _.getMessage.asLeft,
        _ => ().asRight
      )

}
