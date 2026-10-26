package live.mehiz.mpvkt.library

/** One video as the library shows it. */
data class LibraryVideo(
  /** MediaStore id plus a path hash, so same-named files in two folders stay apart. */
  val fileKey: String,
  val mediaId: Long,
  val uri: String,
  val name: String,
  val folder: String,
  val path: String,
  val durationMs: Long,
  val sizeBytes: Long,
  val dateAddedSec: Long,
  val positionMs: Long = 0,
  val lastPlayedAt: Long = 0,
  val completed: Boolean = false,
)

/** A folder as the library shows it: name, item count, cover and its videos. */
data class LibraryFolder(
  val name: String,
  val videos: List<LibraryVideo>,
  val pinned: Boolean = false,
) {
  val count: Int get() = videos.size
  val cover: LibraryVideo? get() = videos.firstOrNull()
}

enum class LibrarySort {
  Name,
  DateAdded,
  Size,
  LastPlayed,
  ;

  companion object {
    fun fromName(name: String?): LibrarySort =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DateAdded
  }
}

/**
 * Everything the library needs to hide something goes through this one type.
 * Hidden folders plug in today; locked folders will plug into the same filter
 * later without touching the repository or the screens.
 */
data class LibraryExclusions(
  val folders: Set<String> = emptySet(),
  val fileKeys: Set<String> = emptySet(),
) {
  fun isEmpty(): Boolean = folders.isEmpty() && fileKeys.isEmpty()

  operator fun plus(other: LibraryExclusions): LibraryExclusions =
    LibraryExclusions(folders + other.folders, fileKeys + other.fileKeys)
}
