package live.mehiz.mpvkt.library

/**
 * The "Continue watching" strip at the top of the library: files that were started and
 * not finished, newest first. Pure logic so the ordering rules are testable without UI.
 */
object ContinueWatching {

  /** How many items the strip shows; the rest stay in their folders. */
  const val DEFAULT_LIMIT = 12

  /**
   * A video belongs in the strip when it can be resumed and is not finished.
   * shouldOfferResume alone is not enough: it lets a 98 %-watched file through, and
   * those belong in "finished", not "continue".
   */
  fun isResumable(video: LibraryVideo): Boolean =
    LibraryLogic.shouldOfferResume(
      positionMs = video.positionMs,
      durationMs = video.durationMs,
      completed = video.completed,
    ) && !LibraryLogic.isCompleted(video.positionMs, video.durationMs)

  /**
   * Resumable videos, most recently played first, one row per file, capped at [limit].
   * A zero [limit] returns nothing rather than everything.
   */
  fun pick(videos: List<LibraryVideo>, limit: Int = DEFAULT_LIMIT): List<LibraryVideo> {
    if (limit <= 0) return emptyList()
    return videos
      .asSequence()
      .filter(::isResumable)
      .sortedWith(compareByDescending<LibraryVideo> { it.lastPlayedAt }.thenByDescending { it.positionMs })
      .distinctBy { it.fileKey }
      .take(limit)
      .toList()
  }

  /** Where the strip should restart playback from, in milliseconds. */
  fun resumeAtMs(video: LibraryVideo): Long = video.positionMs.coerceAtLeast(0L)

  /** True when the strip has something to show at all. */
  fun hasAnything(videos: List<LibraryVideo>): Boolean = videos.any(::isResumable)
}
