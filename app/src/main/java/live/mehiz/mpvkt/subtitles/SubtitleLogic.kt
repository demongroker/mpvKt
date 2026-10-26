package live.mehiz.mpvkt.subtitles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Pure subtitle-search decisions — no Android, no network — so the parts that
 * decide *what to ask for* and *which reply to trust* can be unit tested.
 */
object SubtitleLogic {
  private const val MAX_QUERY_WORDS = 4
  private const val MAX_FALLBACK_WORDS = 3
  private const val MAX_RESULTS = 30
  private const val SRT = "srt"
  private const val UNKNOWN_LANGUAGE = "und"
  private const val FALLBACK_NAME = "subtitle.srt"

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

  /** Parses the provider's JSON array; anything malformed is skipped, never thrown. */
  fun parseHits(body: String): List<SubtitleHit> {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
    return root.mapNotNull { (it as? JsonObject)?.toHit() }
  }

  /** Best first: real .srt files, then by how many people downloaded them. */
  fun rank(hits: List<SubtitleHit>): List<SubtitleHit> =
    hits.sortedWith(
      compareByDescending<SubtitleHit> { it.format.equals(SRT, ignoreCase = true) }
        .thenByDescending { it.downloads }
        .thenBy { it.fileName.lowercase() },
    ).take(MAX_RESULTS)

  /** A downloaded file is only usable when it actually contains subtitle cues. */
  fun looksLikeSubtitle(body: String): Boolean = CUE.containsMatchIn(body)

  fun languageLabel(code: String): String =
    LANGUAGES[code.lowercase()] ?: code.uppercase()

  private fun JsonObject.toHit(): SubtitleHit? {
    val url = text("SubDownloadLink") ?: return null
    return SubtitleHit(
      id = text("IDSubtitleFile") ?: text("IDSubtitle") ?: url,
      fileName = text("SubFileName") ?: FALLBACK_NAME,
      language = text("SubLanguageID") ?: UNKNOWN_LANGUAGE,
      format = text("SubFormat") ?: UNKNOWN_LANGUAGE,
      downloads = text("SubDownloadsCnt")?.toIntOrNull() ?: 0,
      downloadUrl = url,
    )
  }

  private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
