package live.mehiz.mpvkt.subtitles

/**
 * Errors every subtitle provider reports the same way, so the UI never has to
 * know which backend answered.
 */
enum class ProviderError {
  /** Network, DNS, timeout, HTTP 5xx, or an answer we could not parse. */
  ProviderUnavailable,

  /** The provider asked us to slow down (HTTP 429 or an exhausted quota). */
  RateLimited,

  /** The selected provider needs an API key (or account) that is missing or was rejected. */
  KeyMissing,
}

/** One subtitle file offered by a provider. */
data class SubtitleHit(
  val id: String,
  val fileName: String,
  val language: String,
  val format: String,
  val downloads: Int,
  val rating: Double,
  val providerId: String,
  /** Provider-specific handle: a download URL for the keyless endpoint, a file id for OpenSubtitles.com. */
  val downloadRef: String,
)

sealed interface SubtitleSearchResult {
  data class Found(val hits: List<SubtitleHit>) : SubtitleSearchResult
  data class Failure(val error: ProviderError) : SubtitleSearchResult
}

sealed interface SubtitleSaveResult {
  data class Saved(val file: java.io.File) : SubtitleSaveResult
  data class Failure(val error: ProviderError) : SubtitleSaveResult
}

sealed interface SubtitleFileResult {
  data class Text(val body: String) : SubtitleFileResult
  data class Failure(val error: ProviderError) : SubtitleFileResult
}

/** State of the in-player "download subtitles" sheet. */
sealed interface SubtitleSearchState {
  data class Idle(val query: String) : SubtitleSearchState

  data class Searching(val query: String) : SubtitleSearchState

  data class Results(val query: String, val hits: List<SubtitleHit>) : SubtitleSearchState

  /** [error] is a [ProviderError]; the sheet turns it into a sentence. */
  data class Failed(val query: String, val error: ProviderError) : SubtitleSearchState

  data class Downloading(val fileName: String) : SubtitleSearchState
}

/**
 * A subtitle backend. Adding a provider means implementing this — the player and
 * the sheet stay unchanged.
 */
interface SubtitleProvider {
  val id: String

  suspend fun search(query: String, language: String): SubtitleSearchResult

  suspend fun download(hit: SubtitleHit): SubtitleFileResult
}
