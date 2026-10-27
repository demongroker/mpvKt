package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingTest {

  private fun video(
    key: String,
    positionMs: Long = 60_000,
    durationMs: Long = 600_000,
    lastPlayedAt: Long = 0,
    completed: Boolean = false,
  ) = LibraryVideo(
    fileKey = key,
    mediaId = key.hashCode().toLong(),
    uri = "content://media/external/video/media/${key.hashCode()}",
    name = "$key.mkv",
    folder = "Films",
    path = "/storage/emulated/0/Films/$key.mkv",
    durationMs = durationMs,
    sizeBytes = 1_000,
    dateAddedSec = 0,
    positionMs = positionMs,
    lastPlayedAt = lastPlayedAt,
    completed = completed,
  )

  @Test
  fun finishedFilesStayOutOfTheStrip() {
    val finished = video("done", positionMs = 600_000, durationMs = 600_000, completed = true)
    val nearly = video("nearly", positionMs = 590_000, durationMs = 600_000)
    assertFalse(ContinueWatching.isResumable(finished))
    assertFalse(ContinueWatching.isResumable(nearly))
    assertTrue(ContinueWatching.pick(listOf(finished, nearly)).isEmpty())
  }

  @Test
  fun barelyStartedFilesStayOutToo() {
    val barely = video("barely", positionMs = 4_000)
    assertFalse(ContinueWatching.isResumable(barely))
  }

  @Test
  fun mostRecentlyPlayedComesFirst() {
    val a = video("a", lastPlayedAt = 100)
    val b = video("b", lastPlayedAt = 300)
    val c = video("c", lastPlayedAt = 200)
    assertEquals(listOf("b", "c", "a"), ContinueWatching.pick(listOf(a, b, c)).map { it.fileKey })
  }

  @Test
  fun theLimitIsHonouredAndZeroMeansNone() {
    val list = (1..20).map { video("v$it", lastPlayedAt = it.toLong()) }
    assertEquals(ContinueWatching.DEFAULT_LIMIT, ContinueWatching.pick(list).size)
    assertEquals(3, ContinueWatching.pick(list, limit = 3).size)
    assertTrue(ContinueWatching.pick(list, limit = 0).isEmpty())
  }

  @Test
  fun oneRowPerFileAndResumePointIsKept() {
    val first = video("same", lastPlayedAt = 10, positionMs = 30_000)
    val dupe = video("same", lastPlayedAt = 50, positionMs = 90_000)
    val picked = ContinueWatching.pick(listOf(first, dupe))
    assertEquals(1, picked.size)
    assertEquals(90_000L, ContinueWatching.resumeAtMs(picked.first()))
  }

  @Test
  fun hasAnythingSeesOnlyResumableFiles() {
    assertFalse(ContinueWatching.hasAnything(listOf(video("idle", positionMs = 0))))
    assertTrue(ContinueWatching.hasAnything(listOf(video("started"))))
  }
}
