package live.mehiz.mpvkt.subtitles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Pure subtitle-search decisions — no Android, no network — so the parts that
 * decide *what to ask for*, *which reply to trust* and *how an HTTP status maps
 * to a shared error* can be unit tested.
 */
object SubtitleLogic {
  const val PROVIDER_LEGACY = "opensubtitles-legacy"
  const val PROVIDER_COM = "opensubtitles-com"

  private const val MAX_QUERY_WORDS = 4
  private const val MAX_FALLBACK_WORDS = 3
  private const val MAX_RESULTS = 30
  private const val SRT = "srt"
  private const val UNKNOWN_LANGUAGE = "und"
  private const val FALLBACK_NAME = "subtitle.srt"
  private const val HTTP_UNAUTHORIZED = 401
  private const val HTTP_FORBIDDEN = 403
  private const val HTTP_TOO_MANY = 429
  private const val HTTP_SERVER_ERROR = 500

  private val VIDEO_EXTENSION =
    Regex("""\.(mkv|mp4|m4v|avi|mov|webm|ts|m2ts|flv|wmv|mpg|mpeg|iso|strm)$""", RegexOption.IGNORE_CASE)
  private val SEPARATORS = Regex("""[._]+""")
  private val RELEASE_GROUP = Regex("""\[[^\]]*]""")
  private val WHITESPACE = Regex("""\s+""")
  private val EPISODE = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,4})""")
  private val CUE = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}""")

  /** Release tags that never help a subtitle search and only narrow it to nothing. */
  private val QUALITY_TOKENS = setOf(
    "1080p", "720p", "2160p", "480p", "4k", "x264", "x265", "h264", "h265", "hevc",
    "bluray", "brrip", "webrip", "web", "webdl", "hdtv", "hdrip", "dvdrip", "remux",
    "aac", "ac3", "dts", "ddp", "hdr", "proper", "repack", "extended", "internal",
  )

  private val LANGUAGES = mapOf(
    "eng" to "English",
    "en" to "English",
    "ind" to "Indonesian",
    "may" to "Malay",
    "msa" to "Malay",
    "zho" to "Chinese",
    "chi" to "Chinese",
    "jpn" to "Japanese",
    "kor" to "Korean",
    "tha" to "Thai",
    "vie" to "Vietnamese",
    "tgl" to "Tagalog",
    "fil" to "Filipino",
    "mya" to "Burmese",
    "ara" to "Arabic",
    "spa" to "Spanish",
    "fre" to "French",
    "ger" to "German",
    "rus" to "Russian",
    "por" to "Portuguese",
  )

  /** "Game.of.Thrones.S01E01.1080p.mkv" -> "Game of Thrones S01E01 1080p". */
  fun cleanName(raw: String): String =
    raw.replace(VIDEO_EXTENSION, "")
      .replace(SEPARATORS, " ")
      .replace(RELEASE_GROUP, " ")
      .replace(WHITESPACE, " ")
      .trim()

  /**
   * The narrower query used when the full release name finds nothing:
   * show/movie words plus the SxxExx marker, or the first few words of a film.
   */
  fun fallbackQuery(cleaned: String): String {
    val episode = EPISODE.find(cleaned)
    if (episode != null) {
      val name = cleaned.substring(0, episode.range.first)
        .split(' ')
        .filter { it.isNotBlank() && it.lowercase() !in QUALITY_TOKENS }
        .take(MAX_QUERY_WORDS)
      return (name + episode.value).joinToString(" ").trim()
    }
    return cleaned.split(' ')
      .filter { it.isNotBlank() && it.lowercase() !in QUALITY_TOKENS }
      .take(MAX_FALLBACK_WORDS)
      .joinToString(" ")
  }

  /** Queries to try in order: exact release name first, then the looser one. */
  fun queries(mediaTitle: String): List<String> {
    val cleaned = cleanName(mediaTitle)
    if (cleaned.isBlank()) return emptyList()
    return listOf(cleaned, fallbackQuery(cleaned)).filter { it.isNotBlank() }.distinct()
  }

  /**
   * One place that decides what HTTP means, so both providers report the same
   * [ProviderError] for the same situation.
   */
  fun errorFor(status: Int): ProviderError = when {
    status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN -> ProviderError.KeyMissing
    status == HTTP_TOO_MANY -> ProviderError.RateLimited
    else -> ProviderError.ProviderUnavailable
  }

  fun isSuccess(status: Int): Boolean = status in 200..299

  fun isServerFailure(status: Int): Boolean = status >= HTTP_SERVER_ERROR

  /** Keyless endpoint: a plain JSON array of results. */
  fun parseHits(body: String): List<SubtitleHit> {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
    return root.mapNotNull { (it as? JsonObject)?.toLegacyHit() }
  }

  /** OpenSubtitles.com: `data[].attributes`, with the file id under `files[0].file_id`. */
  fun parseComHits(body: String): List<SubtitleHit> {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
    val data = root["data"] as? JsonArray ?: return emptyList()
    return data.mapNotNull { entry ->
      val attributes = (entry as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
      val fileId = ((attributes["files"] as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.get("file_id")?.let { (it as? JsonPrimitive)?.contentOrNull }
      if (fileId.isNullOrBlank()) return@mapNotNull null
      val details = attributes["feature_details"] as? JsonObject
      SubtitleHit(
        id = fileId,
        fileName = attributes.text("release")
          ?: details?.text("movie_name")
          ?: attributes.text("url")
          ?: FALLBACK_NAME,
        language = attributes.text("language") ?: UNKNOWN_LANGUAGE,
        format = attributes.text("format") ?: SRT,
        downloads = attributes.int("download_count"),
        rating = attributes.double("ratings"),
        providerId = PROVIDER_COM,
        downloadRef = fileId,
      )
    }
  }

  /** Where the provider's answer says "slow down" in words rather than a 429. */
  fun looksRateLimited(body: String): Boolean {
    val lower = body.lowercase()
    return lower.contains("rate limit") || lower.contains("too many requests") ||
      lower.contains("quota") && lower.contains("exceed")
  }

  /** Best first: real .srt files, then by rating, then by how many people downloaded them. */
  fun rank(hits: List<SubtitleHit>): List<SubtitleHit> =
    hits.sortedWith(
      compareByDescending<SubtitleHit> { it.format.equals(SRT, ignoreCase = true) }
        .thenByDescending { it.rating }
        .thenByDescending { it.downloads }
        .thenBy { it.fileName.lowercase() },
    ).take(MAX_RESULTS)

  /** A downloaded file is only usable when it actually contains subtitle cues. */
  fun looksLikeSubtitle(body: String): Boolean = CUE.containsMatchIn(body)

  fun languageLabel(code: String): String =
    LANGUAGES[code.lowercase()] ?: code.uppercase()

  private fun JsonObject.toLegacyHit(): SubtitleHit? {
    val url = text("SubDownloadLink") ?: return null
    return SubtitleHit(
      id = text("IDSubtitleFile") ?: text("IDSubtitle") ?: url,
      fileName = text("SubFileName") ?: FALLBACK_NAME,
      language = text("SubLanguageID") ?: UNKNOWN_LANGUAGE,
      format = text("SubFormat") ?: UNKNOWN_LANGUAGE,
      downloads = text("SubDownloadsCnt")?.toIntOrNull() ?: 0,
      rating = text("SubRating")?.toDoubleOrNull() ?: 0.0,
      providerId = PROVIDER_LEGACY,
      downloadRef = url,
    )
  }

  private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

  private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 0

  private fun JsonObject.double(key: String): Double =
    (this[key] as? JsonPrimitive)?.let {
      it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() ?: if (it.booleanOrNull == true) 1.0 else null
    } ?: 0.0
}
