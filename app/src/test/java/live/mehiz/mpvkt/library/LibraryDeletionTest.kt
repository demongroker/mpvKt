package live.mehiz.mpvkt.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDeletionTest {

  private fun video(id: Long, name: String) = LibraryVideo(
    fileKey = LibraryLogic.fileKey(id, "/storage/emulated/0/Films/$name"),
    mediaId = id,
    uri = "content://media/external/video/media/$id",
    name = name,
    folder = "Films",
    path = "/storage/emulated/0/Films/$name",
    durationMs = 60_000,
    sizeBytes = 1_000,
    dateAddedSec = id,
    positionMs = 30_000,
    lastPlayedAt = 1_800_000_000,
  )

  @Test
  fun aConfirmedDeleteRemovesTheResumeRowAndTheItem() = runBlocking {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv"))
    val cleared = mutableListOf<String>()
    val remaining = LibraryDeletion.applySuccess(videos, videos[0].fileKey) { key -> cleared += key }
    assertEquals(listOf(videos[0].fileKey), cleared)
    assertEquals(listOf("b.mkv"), remaining.map { it.name })
  }

  @Test
  fun aCancelledDeleteLeavesTheFileAndItsResumeRowAlone() = runBlocking {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv"))
    val cleared = mutableListOf<String>()
    val remaining = LibraryDeletion.applyCancel(videos, videos[0].fileKey)
    assertEquals(emptyList<String>(), cleared)
    assertEquals(videos, remaining)
    assertTrue(remaining.any { it.fileKey == videos[0].fileKey })
  }

  @Test
  fun deletingSomethingThatIsNotInTheListChangesNothingElse() = runBlocking {
    val videos = listOf(video(1, "a.mkv"))
    val cleared = mutableListOf<String>()
    val remaining = LibraryDeletion.applySuccess(videos, "missing-key") { key -> cleared += key }
    assertEquals(listOf("missing-key"), cleared)
    assertEquals(listOf("a.mkv"), remaining.map { it.name })
  }

  @Test
  fun theQueueNeverPointsAtADeletedFile() {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv"), video(3, "c.mkv"))
    val queue = QueueLogic.queueFor(videos, "Films", shuffled = false, seed = 0)
    val pruned = LibraryDeletion.pruneQueue(queue, videos[1].fileKey)
    assertEquals(listOf("a.mkv", "c.mkv"), pruned?.items?.map { it.name })
    assertNull(LibraryDeletion.pruneQueue(null, videos[1].fileKey))
  }

  @Test
  fun aDeletedFileCannotBeTheNextOne() {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv"), video(3, "c.mkv"))
    val queue = QueueLogic.queueFor(videos, "Films", shuffled = false, seed = 0)
    val pruned = LibraryDeletion.pruneQueue(queue, videos[1].fileKey)!!
    val next = QueueLogic.next(pruned, videos[0].fileKey, loop = false)
    assertEquals("c.mkv", next?.name)
    assertFalse(next?.fileKey == videos[1].fileKey)
  }
}
