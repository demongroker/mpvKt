package live.mehiz.mpvkt.subtitles

import android.content.Context
import java.io.File

/** Where downloaded subtitles land: next to the app's own files, never the media folder. */
object SubtitleStore {
  private const val DIR = "subtitles"
  private const val SRT_EXTENSION = "srt"
  private const val FALLBACK_BASENAME = "video"

  fun save(context: Context, body: String, mediaTitle: String, language: String): File? {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR)
    if (!dir.exists() && !dir.mkdirs()) return null
    val basename = SubtitleLogic.cleanName(mediaTitle).ifBlank { FALLBACK_BASENAME }
    val file = File(dir, "$basename.$language.$SRT_EXTENSION")
    return runCatching {
      file.writeText(body)
      file
    }.getOrNull()
  }
}
