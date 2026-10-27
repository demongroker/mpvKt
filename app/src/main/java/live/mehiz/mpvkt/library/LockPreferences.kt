package live.mehiz.mpvkt.library

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

/** What the owner chose about locking: which folders, the PIN hash, and the timeout. */
class LockPreferences(private val preferenceStore: PreferenceStore) {

  /** Folders hidden behind the PIN. Names, because folders have no id. */
  val lockedFolders = preferenceStore.getStringSet("lock_locked_folders")

  /** Salted PBKDF2 hash of the PIN. The PIN itself is never written. */
  val pinHash = preferenceStore.getString("lock_pin_hash", "")

  /** Salt that goes with [pinHash]. Regenerated only when the PIN is changed. */
  val pinSalt = preferenceStore.getString("lock_pin_salt", "")

  /** Minutes before an unlocked folder locks itself again. 0 = only at app restart. */
  val autoLockMinutes = preferenceStore.getInt("lock_auto_lock_minutes", 5)

  fun hasPin(): Boolean = pinHash.get().isNotEmpty()

  /** Stores a new PIN; the caller is responsible for having hashed it. */
  fun setPin(pin: String) {
    val salt = LibraryLock.newSalt()
    pinSalt.set(salt)
    pinHash.set(LibraryLock.hash(pin, salt))
  }

  fun clearPin() {
    pinHash.set("")
    pinSalt.set("")
    lockedFolders.set(emptySet())
  }

  /** The exclusion this feature contributes to the single library filter point. */
  fun exclusions(unlockedFolders: Set<String>): LibraryExclusions =
    LibraryLock.exclusions(lockedFolders.get(), unlockedFolders)
}
