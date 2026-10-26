package live.mehiz.mpvkt.update

/**
 * Pure update decisions — no Android, no network, so they can be unit tested.
 * Kept out of [Updater] on purpose: a wrong answer here means the phone either
 * misses an update or installs a truncated APK.
 */
object UpdateLogic {

  /** A version parsed out of a release tag, a versionName or a git tag. */
  data class VersionNumber(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** The `-vela<N>` build number, or 0 when the string carries none. */
    val build: Int,
  )

  private val VERSION = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:\D*?vela(\d+))?.*$""", RegexOption.IGNORE_CASE)

  /**
   * "v0.1.10-vela10" -> 0.1.10 build 10
   * "0.1.11"         -> 0.1.11 build 0
   * Returns null for anything that is not a version — a tag we cannot read must
   * never be treated as an update.
   */
  fun parseVersion(raw: String?): VersionNumber? {
    val match = VERSION.find(raw?.trim().orEmpty()) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    val patch = match.groupValues[3].toIntOrNull() ?: return null
    val build = match.groupValues[4].toIntOrNull() ?: 0
    return VersionNumber(major, minor, patch, build)
  }

  /** Numeric comparison of major.minor.patch only; negative when [a] is older. */
  fun compareSemver(a: VersionNumber, b: VersionNumber): Int {
    if (a.major != b.major) return a.major - b.major
    if (a.minor != b.minor) return a.minor - b.minor
    return a.patch - b.patch
  }

  /**
   * Should the update banner be shown?
   *
   * Compares the release tag with what is installed, numerically: first
   * major.minor.patch, then (for the same version) the `-vela<N>` build number
   * against the installed tag's build, falling back to the installed versionCode.
   * Equal or older never shows a banner — the old code compared the tag to the
   * build's git sha, which kept offering an update to the version already
   * installed.
   */
  fun isUpdateAvailable(
    remoteTag: String?,
    installedVersionName: String?,
    installedTag: String?,
    installedVersionCode: Int = 0,
  ): Boolean {
    val remote = parseVersion(remoteTag) ?: return false
    val installed = parseVersion(installedVersionName) ?: return false
    val bySemver = compareSemver(remote, installed)
    if (bySemver != 0) return bySemver > 0
    val installedBuild = parseVersion(installedTag)?.build?.takeIf { it > 0 } ?: installedVersionCode
    return remote.build > installedBuild
  }

  /** "v0.1.10-vela10" -> [0, 1, 10] ; ignores the leading "v" and any "-suffix". */
  fun versionFromTag(tag: String): List<Int> =
    tag.trim()
      .removePrefix("v")
      .substringBefore('-')
      .split('.')
      .mapNotNull { it.trim().toIntOrNull() }

  /** True when [releaseTag] is a strictly newer version than [currentVersionName]. */
  fun isNewer(releaseTag: String, currentVersionName: String): Boolean {
    val remote = parseVersion(releaseTag)
    val installed = parseVersion(currentVersionName)
    if (remote != null && installed != null) return compareSemver(remote, installed) > 0
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
