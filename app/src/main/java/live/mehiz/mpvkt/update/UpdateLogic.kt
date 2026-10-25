package live.mehiz.mpvkt.update

/**
 * Pure update decisions — no Android, no network, so they can be unit tested.
 * Kept out of [Updater] on purpose: a wrong answer here means the phone either
 * misses an update or installs a truncated APK.
 */
object UpdateLogic {

  /** "v0.1.10-vela10" -> [0, 1, 10] ; ignores the leading "v" and any "-suffix". */
  fun versionFromTag(tag: String): List<Int> =
    tag.trim()
      .removePrefix("v")
      .substringBefore('-')
      .split('.')
      .mapNotNull { it.trim().toIntOrNull() }

  /** True when [releaseTag] is a strictly newer version than [currentVersionName]. */
  fun isNewer(releaseTag: String, currentVersionName: String): Boolean {
    val a = versionFromTag(releaseTag)
    val b = versionFromTag(currentVersionName)
    val firstDifference = (0 until maxOf(a.size, b.size))
      .firstOrNull { a.getOrElse(it) { 0 } != b.getOrElse(it) { 0 } }
    return if (a.isEmpty() || firstDifference == null) {
      false
    } else {
      a.getOrElse(firstDifference) { 0 } > b.getOrElse(firstDifference) { 0 }
    }
  }

  /** Prefer the small arm64-only build; fall back to the universal APK. */
  fun pickApk(names: List<String>): String? =
    names.firstOrNull { it.endsWith("arm64-v8a-release.apk") }
      ?: names.firstOrNull { it.endsWith(".apk") && !it.endsWith(".apk.sha256") }

  fun pickSha(names: List<String>): String? =
    names.firstOrNull { it.endsWith("arm64-v8a-release.apk.sha256") }
      ?: names.firstOrNull { it.endsWith(".apk.sha256") }

  /** A download may only be installed when its hash matches the published one. */
  fun matches(expectedSha: String?, actualSha: String?): Boolean {
    val e = expectedSha?.trim()?.lowercase()?.substringBefore(' ')
    val a = actualSha?.trim()?.lowercase()
    return !e.isNullOrEmpty() && !a.isNullOrEmpty() && e == a
  }

  /** Anything under this is a truncated download, whatever the hash says. */
  fun plausibleSize(bytes: Long): Boolean = bytes > 10L * 1024 * 1024
}
