package live.mehiz.mpvkt.subtitles

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** A raw HTTP answer, already read into memory. */
data class HttpReply(val status: Int, val body: ByteArray, val contentType: String?) {
  fun text(): String = body.toString(Charsets.UTF_8)
}

/**
 * Minimal HTTP for the subtitle providers: it follows redirects itself, sends
 * the headers providers insist on, and hands back gzip-decoded bodies.
 */
object SubtitleHttp {
  private const val TIMEOUT_MS = 20_000
  private const val MAX_REDIRECTS = 5
  private const val BUFFER_SIZE = 8 * 1024
  private const val GZIP_MAGIC = 0x1f
  private const val GZIP_MAGIC_SECOND = 0x8b
  private const val REDIRECT_MIN = 300
  private const val REDIRECT_MAX = 399
  private const val SUCCESS_MIN = 200
  private const val SUCCESS_MAX = 299

  fun get(url: String, headers: Map<String, String> = emptyMap()): HttpReply =
    request(url, headers, method = "GET", body = null)

  fun postJson(url: String, headers: Map<String, String>, json: String): HttpReply =
    request(url, headers + mapOf("Content-Type" to "application/json"), method = "POST", body = json.toByteArray())

  private fun request(url: String, headers: Map<String, String>, method: String, body: ByteArray?): HttpReply {
    var current = url
    repeat(MAX_REDIRECTS) {
      val connection = (URL(current).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = false
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
        if (body != null) {
          doOutput = true
          setFixedLengthStreamingMode(body.size)
        }
      }
      try {
        if (body != null) connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        if (status in REDIRECT_MIN..REDIRECT_MAX) {
          val next = connection.getHeaderField("Location") ?: error("redirect without location")
          current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
        } else {
          val stream: InputStream? = if (status in SUCCESS_MIN..SUCCESS_MAX) {
            connection.inputStream
          } else {
            connection.errorStream
          }
          val bytes = stream?.use { decode(it.readBytes()) } ?: ByteArray(0)
          return HttpReply(status, bytes, connection.contentType)
        }
      } finally {
        connection.disconnect()
      }
    }
    error("too many redirects")
  }

  /** OpenSubtitles serves gzipped subtitle files; JSON answers are plain. */
  private fun decode(bytes: ByteArray): ByteArray {
    val isGzip = bytes.size > 1 &&
      bytes[0].toInt().and(0xff) == GZIP_MAGIC &&
      bytes[1].toInt().and(0xff) == GZIP_MAGIC_SECOND
    return if (isGzip) GZIPInputStream(bytes.inputStream()).use { it.readAllBytes() } else bytes
  }

  private fun InputStream.readAllBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
      val read = read(buffer)
      if (read <= 0) break
      out.write(buffer, 0, read)
    }
    return out.toByteArray()
  }
}
