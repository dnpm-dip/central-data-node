package de.dnpm.ccdn.connector


import java.nio.file.Files.createTempDirectory
import scala.util.Failure
import org.scalatest.flatspec.AnyFlatSpec
import de.dnpm.ccdn.core.{
  DNPM,
  BfArM,
  ReportQueue
}


final class Tests extends AnyFlatSpec:

  val queueDir =
    createTempDirectory("dnpm_ccdn_test_")
      .toFile

  System.setProperty("dnpm.ccdn.broker.baseurl","http://localhost")
  System.setProperty("dnpm.ccdn.bfarm.baseurl","http://localhost/bfarm")
  System.setProperty("dnpm.ccdn.queue.dir",queueDir.getAbsolutePath)


  val dnpmConnector =
    DNPM.Connector.getInstance

  val bfarmConnector =
    BfArM.Connector.getInstance

  val reportQueue =
    ReportQueue.getInstance
      .recoverWith { 
        case t =>
          t.printStackTrace
          Failure(t)
      }


  "SPI Loaders" must "have worked" in {
    assert(dnpmConnector.isSuccess)
    assert(bfarmConnector.isSuccess)
    assert(reportQueue.isSuccess)
  }

