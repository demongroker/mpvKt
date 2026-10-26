package live.mehiz.mpvkt.subtitles

/** One subtitle file offered by the provider. */
data class SubtitleHit(
  val id: String,
  val fileName: String,
  val language: String,
  val format: String,
  val downloads: Int,
  val downloadUrl: String,
)

/** State of the in-player "download subtitles" sheet. */
sealed interface SubtitleSearchState {
  /** Nothing searched yet for [query]. */
  data class Idle(val query: String) : SubtitleSearchState

  data class Searching(val query: String) : SubtitleSearchState

  data class Results(val query: String, val hits: List<SubtitleHit>) : SubtitleSearchState

  data class Failed(val query: String, val reason: String) : SubtitleSearchState

  data class Downloading(val fileName: String) : SubtitleSearchState
}
