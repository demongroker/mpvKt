package live.mehiz.mpvkt.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which locked folders are open right now, for this session only. Nothing here is
 * persisted: closing the app locks everything again, which is the point.
 */
class LockSession(private val preferences: LockPreferences) {

  private val unlocked = MutableStateFlow<Set<String>>(emptySet())
  val unlockedFolders: StateFlow<Set<String>> = unlocked.asStateFlow()

  private var lastUnlockAt: Long = 0

  /** Opens one locked folder after the PIN has been verified. */
  fun unlock(folder: String, now: Long = System.currentTimeMillis()) {
    lastUnlockAt = now
    unlocked.update { it + folder }
  }

  /** Opens everything at once (used after a successful PIN entry on the lock screen). */
  fun unlockAll(now: Long = System.currentTimeMillis()) {
    lastUnlockAt = now
    unlocked.value = preferences.lockedFolders.get()
  }

  fun lockAll() {
    lastUnlockAt = 0
    unlocked.value = emptySet()
  }

  fun isUnlocked(folder: String): Boolean = folder in unlocked.value

  /** Locks everything again once the timeout has passed. Returns true if it relocked. */
  fun enforceTimeout(now: Long = System.currentTimeMillis()): Boolean {
    val minutes = preferences.autoLockMinutes.get()
    if (LibraryLock.shouldAutoLock(lastUnlockAt, now, minutes)) {
      lockAll()
      return true
    }
    return false
  }

  /** Exclusions for the library: locked folders minus whatever is currently open. */
  fun exclusions(): LibraryExclusions =
    LibraryLock.exclusions(preferences.lockedFolders.get(), unlocked.value)
}
