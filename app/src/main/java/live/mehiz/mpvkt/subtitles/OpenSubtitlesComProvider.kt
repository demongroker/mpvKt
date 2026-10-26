package live.mehiz.mpvkt.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

/**
 * Optional provider: the official OpenSubtitles.com REST API. It needs an API key
 * from the user's account (stored encrypted, never in source) and, for the actual
 * download, the account's username and password. Missing credentials are reported
 * as [ProviderError.KeyMissing] instead of failing obscurely.
 */
class OpenSubtitlesComProvider(
  private val apiKey: String?,
  private val username: String?,
  private val password: String?,
) : SubtitleProvider {
  override val id: String = SubtitleLogic.PROVIDER_COM

  @Volatile
  private var token: String? = null

  override suspend fun search(query: String, language: String): SubtitleSearchResult =
    withContext(Dispatchers.IO) {
      val key = apiKey?.trim().orEmpty()
      if (key.isEmpty()) return@withContext SubtitleSearchResult.Failure(ProviderError.KeyMissing)
      val reply = runCatching {
        SubtitleHttp.get(searchUrl(query, language), headers(key))
      }.getOrNull() ?: return@withContext SubtitleSearchResult.Failure(ProviderError.ProviderUnavailable)
      if (!SubtitleLogic.isSuccess(reply.status)) {
        return@withContext SubtitleSearchResult.Failure(SubtitleLogic.errorFor(reply.status))
      }
      SubtitleSearchResult.Found(SubtitleLogic.rank(SubtitleLogic.parseComHits(reply.text())))
    }

  override suspend fun download(hit: SubtitleHit): SubtitleFileResult = withContext(Dispatchers.IO) {
    val key = apiKey?.trim().orEmpty()
    if (key.isEmpty()) return@withContext SubtitleFileResult.Failure(ProviderError.KeyMissing)
    val auth = token ?: login(key) ?: return@withContext SubtitleFileResult.Failure(ProviderError.KeyMissing)
    token = auth
    val request = """{"file_id":${hit.downloadRef.toLongOrNull() ?: return@withContext SubtitleFileResult.Failure(
      ProviderError.ProviderUnavailable,
    )}}"""
    val reply = runCatching {
      SubtitleHttp.postJson(
        "$API/download",
        headers(key) + mapOf("Authorization" to "Bearer $auth"),
        request,
      )
    }.getOrNull() ?: return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    if (!SubtitleLogic.isSuccess(reply.status)) {
      val error = SubtitleLogic.errorFor(reply.status)
      if (error == ProviderError.KeyMissing) token = null
      return@withContext SubtitleFileResult.Failure(error)
    }
    val link = runCatching { field(reply.text(), "link") }.getOrNull()
      ?: return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    val file = runCatching { SubtitleHttp.get(link) }.getOrNull()
      ?: return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    if (!SubtitleLogic.isSuccess(file.status)) {
      return@withContext SubtitleFileResult.Failure(SubtitleLogic.errorFor(file.status))
    }
    val text = file.text().removePrefix("\uFEFF")
    if (!SubtitleLogic.looksLikeSubtitle(text)) {
      return@withContext SubtitleFileResult.Failure(ProviderError.ProviderUnavailable)
    }
    SubtitleFileResult.Text(text)
  }

  /** Returns an auth token, or null when the account credentials are missing or rejected. */
  private fun login(key: String): String? {
    val user = username?.trim().orEmpty()
    val pass = password.orEmpty()
    if (user.isEmpty() || pass.isEmpty()) return null
    val body = """{"username":${quote(user)},"password":${quote(pass)}}"""
    val reply = runCatching { SubtitleHttp.postJson("$API/login", headers(key), body) }.getOrNull() ?: return null
    if (!SubtitleLogic.isSuccess(reply.status)) return null
    return runCatching { field(reply.text(), "token") }.getOrNull()
  }

  private fun headers(key: String): Map<String, String> = mapOf(
    "Api-Key" to key,
    "User-Agent" to USER_AGENT,
    "Accept" to "application/json",
  )

  private fun field(body: String, name: String): String? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    return (root[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
  }

  private fun quote(value: String): String = JsonPrimitive(value).toString()

  private fun searchUrl(query: String, language: String): String =
    "$API/subtitles?query=${URLEncoder.encode(query, "UTF-8")}&languages=$language"

  private companion object {
    const val API = "https://api.opensubtitles.com/api/v1"
    const val USER_AGENT = "Vela v0.1.11"
  }
}
