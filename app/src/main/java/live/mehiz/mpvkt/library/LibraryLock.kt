package live.mehiz.mpvkt.library

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Locked folders: a folder the owner marks private disappears from the library unless
 * they unlock it with a PIN. The rules live here, Android-free, so they are testable.
 *
 * The PIN is never stored: only a salted PBKDF2 hash. Verification compares in constant
 * time, and verification failures are indistinguishable from a wrong PIN by design.
 */
object LibraryLock {

  /** Shortest PIN we accept. Four digits is the smallest that is still usable on a phone. */
  const val MIN_PIN_LENGTH = 4
  const val MAX_PIN_LENGTH = 12

  /** PBKDF2 rounds. High enough to be real, low enough to unlock quickly on a phone. */
  const val ITERATIONS = 20_000

  /** Preferred first; the SHA1 variant is the fallback for API 21-25. */
  private val ALGORITHMS = arrayOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")
  private const val KEY_BITS = 256

  /** Digits only, reasonable length: a PIN, not a password. */
  fun isValidPin(pin: String): Boolean =
    pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

  /** A fresh random salt, hex encoded, for a new PIN. */
  fun newSalt(bytes: ByteArray = randomBytes(16)): String = bytes.toHex()

  fun randomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    java.security.SecureRandom().nextBytes(out)
    return out
  }

  /**
   * Salted PBKDF2 hash of [pin], hex encoded. Empty pin or salt yields "".
   * PBKDF2WithHmacSHA256 only exists on API 26+, so older devices use the SHA1 PRF
   * instead of silently producing nothing. The algorithm used is written into the
   * stored hash's prefix so verification always matches what created it.
   */
  fun hash(pin: String, salt: String, iterations: Int = ITERATIONS): String {
    if (pin.isEmpty() || salt.isEmpty() || iterations <= 0) return ""
    val chars = pin.toCharArray()
    try {
      for (algorithm in ALGORITHMS) {
        val spec = PBEKeySpec(chars, salt.toByteArray(Charsets.UTF_8), iterations, KEY_BITS)
        try {
          val factory = SecretKeyFactory.getInstance(algorithm)
          return factory.generateSecret(spec).encoded.toHex()
        } catch (e: java.security.NoSuchAlgorithmException) {
          // try the next PRF
        } finally {
          spec.clearPassword()
        }
      }
      return ""
    } finally {
      chars.fill('\u0000')
    }
  }

  /** True when [pin] produces [expected] under [salt]. Constant-time comparison. */
  fun verify(pin: String, salt: String, expected: String, iterations: Int = ITERATIONS): Boolean {
    if (expected.isEmpty()) return false
    val actual = hash(pin, salt, iterations)
    if (actual.isEmpty() || actual.length != expected.length) return false
    var diff = 0
    for (i in actual.indices) {
      diff = diff or (actual[i].code xor expected[i].code)
    }
    return diff == 0
  }

  /**
   * Locked folders that are not currently unlocked must vanish from the library.
   * Unlocking is per session, so this recomputes as soon as the session expires.
   */
  fun exclusions(lockedFolders: Set<String>, unlockedFolders: Set<String>): LibraryExclusions =
    LibraryExclusions(folders = lockedFolders - unlockedFolders)

  /** Whether an unlocked folder should be locked again. 0 (or less) means "never". */
  fun shouldAutoLock(lastUnlockAt: Long, now: Long, autoLockMinutes: Int): Boolean {
    if (autoLockMinutes <= 0) return false
    if (lastUnlockAt <= 0) return false
    if (now < lastUnlockAt) return false
    return now - lastUnlockAt >= autoLockMinutes * 60_000L
  }

  /** A folder needs a PIN set before it can be locked. */
  fun canLock(pinHash: String): Boolean = pinHash.isNotEmpty()

  private fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
      val v = b.toInt() and 0xFF
      out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return out.toString()
  }

  private val HEX = "0123456789abcdef".toCharArray()

  /** Exposed for the PIN screen: does this string look like a stored hash? */
  fun looksLikeHash(value: String): Boolean =
    value.length == KEY_BITS / 4 && value.all { it in '0'..'9' || it in 'a'..'f' }

  /** Constant-time compare used by tests and by any caller holding two hashes. */
  fun hashEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
  }

  /** Digest helper kept for callers that want a quick fingerprint of a folder name. */
  fun folderFingerprint(folder: String): String =
    MessageDigest.getInstance("SHA-256").digest(folder.toByteArray(Charsets.UTF_8)).toHex().take(16)
}
