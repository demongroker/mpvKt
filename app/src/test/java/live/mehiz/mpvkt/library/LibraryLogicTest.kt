package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

  private fun video(
    id: Long,
    name: String,
    folder: String,
    durationMs: Long = 60_000,
    sizeBytes: Long = 1_000,
    dateAddedSec: Long = 100,
    positionMs: Long = 0,
    lastPlayedAt: Long = 0,
    completed: Boolean = false,
    path: String = "/storage/emulated/0/$folder/$name",
  ) = LibraryVideo(
    fileKey = LibraryLogic.fileKey(id, path),
    mediaId = id,
    uri = "content://media/external/video/media/$id",
    name = name,
    folder = folder,
    path = path,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    dateAddedSec = dateAddedSec,
    positionMs = positionMs,
    lastPlayedAt = lastPlayedAt,
    completed = completed,
  )

  @Test
  fun fileKeyIsStableAndDistinguishesSameNameInTwoFolders() {
    val a = LibraryLogic.fileKey(7, "/storage/emulated/0/Anime/ep1.mkv")
    val b = LibraryLogic.fileKey(7, "/storage/emulated/0/Anime/ep1.mkv")
    val c = LibraryLogic.fileKey(7, "/storage/emulated/0/Films/ep1.mkv")
    assertEquals(a, b)
    assertFalse(a == c)
    assertTrue(a.startsWith("7-"))
  }

  @Test
  fun folderNameFallsBackToThePathThenOther() {
    assertEquals("Films", LibraryLogic.folderName("Films", "/x/Films/a.mkv"))
    assertEquals("Films", LibraryLogic.folderName("  ", "/x/Films/a.mkv"))
    assertEquals(LibraryLogic.OTHER_FOLDER, LibraryLogic.folderName(null, ""))
  }

  @Test
  fun ninetyFivePercentCountsAsFinished() {
    assertTrue(LibraryLogic.isCompleted(95_000, 100_000))
    assertFalse(LibraryLogic.isCompleted(50_000, 100_000))
    assertFalse(LibraryLogic.isCompleted(5_000, 0))
  }

  @Test
  fun resumeIsOfferedOnlyForAStartedUnfinishedFile() {
    assertTrue(LibraryLogic.shouldOfferResume(30_000, 100_000, completed = false))
    assertFalse(LibraryLogic.shouldOfferResume(3_000, 100_000, completed = false))
    assertFalse(LibraryLogic.shouldOfferResume(30_000, 100_000, completed = true))
    assertFalse(LibraryLogic.shouldOfferResume(100_000, 100_000, completed = false))
  }

  @Test
  fun progressFractionIsClamped() {
    assertEquals(0.5f, LibraryLogic.progressFraction(50_000, 100_000), 0.001f)
    assertEquals(0f, LibraryLogic.progressFraction(0, 100_000), 0.001f)
    assertEquals(1f, LibraryLogic.progressFraction(200_000, 100_000), 0.001f)
    assertEquals(0f, LibraryLogic.progressFraction(50_000, 0), 0.001f)
  }

  @Test
  fun hiddenFoldersAndExcludedKeysAreFilteredInOnePlace() {
    val videos = listOf(
      video(1, "a.mkv", "Films"),
      video(2, "b.mkv", "Anime"),
      video(3, "c.mkv", "Private"),
    )
    val hidden = LibraryLogic.applyExclusions(videos, LibraryExclusions(folders = setOf("Private")))
    assertEquals(listOf("a.mkv", "b.mkv"), hidden.map { it.name })

    val byKey = LibraryLogic.applyExclusions(videos, LibraryExclusions(fileKeys = setOf(videos[1].fileKey)))
    assertEquals(listOf("a.mkv", "c.mkv"), byKey.map { it.name })

    assertEquals(videos, LibraryLogic.applyExclusions(videos, LibraryExclusions()))
  }

  @Test
  fun foldersAreGroupedCountedAndPinnedFirst() {
    val videos = listOf(
      video(1, "b.mkv", "Films", dateAddedSec = 200),
      video(2, "a.mkv", "Films", dateAddedSec = 300),
      video(3, "x.mkv", "Anime", dateAddedSec = 100),
    )
    val folders = LibraryLogic.groupByFolder(
      videos = videos,
      pinnedFolders = setOf("Anime"),
      sort = LibrarySort.DateAdded,
    )
    assertEquals(listOf("Anime", "Films"), folders.map { it.name })
    assertEquals(true, folders[0].pinned)
    assertEquals(2, folders[1].count)
    // DateAdded sorts newest first inside a folder.
    assertEquals(listOf("a.mkv", "b.mkv"), folders[1].videos.map { it.name })
  }

  @Test
  fun everySortOrdersVideosAsAdvertised() {
    val videos = listOf(
      video(1, "b.mkv", "F", sizeBytes = 10, dateAddedSec = 100, lastPlayedAt = 500),
      video(2, "a.mkv", "F", sizeBytes = 30, dateAddedSec = 300, lastPlayedAt = 100),
      video(3, "c.mkv", "F", sizeBytes = 20, dateAddedSec = 200, lastPlayedAt = 900),
    )
    fun order(sort: LibrarySort) = LibraryLogic.groupByFolder(videos, sort = sort).single().videos.map { it.name }
    assertEquals(listOf("a.mkv", "b.mkv", "c.mkv"), order(LibrarySort.Name))
    assertEquals(listOf("a.mkv", "c.mkv", "b.mkv"), order(LibrarySort.DateAdded))
    assertEquals(listOf("a.mkv", "c.mkv", "b.mkv"), order(LibrarySort.Size))
    assertEquals(listOf("c.mkv", "b.mkv", "a.mkv"), order(LibrarySort.LastPlayed))
  }

  @Test
  fun searchMatchesFileAndFolderNamesAndKeepsGrouping() {
    val videos = listOf(
      video(1, "S01E01.mkv", "Anime"),
      video(2, "Holiday.mp4", "Films"),
    )
    val byName = LibraryLogic.groupByFolder(videos, query = "holiday")
    assertEquals(listOf("Films"), byName.map { it.name })

    val byFolder = LibraryLogic.groupByFolder(videos, query = "anime")
    assertEquals(listOf("Anime"), byFolder.map { it.name })

    assertEquals(2, LibraryLogic.groupByFolder(videos, query = "").sumOf { it.count })
  }

  @Test
  fun continueWatchingKeepsStartedUnfinishedRecentItems() {
    val now = 1_800_000_000L
    val videos = listOf(
      video(1, "fresh.mkv", "F", positionMs = 60_000, lastPlayedAt = now - 60),
      video(2, "done.mkv", "F", positionMs = 99_000, lastPlayedAt = now - 60, completed = true),
      video(3, "old.mkv", "F", positionMs = 30_000, lastPlayedAt = now - 8 * 24 * 3600),
      video(4, "never.mkv", "F"),
    )
    assertEquals(listOf("fresh.mkv"), LibraryLogic.continueWatching(videos, now).map { it.name })
  }
}
