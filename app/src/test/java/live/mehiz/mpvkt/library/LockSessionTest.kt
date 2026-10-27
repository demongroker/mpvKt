package live.mehiz.mpvkt.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import live.mehiz.mpvkt.preferences.preference.Preference
import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LockSession is Android-free: it only needs LockPreferences, so a fake store is enough. */
class LockSessionTest {

  private class FakeStore : PreferenceStore {
    private val values = mutableMapOf<String, Any?>()

    private fun <T> pref(key: String, defaultValue: T): Preference<T> = object : Preference<T> {
      override fun key(): String = key

      @Suppress("UNCHECKED_CAST")
      override fun get(): T = values[key] as? T ?: defaultValue

      override fun set(value: T) {
        values[key] = value
      }

      override fun isSet(): Boolean = values.containsKey(key)
      override fun delete() {
        values.remove(key)
      }

      override fun defaultValue(): T = defaultValue
      override fun changes(): Flow<T> = flowOf(get())
      override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
    }

    override fun getString(key: String, defaultValue: String): Preference<String> = pref(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long): Preference<Long> = pref(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int): Preference<Int> = pref(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = pref(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = pref(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      pref(key, defaultValue)

    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = values
  }

  private fun preferences(locked: Set<String>, minutes: Int = 5): LockPreferences {
    val prefs = LockPreferences(FakeStore())
    prefs.lockedFolders.set(locked)
    prefs.autoLockMinutes.set(minutes)
    prefs.setPin("1234")
    return prefs
  }

  @Test
  fun nothingIsUnlockedBeforeAPinIsEntered() {
    val session = LockSession(preferences(setOf("Private")))
    assertEquals(setOf("Private"), session.exclusions().folders)
    assertFalse(session.isUnlocked("Private"))
  }

  @Test
  fun unlockingOneFolderLeavesTheOthersLocked() {
    val session = LockSession(preferences(setOf("Private", "Soft")))
    session.unlock("Private")
    assertEquals(setOf("Soft"), session.exclusions().folders)
    assertTrue(session.isUnlocked("Private"))
  }

  @Test
  fun unlockAllOpensEveryLockedFolder() {
    val session = LockSession(preferences(setOf("Private", "Soft")))
    session.unlockAll()
    assertTrue(session.exclusions().isEmpty())
  }

  @Test
  fun theTimeoutRelocksEverything() {
    val session = LockSession(preferences(setOf("Private"), minutes = 5))
    val start = 2_000_000L
    session.unlockAll(now = start)

    assertFalse(session.enforceTimeout(now = start + 60_000))
    assertTrue(session.exclusions().isEmpty())

    assertTrue(session.enforceTimeout(now = start + 6 * 60_000))
    assertEquals(setOf("Private"), session.exclusions().folders)
  }

  @Test
  fun lockAllClosesEverythingImmediately() {
    val session = LockSession(preferences(setOf("Private")))
    session.unlockAll()
    session.lockAll()
    assertEquals(setOf("Private"), session.exclusions().folders)
  }

  @Test
  fun aStoredPinIsAHashedValue() {
    val prefs = preferences(setOf("Private"))
    assertTrue(prefs.hasPin())
    val hash = prefs.pinHash.get()
    assertTrue(LibraryLock.looksLikeHash(hash))
    assertTrue(LibraryLock.verify("1234", prefs.pinSalt.get(), hash))
    assertFalse(LibraryLock.verify("9999", prefs.pinSalt.get(), hash))
  }

  @Test
  fun theExclusionSourceReportsTheSessionState() {
    val session = LockSession(preferences(setOf("Private")))
    val source = LockedFolderExclusionSource(session)
    assertTrue(runBlocking { source.exclusions() }.folders.contains("Private"))
    session.unlock("Private")
    assertTrue(runBlocking { source.exclusions() }.folders.isEmpty())
  }
}
