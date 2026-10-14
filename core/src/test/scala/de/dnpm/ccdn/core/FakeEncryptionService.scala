package de.dnpm.ccdn.core


import play.api.libs.json.{Json, JsValue}


/**
 * Doesn't actually encrypt, but stores the payload in plain text as "ciphertext"
 */
class FakeEncryptionService extends EncryptionService
{
  override def encrypt(payload: JsValue): EncryptionService.Encrypted =
    EncryptionService.Encrypted("none", "", "", Json.stringify(payload))
}
