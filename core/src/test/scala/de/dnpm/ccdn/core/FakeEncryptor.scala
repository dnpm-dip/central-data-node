package de.dnpm.ccdn.core


import play.api.libs.json.{Json, JsValue}


/**
 * Doesn't actually encrypt, but stores the payload in plain text as "ciphertext"
 */
class FakeEncryptor extends Encryptor
{
  override def encrypt(payload: JsValue): Encryptor.Encrypted =
    Encryptor.Encrypted("none", "", "", Json.stringify(payload))
}
