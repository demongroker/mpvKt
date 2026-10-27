package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLockTest {

  private val salt = LibraryLock.newSalt()

  @Test
  fun aPinMustBeDigitsOfSaneLength() {
    assertTrue(LibraryLock.isValidPin("1234"))
    assertTrue(LibraryLock.isValidPin("1234567890"))
    assertFalse(LibraryLock.isValidPin("123"))
    assertFalse(LibraryLock.isValidPin("1234567890123"))
    assertFalse(LibraryLock.isValidPin("12a4"))
    assertFalse(LibraryLock.isValidPin(""))
  }

  @Test
  fun theSamePinAndSaltAlwaysHashTheSame() {
    val a = LibraryLock.hash("4729", salt)
    val b = LibraryLock.hash("4729", salt)
    assertEquals(a, b)
    assertEquals(64, a.length)
    assertTrue(LibraryLock.looksLikeHash(a))
  }

  @Test
  fun aDifferentSaltGivesADifferentHash() {
    val other = LibraryLock.newSalt()
    assertNotEquals(other, salt)
    assertNotEquals(LibraryLock.hash("4729", salt), LibraryLock.hash("4729", other))
  }

  @Test
  fun theHashIsNotThePin() {
    val hash = LibraryLock.hash("4729", salt)
    assertFalse(hash.contains("4729"))
  }

  @Test
  fun verificationAcceptsTheRightPinAndRejectsTheRest() {
    val hash = LibraryLock.hash("4729", salt)
    assertTrue(LibraryLock.verify("4729", salt, hash))
    assertFalse(LibraryLock.verify("4728", salt, hash))
    assertFalse(LibraryLock.verify("4729", LibraryLock.newSalt(), hash))
    assertFalse(LibraryLock.verify("4729", salt, ""))
    assertFalse(LibraryLock.verify("", salt, hash))
  }

  @Test
  fun emptyInputsNeverProduceAHash() {
    assertEquals("", LibraryLock.hash("", salt))
    assertEquals("", LibraryLock.hash("4729", ""))
    assertEquals("", LibraryLock.hash("4729", salt, iterations = 0))
  }

  @Test
  fun lockedFoldersDisappearUntilTheyAreUnlocked() {
    val locked = setOf("Private", "Soft")
    val exclusions = LibraryLock.exclusions(locked, unlockedFolders = emptySet())
    assertEquals(locked, exclusions.folders)

    val opened = LibraryLock.exclusions(locked, unlockedFolders = setOf("Private"))
    assertEquals(setOf("Soft"), opened.folders)

    val allOpen = LibraryLock.exclusions(locked, unlockedFolders = locked)
    assertTrue(allOpen.isEmpty())
  }

  @Test
  fun theAutoLockTimeoutIsRespected() {
    val start = 1_000_000L
    // five minutes
    assertFalse(LibraryLock.shouldAutoLock(start, start + 4 * 60_000, 5))
    assertTrue(LibraryLock.shouldAutoLock(start, start + 5 * 60_000, 5))
    assertTrue(LibraryLock.shouldAutoLock(start, start + 60 * 60_000, 5))
  }

  @Test
  fun aTimeoutOfZeroNeverLocks() {
    val start = 1_000_000L
    assertFalse(LibraryLock.shouldAutoLock(start, start + 100 * 60 * 60_000L, 0))
    assertFalse(LibraryLock.shouldAutoLock(0, start, 5))
  }

  @Test
  fun aFolderCannotBeLockedBeforeAPinExists() {
    assertFalse(LibraryLock.canLock(""))
    assertTrue(LibraryLock.canLock(LibraryLock.hash("1234", salt)))
  }

  @Test
  fun theFolderFingerprintIsStableAndShort() {
    assertEquals(16, LibraryLock.folderFingerprint("Private").length)
    assertEquals(LibraryLock.folderFingerprint("Private"), LibraryLock.folderFingerprint("Private"))
    assertNotEquals(LibraryLock.folderFingerprint("Private"), LibraryLock.folderFingerprint("private"))
  }

  @Test
  fun hashComparisonIsContentNotIdentity() {
    val a = LibraryLock.hash("1234", salt)
    val b = LibraryLock.hash("1234", salt)
    assertTrue(LibraryLock.hashEquals(a, b))
    assertFalse(LibraryLock.hashEquals(a, a.dropLast(1) + "0"))
    assertFalse(LibraryLock.hashEquals(a, "short"))
  }
}
