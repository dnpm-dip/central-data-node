package de.dnpm.ccdn.core


import cats.data.EitherNel
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.{
  Id,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN
}


trait Repository[T]
{
  type Key

  protected def key(t: T): Key

  def saveIfAbsent(t: T): Either[String,Unit]

  def saveIfAbsent(ts: Seq[T]): EitherNel[T,Unit]

  def replace(t: T): Either[String,Unit]

  def entries(filter: T => Boolean): Seq[T]

  def exists(filter: T => Boolean): Boolean =
    entries(filter).nonEmpty

  def remove(t: T): Either[String,Unit]

}


trait ReportRepository extends Repository[Submission.Report]
{
  type Key = (Code[Site],Id[TransferTAN])

  override def key(report: Submission.Report): Key =
    report.site.code -> report.id
}

trait ReportRepositoryProvider extends SPI[ReportRepository]

object ReportRepository extends SPILoader[ReportRepositoryProvider]

/*
trait Repository[K,T]
{

  def saveIfAbsent(key: K, t: T): Either[String,Unit]

  def saveIfAbsent(ts: Seq[T], key: T => K): EitherNel[T,Unit]

  def replace(key: K, t: T): Either[String,Unit]

  def entries(filter: T => Boolean): Seq[T]

  def exists(filter: T => Boolean): Boolean =
    entries(filter).nonEmpty

  def remove(key: K): Either[String,Unit]

}


trait ReportRepository extends Repository[(Code[Site],Id[TransferTAN]),Submission.Report]

trait ReportRepositoryProvider extends SPI[ReportRepository]

object ReportRepository extends SPILoader[ReportRepositoryProvider]
*/
