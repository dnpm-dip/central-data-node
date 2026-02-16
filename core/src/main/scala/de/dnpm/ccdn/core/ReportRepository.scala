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


/**
 * Stores entities
 * @tparam T the type of entities that are stored. Must be convertable into some
 *           key type without collisions
 */
trait Repository[T]
{
  type Key

  /**
   * @return a key generated from the given t to find stored items again
   */
  protected def key(t: T): Key

  /**
   * Computes the key from the given parameter t. If an item for this key is found,
   * nothing happens and Right(unit) is returned, else replace is called with t
   */
  def saveIfAbsent(t: T): Either[String,Unit]

  /**
   * Calls saveIfAbsent with every element in ts. If errors occur they are collected,
   * if none occur Right(Unit) is returned
   */
  def saveIfAbsent(ts: Seq[T]): EitherNel[T,Unit]

  /**
   * Actually stores t in this repository. If something goes wrong in this,
   * Left(someError) is returned, else Right(Unit)
   * @param t
   * @return
   */
  def replace(t: T): Either[String,Unit]

  /**
   * @return the sequence of all elements in this repository that pass the given
   * filter parameter
   */
  def entries(filter: T => Boolean): Seq[T]

  /**
   * @return Whether any element exists that passes the given filter
   */
  def exists(filter: T => Boolean): Boolean =
    entries(filter).nonEmpty

  def removeFromQueue(t: T): Either[String,Unit]

}


trait ReportRepository extends Repository[Submission.Report]
{
  type Key = (Code[Site],Id[TransferTAN])

  override def key(report: Submission.Report): Key =
    report.site.code -> report.id
}

trait ReportRepositoryProvider extends SPI[ReportRepository]

object ReportRepository extends SPILoader[ReportRepositoryProvider]
