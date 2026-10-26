package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueAdvanceTest {

  private fun video(id: Long, name: String, folder: String = "Films") = LibraryVideo(
    fileKey = LibraryLogic.fileKey(id, "/storage/emulated/0/$folder/$name"),
    mediaId = id,
    uri = "content://media/external/video/media/$id",
    name = name,
    folder = folder,
    path = "/storage/emulated/0/$folder/$name",
    durationMs = 60_000,
    sizeBytes = 1_000,
    dateAddedSec = id,
  )

  private fun queueOf(vararg names: String): PlaybackQueue =
    QueueLogic.queueFor(names.mapIndexed { i, n -> video(i + 1L, n) }, "Films", shuffled = false, seed = 0)

  @Test
  fun endOfFileAdvancesByFileKey() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv")
    val next = QueueAdvance.nextAfterEnd(
      queue = films,
      currentKey = films.items[0].fileKey,
      currentUri = "",
      currentPath = "",
      currentName = "",
      failedKeys = emptySet(),
      loop = false,
    )
    assertEquals("b.mkv", next?.name)
  }

  @Test
  fun aMismatchedKeyStillAdvancesViaUriOrPathOrName() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv")
    // The player resolved the file to a descriptor, so the key looks foreign.
    val byUri = QueueAdvance.nextAfterEnd(films, "foreign-key", films.items[0].uri, "", "", emptySet(), false)
    assertEquals("b.mkv", byUri?.name)
    val byPath = QueueAdvance.nextAfterEnd(films, "foreign-key", "", films.items[1].path, "", emptySet(), false)
    assertEquals("c.mkv", byPath?.name)
    val byName = QueueAdvance.nextAfterEnd(films, "foreign-key", "", "", "b.mkv", emptySet(), false)
    assertEquals("c.mkv", byName?.name)
  }

  @Test
  fun anUnrecognisedCurrentFileStartsFromTheTopOfTheQueue() {
    val films = queueOf("a.mkv", "b.mkv")
    val next = QueueAdvance.nextAfterEnd(films, "nope", "content://unknown", "/unknown", "unknown.mkv", emptySet(), false)
    assertEquals("a.mkv", next?.name)
  }

  @Test
  fun aFailedNextItemIsSkipped() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv")
    val failed = setOf(films.items[1].fileKey)
    val next = QueueAdvance.nextAfterEnd(films, films.items[0].fileKey, "", "", "", failed, loop = false)
    assertEquals("c.mkv", next?.name)
  }

  @Test
  fun skippingStopsAfterTheCap() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv", "d.mkv")
    val failed = films.items.take(QueueAdvance.MAX_SKIPS).map { it.fileKey }.toSet()
    assertNull(QueueAdvance.nextAfterEnd(films, films.items[0].fileKey, "", "", "", failed, loop = false))
  }

  @Test
  fun aFileOpenedWithoutAQueueStopsCleanly() {
    assertNull(QueueAdvance.nextAfterEnd(null, "any-key", "https://example.com/x.mkv", "", "", emptySet(), loop = true))
  }

  @Test
  fun loopingWrapsToTheFirstItemThatHasNotFailed() {
    val films = queueOf("a.mkv", "b.mkv")
    val next = QueueAdvance.nextAfterEnd(films, films.items[1].fileKey, "", "", "", emptySet(), loop = true)
    assertEquals("a.mkv", next?.name)
  }

  @Test
  fun previousExistsOnlyWhenSomethingComesBefore() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv")
    assertNull(
      QueueAdvance.previous(films, films.items[0].fileKey, "", "", "", loop = false),
    )
    assertEquals(
      "a.mkv",
      QueueAdvance.previous(films, films.items[1].fileKey, "", "", "", loop = false)?.name,
    )
    assertFalse(QueueAdvance.hasPrevious(films, films.items[0].fileKey, "", "", "", loop = false))
    assertTrue(QueueAdvance.hasPrevious(films, films.items[2].fileKey, "", "", "", loop = false))
  }

  @Test
  fun previousStepsBackwardsInQueueOrder() {
    val films = queueOf("a.mkv", "b.mkv", "c.mkv")
    assertEquals("b.mkv", QueueAdvance.previous(films, films.items[2].fileKey, "", "", "", loop = false)?.name)
    // Matching stays loose here too.
    assertEquals(
      "b.mkv",
      QueueAdvance.previous(films, "foreign", "", films.items[2].path, "", loop = false)?.name,
    )
  }

  @Test
  fun previousWrapsToTheLastItemOnlyWhenLooping() {
    val films = queueOf("a.mkv", "b.mkv")
    assertNull(QueueAdvance.previous(films, films.items[0].fileKey, "", "", "", loop = false))
    assertEquals("b.mkv", QueueAdvance.previous(films, films.items[0].fileKey, "", "", "", loop = true)?.name)
  }

  @Test
  fun previousWithoutAQueueIsNothing() {
    assertNull(QueueAdvance.previous(null, "a", "u", "p", "n", loop = true))
  }

  @Test
  fun theFolderOfTheCurrentFileCanBeFoundToRebuildAQueue() {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv", folder = "Anime"))
    assertEquals("Films", QueueAdvance.folderOf(videos, videos[0].fileKey, "", "", ""))
    assertEquals("Anime", QueueAdvance.folderOf(videos, "foreign", videos[1].uri, "", ""))
    assertEquals("Films", QueueAdvance.folderOf(videos, "foreign", "content://foreign", "", "a.mkv"))
    assertNull(QueueAdvance.folderOf(videos, "foreign", "content://foreign", "", ""))
  }

  @Test
  fun aQueueCanBeRebuiltFromTheFolderListing() {
    val videos = listOf(video(1, "a.mkv"), video(2, "b.mkv"))
    val rebuilt = QueueAdvance.queueFromFolder(videos, "Films", shuffled = false, seed = 0)
    assertEquals(2, rebuilt?.items?.size)
    assertNull(QueueAdvance.queueFromFolder(emptyList(), "Films", shuffled = false, seed = 0))
  }
}
