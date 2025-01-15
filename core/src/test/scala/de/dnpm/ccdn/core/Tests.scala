package de.dnpm.ccdn.core


import java.time.temporal.ChronoUnit.SECONDS
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._


final class Tests extends AsyncFlatSpec
{

  val rate = 3
  val timeUnit = SECONDS

  System.setProperty("dnpm.ccdn.polling.rate",rate.toString)
  System.setProperty("dnpm.ccdn.polling.timeunit",timeUnit.toString)


  val reportQueue  = FakeReportQueue
  val dnpm  = DNPM.Connector.getInstance.get
  val bfarm = BfArM.Connector.getInstance.get

  val service =
    new MVHReportingService(
      Config.instance,
      reportQueue,
      dnpm, 
      bfarm
    )


  "Dummy" must "..." in {

    for {
      
      _ <- service.pollReports
      
      _ = reportQueue.queue must not be (empty)

      _ <- service.uploadReports
      
    } yield reportQueue.queue must be (empty)

  }


}
