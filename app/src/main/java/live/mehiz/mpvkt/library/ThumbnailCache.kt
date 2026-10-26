package live.mehiz.mpvkt.library

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small in-memory thumbnail cache (least-recently-used) so scrolling a large
 * library does not decode the same frame over and over.
 */
class ThumbnailCache(
  private val maxEntries: Int = 160,
) {
  private val cache = object : LinkedHashMap<String, ImageBitmap>(maxEntries, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
      size > maxEntries
  }

  suspend fun load(context: Context, video: LibraryVideo): ImageBitmap? = withContext(Dispatchers.IO) {
    cache[video.fileKey]?.let { return@withContext it }
    val bitmap = decode(context, video) ?: return@withContext null
    cache[video.fileKey] = bitmap
    bitmap
  }

  fun clear() = cache.clear()

  fun size(): Int = cache.size

  private fun decode(context: Context, video: LibraryVideo): ImageBitmap? = try {
    val uri = video.uri.let { android.net.Uri.parse(it) }
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 29) {
      context.contentResolver.loadThumbnail(uri, Size(THUMB_WIDTH, THUMB_HEIGHT), null)
    } else {
      @Suppress("DEPRECATION")
      MediaStore.Video.Thumbnails.getThumbnail(
        context.contentResolver,
        video.mediaId,
        MediaStore.Video.Thumbnails.MINI_KIND,
        null,
      )
    }
    bitmap?.asImageBitmap()
  } catch (_: Exception) {
    null
  }

  private companion object {
    const val THUMB_WIDTH = 480
    const val THUMB_HEIGHT = 270
  }
}
