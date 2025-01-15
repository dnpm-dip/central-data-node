package de.dnpm.ccdn.core


import java.time.temporal.ChronoUnit.SECONDS
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import de.dnpm.ccdn.core.dip
import de.dnpm.ccdn.core.bfarm


final class Tests extends AsyncFlatSpec
{

  val rate = 3
  val timeUnit = SECONDS

  System.setProperty("dnpm.ccdn.polling.rate",rate.toString)
  System.setProperty("dnpm.ccdn.polling.timeunit",timeUnit.toString)


  val reportQueue    = FakeReportQueue
  val dipConnector   = dip.Connector.getInstance.get
  val bfarmConnector = bfarm.Connector.getInstance.get

  val service =
    new MVHReportingService(
      Config.instance,
      reportQueue,
      dipConnector, 
      bfarmConnector
    )


  "Dummy" must "..." in {

    for {
      
      _ <- service.pollReports
      
      _ = reportQueue.queue must not be (empty)

      _ <- service.uploadReports
      
    } yield reportQueue.queue must be (empty)

  }


}
