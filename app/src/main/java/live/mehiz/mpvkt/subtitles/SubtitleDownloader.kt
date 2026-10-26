package live.mehiz.mpvkt.subtitles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * Fetches subtitles from OpenSubtitles' public search API (the one VLC's VLSub
 * talks to). No account and no API key are needed — but it wants a plain
 * User-Agent, answers with a redirect, and hands out gzipped .srt files.
 */
object SubtitleDownloader {
  private const val API = "https://rest.opensubtitles.org/search"
  private const val USER_AGENT = "TemporaryUserAgent"
  private const val TIMEOUT_MS = 20_000
  private const val DEFAULT_LANGUAGE = "eng"
  private const val SRT_EXTENSION = "srt"
  private const val FALLBACK_BASENAME = "video"
  private const val GZIP_MAGIC = 0x1f
  private const val GZIP_MAGIC_SECOND = 0x8b
  private const val MAX_REDIRECTS = 5

  /**
   * Searches for [mediaTitle] (a file name is fine), trying the exact release
   * name first and a looser query second. Empty list means nothing was found.
   */
  suspend fun search(mediaTitle: String, language: String = DEFAULT_LANGUAGE): List<SubtitleHit> =
    withContext(Dispatchers.IO) {
      for (query in SubtitleLogic.queries(mediaTitle)) {
        val hits = runCatching {
          val body = fetch(searchUrl(query, language)).toString(Charsets.UTF_8)
          SubtitleLogic.rank(SubtitleLogic.parseHits(body))
        }.getOrDefault(emptyList())
        if (hits.isNotEmpty()) return@withContext hits
      }
      emptyList()
    }

  /**
   * Downloads [hit] beside the app's other files and returns the local .srt, or
   * null when the transfer failed or the payload is not really a subtitle.
   */
  suspend fun download(context: Context, hit: SubtitleHit, mediaTitle: String): File? =
    withContext(Dispatchers.IO) {
      val text = runCatching { decode(fetch(hit.downloadUrl)) }.getOrNull() ?: return@withContext null
      if (!SubtitleLogic.looksLikeSubtitle(text)) return@withContext null
      val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "subtitles")
      if (!dir.exists() && !dir.mkdirs()) return@withContext null
      val basename = SubtitleLogic.cleanName(mediaTitle).ifBlank { FALLBACK_BASENAME }
      val file = File(dir, "$basename.${hit.language}.$SRT_EXTENSION")
      runCatching {
        file.writeText(text)
        file
      }.getOrNull()
    }

  private fun searchUrl(query: String, language: String): String =
    "$API/query-${URLEncoder.encode(query, "UTF-8").replace("+", "%20")}/sublanguageid-$language"

  private fun fetch(url: String): ByteArray {
    var current = url
    repeat(MAX_REDIRECTS) {
      val connection = (URL(current).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "*/*")
      }
      try {
        val code = connection.responseCode
        if (code in 300..399) {
          val next = connection.getHeaderField("Location") ?: error("redirect without location")
          current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
        } else {
          if (code !in 200..299) error("HTTP $code")
          return connection.inputStream.use { it.readBytes() }
        }
      } finally {
        connection.disconnect()
      }
    }
    error("too many redirects")
  }

  /** OpenSubtitles serves gzipped files; plain text is passed through. */
  private fun decode(bytes: ByteArray): String {
    val isGzip = bytes.size > 1 &&
      bytes[0].toInt().and(0xff) == GZIP_MAGIC &&
      bytes[1].toInt().and(0xff) == GZIP_MAGIC_SECOND
    val text = if (isGzip) {
      GZIPInputStream(bytes.inputStream()).use { readAll(it) }
    } else {
      String(bytes, Charsets.UTF_8)
    }
    return text.removePrefix("\uFEFF")
  }

  private fun readAll(input: InputStream): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
  }

  private const val BUFFER_SIZE = 8 * 1024
}
