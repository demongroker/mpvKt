package live.mehiz.mpvkt.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Default provider: OpenSubtitles' public search endpoint (the one VLC's VLSub
 * uses). No account, no API key — but it wants a plain User-Agent, answers with
 * a redirect and hands out gzipped .srt files.
 */
class LegacyOpenSubtitlesProvider : SubtitleProvider {
  override val id: String = SubtitleLogic.PROVIDER_LEGACY

  private val headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "*/*")

  override suspend fun search(query: String, language: String): SubtitleSearchResult =
    withContext(Dispatchers.IO) {
      for (candidate in SubtitleLogic.queries(query)) {
        val reply = runCatching { SubtitleHttp.get(searchUrl(candidate, language), headers) }.getOrNull()
          ?: return@withContext SubtitleSearchResult.Failure(ProviderError.ProviderUnavailable)
        if (!SubtitleLogic.isSuccess(reply.status)) {
          return@withContext SubtitleSearchResult.Failure(SubtitleLogic.errorFor(reply.status))
        }
        val hits = SubtitleLogic.rank(SubtitleLogic.parseHits(reply.text()))
        if (hits.isNotEmpty()) return@withContext SubtitleSearchResult.Found(hits)
      }
      SubtitleSearchResult.Found(emptyList())
    }

  override suspend fun download(hit: SubtitleHit): SubtitleFileResult = withContext(Dispatchers.IO) {
    val reply = runCatching { SubtitleHttp.get(hit.downloadRef, headers) }.getOrNull()
      ?: return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    if (!SubtitleLogic.isSuccess(reply.status)) {
      return@withContext SubtitleFileResult.Failure(SubtitleLogic.errorFor(reply.status))
    }
    val text = reply.text().removePrefix("\uFEFF")
    if (!SubtitleLogic.looksLikeSubtitle(text)) {
      return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    }
    SubtitleFileResult.Text(text)
  }

  private fun searchUrl(query: String, language: String): String =
    "$API/query-${URLEncoder.encode(query, "UTF-8").replace("+", "%20")}/sublanguageid-$language"

  private companion object {
    const val API = "https://rest.opensubtitles.org/search"
    const val USER_AGENT = "TemporaryUserAgent"
  }
}
