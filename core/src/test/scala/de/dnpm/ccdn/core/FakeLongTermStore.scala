package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.Encryptor.Encrypted
import de.dnpm.ccdn.core.dip.Report
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.{MVHService, UseCase}



final class FakeLongTermStoreProvider extends LongTermStoreProvider
{
  override def getInstance: LongTermStore =
    new FakeLongTermStore
}

// Only implements the methods the tests need, the rest is left as ???
//noinspection NotImplementedCode
class FakeLongTermStore extends LongTermStore
{
  override def writeSiteAvailabilityReports(reports: Iterable[ResponsivityReport]): Unit = ()

  override def backupReport(report: Report, content: => Encrypted): Either[String, Unit] = ???

  override def backupSubmission(report: Report, content: => Encrypted): Either[String, Unit] = ???

  override def backupForQuarterReport(report: Report): Either[String, Unit] = Right(())

  override def backupDeletion(site: Coding[Site], usecase: UseCase.Value,
                              deletionEvent: MVHService.DeletionEvent,
                              content: => Encrypted): Either[String, Unit] = ???
  override def applyDeletion(site: Coding[Site], usecase: UseCase.Value,
                             deletionEvent: MVHService.DeletionEvent,
                             content: => Encrypted): Either[String, Unit] = ???
}