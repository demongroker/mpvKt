package live.mehiz.mpvkt.library

import kotlinx.coroutines.runBlocking
import live.mehiz.mpvkt.database.dao.ResumeStateDao
import live.mehiz.mpvkt.database.entities.ResumeStateEntity
import live.mehiz.mpvkt.database.repository.ResumeRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO, so the repository contract is testable on the JVM. */
private class FakeResumeStateDao : ResumeStateDao {
  private val rows = linkedMapOf<String, ResumeStateEntity>()

  override suspend fun upsert(state: ResumeStateEntity) {
    rows[state.fileKey] = state
  }

  override suspend fun get(fileKey: String): ResumeStateEntity? = rows[fileKey]

  override suspend fun getAll(): List<ResumeStateEntity> = rows.values.toList()

  override suspend fun recentlyPlayed(since: Long, limit: Int): List<ResumeStateEntity> =
    rows.values.filter { it.lastPlayedAt > since }.sortedByDescending { it.lastPlayedAt }.take(limit)

  override suspend fun delete(fileKey: String) {
    rows.remove(fileKey)
  }

  override suspend fun clearAll() {
    rows.clear()
  }
}

class ResumeStoreTest {

  private fun repository() = ResumeRepositoryImpl(FakeResumeStateDao())

  private fun state(
    key: String = LibraryLogic.fileKey(1, "/storage/emulated/0/Films/a.mkv"),
    positionMs: Long = 42_000,
    durationMs: Long = 100_000,
    lastPlayedAt: Long = 1_800_000_000_000,
    completed: Boolean = false,
  ) = ResumeStateEntity(key, positionMs, durationMs, lastPlayedAt, completed)

  @Test
  fun writtenPositionReadsBackUnchanged() = runBlocking {
    val repository = repository()
    val saved = state()
    repository.save(saved)
    assertEquals(saved, repository.get(saved.fileKey))
  }

  @Test
  fun secondSaveOverwritesTheSameFileKey() = runBlocking {
    val repository = repository()
    val first = state(positionMs = 10_000)
    repository.save(first)
    repository.save(first.copy(positionMs = 55_000, lastPlayedAt = 1_800_000_500_000))
    assertEquals(55_000L, repository.get(first.fileKey)?.positionMs)
    assertEquals(1, repository.getAll().size)
  }

  @Test
  fun differentFilesDoNotOverwriteEachOther() = runBlocking {
    val repository = repository()
    val films = state(key = LibraryLogic.fileKey(1, "/Films/a.mkv"))
    val anime = state(key = LibraryLogic.fileKey(2, "/Anime/a.mkv"))
    repository.save(films)
    repository.save(anime)
    assertEquals(2, repository.getAll().size)
    assertEquals(anime, repository.get(anime.fileKey))
  }

  @Test
  fun unknownFileHasNoPosition() = runBlocking {
    assertNull(repository().get("nothing-here"))
  }

  @Test
  fun recentlyPlayedOnlyReturnsNewerItemsNewestFirst() = runBlocking {
    val repository = repository()
    repository.save(state(key = "old", lastPlayedAt = 1_000))
    repository.save(state(key = "new", lastPlayedAt = 3_000))
    repository.save(state(key = "mid", lastPlayedAt = 2_000))
    val recent = repository.recentlyPlayed(since = 1_500, limit = 2)
    assertEquals(listOf("new", "mid"), recent.map { it.fileKey })
  }

  @Test
  fun completedFlagIsStoredAsGiven() = runBlocking {
    val repository = repository()
    val finished = state(positionMs = 98_000, completed = LibraryLogic.isCompleted(98_000, 100_000))
    repository.save(finished)
    assertTrue(repository.get(finished.fileKey)?.completed == true)
  }

  @Test
  fun clearingRemovesOnlyThatFile() = runBlocking {
    val repository = repository()
    repository.save(state(key = "a"))
    repository.save(state(key = "b"))
    repository.clear("a")
    assertEquals(listOf("b"), repository.getAll().map { it.fileKey })
  }
}
