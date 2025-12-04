package de.dnpm.ccdn.core


import cats.data.EitherNel
import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import de.dnpm.dip.service.mvh.Submission


trait Repository[T]
{

  def save(t: T): Either[String,Unit]

  def save(ts: Seq[T]): EitherNel[T,Unit]

  def entries(filter: T => Boolean): Seq[T]

  def exists(filter: T => Boolean): Boolean =
    entries(filter).nonEmpty

  def remove(t: T): Either[String,Unit]

}


trait ReportRepository extends Repository[Submission.Report]

trait ReportRepositoryProvider extends SPI[ReportRepository]

object ReportRepository extends SPILoader[ReportRepositoryProvider]

