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
    Try(envOrNone(ENV).orElse(propOrNone(PROP)).get)
      .map(new File(_))
      .map(new FSBackedReportRepository(_))
      .recoverWith { 
        case t =>
          log.error(s"Couldn't set up Report Repository, most likely due to undefined environment variable '$ENV' or system property '$PROP'",t)
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

  type Key = (Code[Site],Id[TransferTAN])


  private val filePrefix = "Report"

  dir.mkdirs

  private val cache: Map[Key,Submission.Report] =
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
    key: Key
  ): File =
    new File(dir,s"${filePrefix}_${key._1}_${key._2}.json")
    
    
  private def saveToFile[T: Writes](
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
    

  override def saveIfAbsent(
    key: Key,
    report: Submission.Report
  ): Either[String,Unit] =
    cache.get(key) match {
      case Some(_) => ().asRight
      case None => replace(key,report)
    }


  override def saveIfAbsent(
    reports: Seq[Submission.Report],
    key: Submission.Report => Key
  ): EitherNel[Submission.Report,Unit] =
    NonEmptyList.fromList(
      reports.foldLeft(List.empty[Submission.Report])(
        (failures,report) =>
          saveIfAbsent(key(report),report) match {
            case Right(_) => failures
            case Left(_)  => report :: failures
          }
      )
    )
    .toLeft(())


  override def replace(
    key: Key,
    report: Submission.Report
  ): Either[String,Unit] =
    Try(saveToFile(report,file(key)))
      .map(_ => cache += key -> report)
      .fold(
        _.getMessage.asLeft,
        _ => ().asRight
      )


  override def entries(f: Submission.Report => Boolean): Seq[Submission.Report] =
    cache.values
      .filter(f)
      .toSeq


  override def remove(
    key: Key
  ): Either[String,Unit] =
    Try(file(key).delete)
      .collect {
        case true => cache -= key 
      }
      .fold(
        _.getMessage.asLeft,
        _ => ().asRight
      )

}
