package live.mehiz.mpvkt.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted store for API keys and account passwords.
 *
 * The AES key lives in the Android Keystore (hardware-backed where available) and
 * never leaves it; only ciphertext reaches SharedPreferences. Values are never
 * logged, and a missing or wiped key simply reads back as "not set".
 */
object SecureSecrets {
  const val OS_COM_API_KEY = "opensubtitles_com_api_key"
  const val OS_COM_USERNAME = "opensubtitles_com_username"
  const val OS_COM_PASSWORD = "opensubtitles_com_password"

  private const val PREFS = "vela_secure_secrets"
  private const val KEYSTORE = "AndroidKeyStore"
  private const val ALIAS = "vela.secrets.v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val TAG_BITS = 128
  private const val SEPARATOR = ":"

  fun isSet(context: Context, name: String): Boolean = !get(context, name).isNullOrEmpty()

  fun get(context: Context, name: String): String? {
    val stored = prefs(context).getString(name, null) ?: return null
    val parts = stored.split(SEPARATOR)
    if (parts.size != 2) return null
    return runCatching {
      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
      String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()
  }

  fun put(context: Context, name: String, value: String) {
    if (value.isBlank()) {
      clear(context, name)
      return
    }
    val encrypted = runCatching {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey())
      val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
      Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
        Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }.getOrNull() ?: return
    prefs(context).edit { putString(name, encrypted) }
  }

  fun clear(context: Context, name: String) {
    prefs(context).edit { remove(name) }
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build(),
    )
    return generator.generateKey()
  }
}
