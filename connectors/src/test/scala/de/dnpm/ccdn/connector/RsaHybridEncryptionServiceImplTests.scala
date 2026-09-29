package de.dnpm.ccdn.connector


import de.dnpm.ccdn.core.EncryptionService

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.{KeyPairGenerator, PrivateKey}
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.spec.{IvParameterSpec, OAEPParameterSpec, PSource, SecretKeySpec}
import javax.crypto.Cipher
import org.scalatest.flatspec.AnyFlatSpec
import play.api.libs.json.Json


class RsaHybridEncryptionServiceImplTests extends AnyFlatSpec
{
  behavior of "RsaHybridEncryptionServiceImpl"

  private val keyPair = {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    gen.generateKeyPair()
  }

  private val publicKeyPemFile = {
    val encoded = Base64.getEncoder.encodeToString(keyPair.getPublic.getEncoded)
    val pem =
      s"-----BEGIN PUBLIC KEY-----\n${encoded.grouped(64).mkString("\n")}\n-----END PUBLIC KEY-----\n"

    val file = Files.createTempFile("dnpm_ccdn_test_pubkey_", ".pem")
    Files.write(file, pem.getBytes(UTF_8))
    file
  }

  private val toTest =
    new RsaHybridEncryptionServiceImpl(
      RsaHybridEncryptionServiceImpl.loadPublicKey(publicKeyPemFile.toString)
    )

  /**
   * Mirrors the two-step shell decryption documented on [[RsaHybridEncryptionServiceImpl]]:
   * first recover the AES key with the RSA private key, then decrypt the
   * payload with that AES key and the transmitted IV
   */
  private def decrypt(encrypted: EncryptionService.Encrypted, privateKey: PrivateKey): String = {
    // Parameters spelled out as in the documented openssl call (rsa_oaep_md:sha256, rsa_mgf1_md:sha256),
    // not taken from the JCE defaults, which use MGF1 with SHA-1
    val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
    rsaCipher.init(
      Cipher.DECRYPT_MODE,
      privateKey,
      new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
    )
    val aesKeyBytes = rsaCipher.doFinal(Base64.getDecoder.decode(encrypted.encryptedKey))

    val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    aesCipher.init(
      Cipher.DECRYPT_MODE,
      new SecretKeySpec(aesKeyBytes, "AES"),
      new IvParameterSpec(Base64.getDecoder.decode(encrypted.iv))
    )

    new String(
      aesCipher.doFinal(Base64.getDecoder.decode(encrypted.ciphertext)),
      UTF_8
    )
  }

  it must "produce a result that the matching RSA private key can decrypt back to the original payload" in {
    val payload = Json.obj("foo" -> "bar", "n" -> 42, "nested" -> Json.obj("a" -> Json.arr(1, 2, 3)))

    val encrypted = toTest.encrypt(payload)

    val decrypted = decrypt(encrypted, keyPair.getPrivate)

    assertResult(payload)(Json.parse(decrypted))
  }

  it must "encrypt the same payload differently each time (fresh AES key/IV per call)" in {
    val payload = Json.obj("same" -> "payload")

    val first  = toTest.encrypt(payload)
    val second = toTest.encrypt(payload)

    assert(first.encryptedKey != second.encryptedKey)
    assert(first.iv != second.iv)
    assert(first.ciphertext != second.ciphertext)

    val decrypted = decrypt(first,keyPair.getPrivate)
    assertResult(decrypted)(decrypt(second,keyPair.getPrivate))
    assertResult(payload)(Json.parse(decrypted))
  }

  it must "fail to decrypt with the wrong RSA private key" in {
    val payload = Json.obj("secret" -> "value")
    val encrypted = toTest.encrypt(payload)

    val otherKeyPair = {
      val gen = KeyPairGenerator.getInstance("RSA")
      gen.initialize(2048)
      gen.generateKeyPair()
    }

    assertThrows[Exception](decrypt(encrypted, otherKeyPair.getPrivate))
  }

}