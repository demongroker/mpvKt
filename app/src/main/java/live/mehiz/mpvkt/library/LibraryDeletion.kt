package live.mehiz.mpvkt.library

/**
 * What "delete" means for the library's own state.
 *
 * Deletion itself is done by the system: MediaStore.createDeleteRequest shows its
 * own confirmation, and only a RESULT_OK from it may reach [applySuccess]. Nothing
 * here touches the file — the file is gone by the time this runs.
 */
object LibraryDeletion {

  /** The file is gone: drop its resume row and remove it from the library list. */
  suspend fun applySuccess(
    videos: List<LibraryVideo>,
    fileKey: String,
    clearResume: suspend (String) -> Unit,
  ): List<LibraryVideo> {
    clearResume(fileKey)
    return videos.filterNot { it.fileKey == fileKey }
  }

  /** Cancelled in either dialog: the list and the resume row stay exactly as they are. */
  fun applyCancel(videos: List<LibraryVideo>, fileKey: String): List<LibraryVideo> = videos

  /** Drops the deleted file from a queue so "next" never points at a missing file. */
  fun pruneQueue(queue: PlaybackQueue?, fileKey: String): PlaybackQueue? =
    queue?.copy(items = queue.items.filterNot { it.fileKey == fileKey })
}
