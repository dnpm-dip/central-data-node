package de.dnpm.ccdn.core


import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._



final class MVHReportingServiceTests extends AsyncFlatSpec
{
  behavior of "MVHReportingService"

  val fakeDipConnector = new FakeDIPConnector
  val fakeBfarmConnector = new FakeBfarmConnector

  val service = 
    new MVHReportingService(
      Config.instance,
      ReportRepository.getInstance.get,
      fakeDipConnector,
      fakeBfarmConnector
    )


  it must "handle multiple uploads from every DIP node in one go" in {

    for {
      
      _ <- service.pollReports
      
      _ = service.pollingQueue.entries(_ => true) must not be (empty)

      _ <- service.uploadReports

      _ <- service.confirmSubmissions

    } yield service.pollingQueue.entries(_ => true) must be (empty)
  }

}
