package de.dnpm.ccdn.connector


import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import java.security.KeyPairGenerator
import java.util.Base64
import scala.util.Failure
import org.scalatest.flatspec.AnyFlatSpec
import com.mongodb.MongoTimeoutException
import de.dnpm.ccdn.core.{EncryptionService, ReportRepository, PersistenceService}
import de.dnpm.ccdn.core.bfarm.BfarmConnector
import de.dnpm.ccdn.core.dip.DipConnector


final class SPITests extends AnyFlatSpec
{

  private val queueDir =
    createTempDirectory("dnpm_ccdn_test_").toFile
  private val backupDir =
    createTempDirectory("dnpm_ccdn_test_quarterReportBackup").toFile

  private val publicKeyFile = {
    val keyPair = {
      val gen = KeyPairGenerator.getInstance("RSA")
      gen.initialize(2048)
      gen.generateKeyPair()
    }
    val encoded = Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)
    val pem =
      s"-----BEGIN PUBLIC KEY-----\n${encoded.grouped(64).mkString("\n")}\n-----END PUBLIC KEY-----\n"

    val file = Files.createTempFile("dnpm_ccdn_test_pubkey_", ".pem")
    Files.write(file, pem.getBytes(UTF_8))
    file
  }


  System.setProperty("ccdn.dnpm.broker.baseurl","http://localhost")
  System.setProperty("ccdn.bfarm.api.url","http://localhost/bfarm")
  System.setProperty("ccdn.bfarm.auth.url","http://localhost/bfarm")
  System.setProperty("ccdn.bfarm.api.client.id","dummy")
  System.setProperty("ccdn.bfarm.api.client.secret","dummy")
  System.setProperty("ccdn.queue.dir",queueDir.getAbsolutePath)
  System.setProperty("ccdn.quarterBackup.dir",backupDir.getAbsolutePath)
  System.setProperty("ccdn.backup.encryption.publicKey.path",publicKeyFile.toString)
  // unreachable (nothing listens on port 1), to test the ping at startup without a running MongoDB
  System.setProperty("ccdn.mongodb.uri","mongodb://localhost:1/?serverSelectionTimeoutMS=200")


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

  private val persistenceService =
    PersistenceService.getInstance

  private val encryptionService =
    EncryptionService.getInstance


  "SPI Loaders" must "load implementations" in {
    assert(dipConnector.isSuccess)
    assert(bfarmConnector.isSuccess)
    assert(reportQueue.isSuccess)
    assert(encryptionService.isSuccess)
  }

  it must "fail to load the PersistenceService if MongoDB is unreachable" in {
    // the timeout of the ping shows that the implementation was found and instantiated
    assert(persistenceService.failed.toOption.exists(_.isInstanceOf[MongoTimeoutException]))
  }

}
