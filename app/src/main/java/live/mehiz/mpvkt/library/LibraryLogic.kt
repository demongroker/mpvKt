package live.mehiz.mpvkt.library

import java.security.MessageDigest
import java.util.Locale

/**
 * Pure library decisions — no Android, no database — so grouping, sorting, the
 * exclusion filter and the resume arithmetic can be unit tested.
 */
object LibraryLogic {
  const val OTHER_FOLDER = "Other"

  private const val HASH_HEX = 16
  private const val COMPLETED_FRACTION = 0.95
  private const val MIN_RESUME_MS = 10_000L
  private const val WEEK_SECONDS = 7 * 24 * 60 * 60L

  /** MediaStore id plus a short hash of the path: unique, stable, and readable in logs. */
  fun fileKey(mediaId: Long, path: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
    val hash = digest.joinToString("") { String.format(Locale.US, "%02x", it) }.take(HASH_HEX)
    return "$mediaId-$hash"
  }

  fun folderName(rawBucket: String?, path: String): String {
    val bucket = rawBucket?.trim().orEmpty()
    if (bucket.isNotEmpty()) return bucket
    val trimmed = path.trim().trimEnd('/')
    val lastSeparator = trimmed.lastIndexOf('/')
    return if (lastSeparator > 0) trimmed.substring(lastSeparator + 1) else OTHER_FOLDER
  }

  /** 95 % watched counts as finished; a finished file is not resumed. */
  fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= (durationMs * COMPLETED_FRACTION).toLong()

  fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0 || positionMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

  /** Long enough to matter, not finished, and shorter than the file itself. */
  fun shouldOfferResume(positionMs: Long, durationMs: Long, completed: Boolean): Boolean =
    !completed && durationMs > 0 && positionMs >= MIN_RESUME_MS && positionMs < durationMs

  /** The single exclusion point for the whole library. */
  fun applyExclusions(videos: List<LibraryVideo>, exclusions: LibraryExclusions): List<LibraryVideo> =
    if (exclusions.isEmpty()) {
      videos
    } else {
      videos.filterNot { it.folder in exclusions.folders || it.fileKey in exclusions.fileKeys }
    }

  fun matchesQuery(video: LibraryVideo, query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return video.name.lowercase().contains(needle) || video.folder.lowercase().contains(needle)
  }

  /** Search first, then group, then order: pinned folders stay on top. */
  fun groupByFolder(
    videos: List<LibraryVideo>,
    exclusions: LibraryExclusions = LibraryExclusions(),
    pinnedFolders: Set<String> = emptySet(),
    query: String = "",
    sort: LibrarySort = LibrarySort.DateAdded,
  ): List<LibraryFolder> {
    val visible = applyExclusions(videos, exclusions).filter { matchesQuery(it, query) }
    return visible
      .groupBy { it.folder }
      .map { (name, items) ->
        LibraryFolder(
          name = name,
          videos = items.sortedWith(comparatorFor(sort)),
          pinned = name in pinnedFolders,
        )
      }
      .sortedWith(compareByDescending<LibraryFolder> { it.pinned }.thenBy { it.name.lowercase() })
  }

  fun comparatorFor(sort: LibrarySort): Comparator<LibraryVideo> = when (sort) {
    LibrarySort.Name -> compareBy { it.name.lowercase() }
    LibrarySort.DateAdded -> compareByDescending { it.dateAddedSec }
    LibrarySort.Size -> compareByDescending { it.sizeBytes }
    LibrarySort.LastPlayed -> compareByDescending { it.lastPlayedAt }
  }

  /** "Continue watching" candidates: started, unfinished, played within the week. */
  fun continueWatching(videos: List<LibraryVideo>, nowSec: Long): List<LibraryVideo> =
    videos
      .filter { it.positionMs > 0 && !it.completed && it.lastPlayedAt >= nowSec - WEEK_SECONDS }
      .sortedByDescending { it.lastPlayedAt }

  fun totalWatchedMs(videos: List<LibraryVideo>): Long = videos.sumOf { it.positionMs }
}
