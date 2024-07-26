package de.dnpm.ccdn.core


import java.time.Duration
import java.time.temporal.ChronoUnit.SECONDS
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._


final class Tests extends AsyncFlatSpec
{

  val rate = 3
  val timeUnit = SECONDS

  System.setProperty("dnpm.ccdn.polling.rate",rate.toString)
  System.setProperty("dnpm.ccdn.polling.timeunit",timeUnit.toString)


  val repo  = FakeRepository
  val dnpm  = DNPM.Connector.getInstance.get
  val bfarm = BfArM.Connector.getInstance.get

  val service =
    new MVHReportingService(
      Config.instance,
      repo,
      dnpm, 
      bfarm
    )


  "Dummy" must "..." in {

    for {
      
      pollingResult <- service.pollReports
      
      _ = repo.queue must not be (empty)

      uploadResult <- service.uploadReports
      
    } yield repo.queue must be (empty)

  }



}
