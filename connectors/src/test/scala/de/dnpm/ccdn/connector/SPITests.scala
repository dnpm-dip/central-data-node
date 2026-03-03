package de.dnpm.ccdn.connector


import java.nio.file.Files.createTempDirectory
import scala.util.Failure
import org.scalatest.flatspec.AnyFlatSpec
import de.dnpm.ccdn.core.ReportRepository
import de.dnpm.ccdn.core.bfarm.BfarmConnector
import de.dnpm.ccdn.core.dip.DipConnector


final class SPITests extends AnyFlatSpec
{

  private val queueDir =
    createTempDirectory("dnpm_ccdn_test_").toFile

  System.setProperty("ccdn.dnpm.broker.baseurl","http://localhost")
  System.setProperty("ccdn.bfarm.api.url","http://localhost/bfarm")
  System.setProperty("ccdn.bfarm.auth.url","http://localhost/bfarm")
  System.setProperty("ccdn.bfarm.api.client.id","dummy")
  System.setProperty("ccdn.bfarm.api.client.secret","dummy")
  System.setProperty("ccdn.queue.dir",queueDir.getAbsolutePath)


  private val dipConnector =
    DipConnector.getInstance

  private val bfarmConnector =
    BfarmConnector.getInstance

  private val reportQueue =
    ReportRepository.getInstance
      .recoverWith { 
        case t =>
          t.printStackTrace
          Failure(t)
      }


  "SPI Loaders" must "have worked" in {
    assert(dipConnector.isSuccess)
    assert(bfarmConnector.isSuccess)
    assert(reportQueue.isSuccess)
  }

}
