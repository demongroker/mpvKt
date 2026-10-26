package live.mehiz.mpvkt.library

/**
 * Advancing the queue at the end of a file.
 *
 * The player's idea of "the current file" can be a different string from the one
 * the library stored (a resolved file descriptor, a title, a re-scanned path), so
 * the current item is located by key, then by uri/path/name before giving up.
 */
object QueueAdvance {

  /** Highest number of failed opens to skip past before giving up. */
  const val MAX_SKIPS = 3

  fun indexOfCurrent(items: List<QueueItem>, key: String, uri: String, path: String, name: String): Int {
    val byKey = items.indexOfFirst { it.fileKey == key }
    if (byKey >= 0) return byKey
    val byUri = items.indexOfFirst { it.uri == uri }
    if (byUri >= 0) return byUri
    val byPath = items.indexOfFirst { it.path == path && path.isNotBlank() }
    if (byPath >= 0) return byPath
    return items.indexOfFirst { it.name == name && name.isNotBlank() }
  }

  /**
   * The next item to play after [currentKey] ended, skipping anything that already
   * failed to open. Returns null when the queue is missing or exhausted.
   */
  fun nextAfterEnd(
    queue: PlaybackQueue?,
    currentKey: String,
    currentUri: String,
    currentPath: String,
    currentName: String,
    failedKeys: Set<String>,
    loop: Boolean,
  ): QueueItem? {
    if (queue == null) return null
    val order = QueueLogic.ordered(queue)
    if (order.isEmpty()) return null
    if (failedKeys.size >= MAX_SKIPS) return null
    val index = indexOfCurrent(order, currentKey, currentUri, currentPath, currentName)
    val start = if (index < 0) 0 else index + 1
    val tail = order.drop(start).ifEmpty { if (loop) order else emptyList() }
    return tail.firstOrNull { it.fileKey !in failedKeys }
  }

  /** Builds a queue from a folder listing when the player was started without one. */
  fun queueFromFolder(
    videos: List<LibraryVideo>,
    folder: String,
    shuffled: Boolean,
    seed: Long,
  ): PlaybackQueue? =
    if (videos.isEmpty()) null else QueueLogic.queueFor(videos, folder, shuffled, seed)

  /** The folder the current file lives in, matched by path, uri, name or file key. */
  fun folderOf(
    videos: List<LibraryVideo>,
    currentKey: String,
    currentUri: String,
    currentPath: String,
    currentName: String,
  ): String? {
    val match = videos.firstOrNull { it.fileKey == currentKey }
      ?: videos.firstOrNull { it.uri == currentUri }
      ?: videos.firstOrNull { it.path == currentPath && currentPath.isNotBlank() }
      ?: videos.firstOrNull { it.name == currentName && currentName.isNotBlank() }
    return match?.folder
  }
}
