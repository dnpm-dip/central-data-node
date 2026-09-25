package de.dnpm.ccdn.connector


import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey, SecureRandom}
import java.util.Base64
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{Cipher, KeyGenerator}
import scala.util.Properties.{envOrNone, propOrNone}
import play.api.libs.json.{JsValue, Json}
import de.dnpm.dip.util.Logging
import de.dnpm.ccdn.core.{EncryptionService, EncryptionServiceProvider}


final class RsaHybridEncryptionServiceProviderImpl extends EncryptionServiceProvider
{
  override def getInstance: EncryptionService =
    RsaHybridEncryptionServiceImpl.instance
}


object RsaHybridEncryptionServiceImpl extends Logging
{

  private val PUBLIC_KEY_PATH_ENV  = "CCDN_ENCRYPTION_PUBLIC_KEY_PATH"
  private val PUBLIC_KEY_PATH_PROP = "ccdn.encryption.publicKey.path"

  /**
   * Reads an RSA public key from a PEM file (X.509 SubjectPublicKeyInfo,
   * i.e. what `openssl pkey -pubout` produces)
   */
  private[connector] def loadPublicKey(path: String): PublicKey = {
    val der =
      Base64.getDecoder.decode(
        new String(Files.readAllBytes(Paths.get(path)), UTF_8)
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s", "")
      )

    KeyFactory.getInstance("RSA")
      .generatePublic(new X509EncodedKeySpec(der))
  }

  lazy val instance: RsaHybridEncryptionServiceImpl = {
    val path =
      envOrNone(PUBLIC_KEY_PATH_ENV)
        .orElse(propOrNone(PUBLIC_KEY_PATH_PROP))
        .getOrElse {
          val msg = s"Couldn't find RSA public key path, configure it via " +
            s"ENV variable $PUBLIC_KEY_PATH_ENV or system property $PUBLIC_KEY_PATH_PROP"
          log.error(msg)
          throw new Exception(msg)
        }

    new RsaHybridEncryptionServiceImpl(loadPublicKey(path))
  }

}


/**
 * Encrypts JSON payloads with a hybrid RSA/AES scheme:
 *
 *  1. A fresh, random AES-256 key is generated for every call to
 *     [[encrypt]]
 *  2. The payload is encrypted with that key using AES-256-CBC and a
 *     random 16-byte IV
 *  3. The AES key itself is encrypted with the configured RSA public key
 *     using OAEP padding (SHA-256 for both the OAEP and the MGF1 hash)
 *
 * Only the RSA public key needs to be present on this system; decryption
 * requires the matching private key, which this service never holds
 * (per the requirement that the key used to decrypt backups be kept
 * separately, itself password-protected).
 *
 * =Key generation (once, offline)=
 * {{{
 *   # private key, AES-256-encrypted with a passphrase (will prompt for one)
 *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -aes256 -out private.pem
 *   # public key, deployed to this application via CCDN_ENCRYPTION_PUBLIC_KEY_PATH
 *   openssl pkey -in private.pem -pubout -out public.pem
 * }}}
 *
 * =Decrypting a result on the linux shell (OpenSSL >= 1.1.1, incl. 3.0.2)=
 *
 * Given the JSON output of [[encrypt]] - `{"algorithm": ..., "encryptedKey": "<b64>",
 * "iv": "<b64>", "ciphertext": "<b64>"}` - and the private key in `private.pem`:
 *
 * {{{
 *   # 1. Recover the AES key (openssl will ask for the private key's passphrase)
 *   echo "<encryptedKey>" | base64 -d | openssl pkeyutl -decrypt \
 *     -inkey private.pem \
 *     -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 \
 *     -out aes_key.bin
 *
 *   # 2. Decrypt the payload with the recovered AES key and IV.
 *   #    `openssl enc` refuses AEAD ciphers, which is why AES-256-CBC (not GCM) is used above.
 *   echo "<ciphertext>" | base64 -d | openssl enc -d -aes-256-cbc \
 *     -K "$(xxd -p -c 256 aes_key.bin)" \
 *     -iv "$(echo "<iv>" | base64 -d | xxd -p -c 32)" \
 *     -out payload.json
 * }}}
 */
final class RsaHybridEncryptionServiceImpl(publicKey: PublicKey) extends EncryptionService
{

  private val AesKeySizeBits = 256
  private val IvSizeBytes    = 16

  private val secureRandom = new SecureRandom

  private def encode(bytes: Array[Byte]): String =
    Base64.getEncoder.encodeToString(bytes)

  override def encrypt(payload: JsValue): EncryptionService.Encrypted = {

    val aesKey = {
      val keyGen = KeyGenerator.getInstance("AES")
      keyGen.init(AesKeySizeBits, secureRandom)
      keyGen.generateKey()
    }

    val iv = new Array[Byte](IvSizeBytes)
    secureRandom.nextBytes(iv)

    val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv))
    val ciphertext = aesCipher.doFinal(Json.stringify(payload).getBytes(UTF_8))

    val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val encryptedKey = rsaCipher.doFinal(aesKey.getEncoded)

    EncryptionService.Encrypted(
      "RSA-OAEP-SHA256+AES-256-CBC",
      encode(encryptedKey),
      encode(iv),
      encode(ciphertext)
    )
  }

}