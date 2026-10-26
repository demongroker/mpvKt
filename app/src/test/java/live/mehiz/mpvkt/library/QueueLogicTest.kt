package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

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

  private fun queue(vararg names: String, shuffled: Boolean = false, seed: Long = 0) =
    QueueLogic.queueFor(
      videos = names.mapIndexed { index, name -> video(index + 1L, name) },
      folder = "Films",
      shuffled = shuffled,
      seed = seed,
    )

  @Test
  fun nextFollowsTheFolderOrder() {
    val films = queue("a.mkv", "b.mkv", "c.mkv")
    assertEquals("b.mkv", QueueLogic.next(films, films.items[0].fileKey, loop = false)?.name)
    assertEquals("c.mkv", QueueLogic.next(films, films.items[1].fileKey, loop = false)?.name)
  }

  @Test
  fun theEndOfTheFolderStopsUnlessLooping() {
    val films = queue("a.mkv", "b.mkv")
    assertNull(QueueLogic.next(films, films.items[1].fileKey, loop = false))
    assertEquals("a.mkv", QueueLogic.next(films, films.items[1].fileKey, loop = true)?.name)
    assertFalse(QueueLogic.hasNext(films, films.items[1].fileKey, loop = false))
  }

  @Test
  fun aSingleFileFolderLoopsOntoItselfOrStops() {
    val single = queue("only.mkv")
    assertNull(QueueLogic.next(single, single.items[0].fileKey, loop = false))
    assertEquals("only.mkv", QueueLogic.next(single, single.items[0].fileKey, loop = true)?.name)
  }

  @Test
  fun anUnknownCurrentFileStartsTheQueue() {
    val films = queue("a.mkv", "b.mkv")
    assertEquals("a.mkv", QueueLogic.next(films, "not-in-queue", loop = false)?.name)
  }

  @Test
  fun anEmptyQueueHasNoNext() {
    val empty = QueueLogic.queueFor(emptyList(), "Films", shuffled = false, seed = 0)
    assertNull(QueueLogic.next(empty, "anything", loop = true))
  }

  @Test
  fun theSameSeedAlwaysShufflesTheSameWay() {
    val films = queue("a.mkv", "b.mkv", "c.mkv", "d.mkv", "e.mkv")
    val first = QueueLogic.shuffled(films.items, seed = 42L).map { it.name }
    val second = QueueLogic.shuffled(films.items, seed = 42L).map { it.name }
    assertEquals(first, second)
    assertEquals(films.items.map { it.name }.sorted(), first.sorted())
  }

  @Test
  fun shuffleChangesTheOrderButKeepsEveryFile() {
    val films = queue("a.mkv", "b.mkv", "c.mkv", "d.mkv", "e.mkv", "f.mkv")
    val plain = QueueLogic.ordered(films).map { it.name }
    val shuffled = QueueLogic.ordered(films.copy(shuffled = true, seed = 7L)).map { it.name }
    assertEquals(plain.sorted(), shuffled.sorted())
    assertFalse(plain == shuffled)
  }

  @Test
  fun nextHonoursTheShuffledOrderAndNotTheLibraryOrder() {
    val films = queue("a.mkv", "b.mkv", "c.mkv", "d.mkv", "e.mkv", "f.mkv")
    val shuffledQueue = films.copy(shuffled = true, seed = 99L)
    val order = QueueLogic.ordered(shuffledQueue)
    val successor = QueueLogic.next(shuffledQueue, order[0].fileKey, loop = false)
    assertEquals(order[1].fileKey, successor?.fileKey)
  }

  @Test
  fun shuffleOnlyTouchesTheQueueNotTheLibraryOrder() {
    val films = queue("a.mkv", "b.mkv", "c.mkv")
    val shuffledQueue = films.copy(shuffled = true, seed = 5L)
    // The queue's own item list is untouched: the library sort is a separate thing.
    assertEquals(listOf("a.mkv", "b.mkv", "c.mkv"), shuffledQueue.items.map { it.name })
    assertEquals(listOf("a.mkv", "b.mkv", "c.mkv"), films.items.map { it.name })
  }

  @Test
  fun aQueueSurvivesTheJsonRoundTrip() {
    val films = queue("a.mkv", "b.mkv").copy(shuffled = true, seed = 1234L)
    val restored = PlaybackQueue.fromJson(films.toJson())
    assertEquals(films, restored)
    assertNull(PlaybackQueue.fromJson("not json"))
    assertNull(PlaybackQueue.fromJson(null))
  }
}
