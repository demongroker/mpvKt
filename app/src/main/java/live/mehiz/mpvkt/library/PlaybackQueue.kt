package live.mehiz.mpvkt.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of the queue that is playing: the folder's files, in playback order. */
@Serializable
data class QueueItem(
  val fileKey: String,
  val mediaId: Long,
  val uri: String,
  val path: String,
  val name: String,
)

/**
 * The queue is the folder the current file belongs to. Shuffle only reorders this
 * queue — the library's own sort order is never touched.
 */
@Serializable
data class PlaybackQueue(
  val folder: String,
  val items: List<QueueItem>,
  val shuffled: Boolean = false,
  val seed: Long = 0,
) {
  fun toJson(): String = json.encodeToString(serializer(), this)

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromJson(raw: String?): PlaybackQueue? {
      if (raw.isNullOrBlank()) return null
      return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
  }
}

/** Pure queue arithmetic: what plays next, and in what order. */
object QueueLogic {

  fun queueFor(
    videos: List<LibraryVideo>,
    folder: String,
    shuffled: Boolean,
    seed: Long,
  ): PlaybackQueue = PlaybackQueue(
    folder = folder,
    items = videos.map { video ->
      QueueItem(
        fileKey = video.fileKey,
        mediaId = video.mediaId,
        uri = video.uri,
        path = video.path,
        name = video.name,
      )
    },
    shuffled = shuffled,
    seed = seed,
  )

  /** The order the queue actually plays in. */
  fun ordered(queue: PlaybackQueue): List<QueueItem> =
    if (queue.shuffled) shuffled(queue.items, queue.seed) else queue.items

  /**
   * Deterministic shuffle: the same seed always produces the same order, so a
   * queue survives the player being reopened without storing the permutation.
   */
  fun shuffled(items: List<QueueItem>, seed: Long): List<QueueItem> {
    if (items.size < 2) return items
    return items.sortedBy { mix(seed, it.fileKey.hashCode().toLong()) }
  }

  /**
   * The file after [currentKey]. Returns null at the end of the folder unless
   * [loop] is on; a single-file folder loops onto itself only when looping.
   */
  fun next(queue: PlaybackQueue, currentKey: String, loop: Boolean): QueueItem? {
    val order = ordered(queue)
    if (order.isEmpty()) return null
    val index = order.indexOfFirst { it.fileKey == currentKey }
    if (index < 0) return order.first()
    if (index + 1 < order.size) return order[index + 1]
    return if (loop) order.first() else null
  }

  fun hasNext(queue: PlaybackQueue, currentKey: String, loop: Boolean): Boolean =
    next(queue, currentKey, loop) != null

  /** SplitMix-style mixing so neighbouring keys do not hash into the same order. */
  private fun mix(seed: Long, value: Long): Long {
    var z = seed xor value + MIX_INC
    z = (z xor (z ushr 30)) * MIX_MUL_1
    z = (z xor (z ushr 27)) * MIX_MUL_2
    return z xor (z ushr 31)
  }

  private const val MIX_INC = -0x61C8864680B583EBL
  private const val MIX_MUL_1 = -0x40a7b892e31b1a47L
  private const val MIX_MUL_2 = -0x6b2fb644ecceee15L
}
