package de.dnpm.ccdn.core


import de.dnpm.dip.util.{
  SPI,
  SPILoader
}
import play.api.libs.json.{
  Json,
  JsValue,
  OWrites
}


/**
 * Encrypts JSON payloads for long-term storage, e.g. as part of the
 * submission backup workflow (see [[Submission.Report.Status.submissionBackedup]]).
 *
 * Implementations are expected to only ever hold the public half of an
 * asymmetric keypair, so that this service is able to encrypt but never to
 * decrypt; the matching private key required for decryption is kept
 * entirely outside of this application (see the implementation's
 * documentation for how to decrypt a result with it).
 */
trait EncryptionService
{
  def encrypt(payload: JsValue): EncryptionService.Encrypted
}


object EncryptionService extends SPILoader[EncryptionServiceProvider]
{

  /**
   * Result of [[EncryptionService.encrypt]]: a JSON payload encrypted
   * with a hybrid RSA/AES scheme, i.e. a single-use AES key encrypts the
   * payload and is itself encrypted with an RSA public key. All binary
   * fields are base64-encoded.
   */
  final case class Encrypted
  (
    algorithm: String,
    encryptedKey: String,
    iv: String,
    ciphertext: String
  )

  object Encrypted
  {
    implicit val format: OWrites[Encrypted] =
      Json.writes[Encrypted]
  }

}


trait EncryptionServiceProvider extends SPI[EncryptionService]