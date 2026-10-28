package live.mehiz.mpvkt.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

  @Test
  fun tagParsingIgnoresPrefixAndSuffix() {
    assertEquals(listOf(0, 1, 10), UpdateLogic.versionFromTag("v0.1.10-vela10"))
    assertEquals(listOf(0, 1, 8), UpdateLogic.versionFromTag("0.1.8"))
  }

  @Test
  fun versionParsingKeepsTheVelaBuildNumber() {
    assertEquals(
      UpdateLogic.VersionNumber(0, 1, 10, 10),
      UpdateLogic.parseVersion("v0.1.10-vela10"),
    )
    assertEquals(UpdateLogic.VersionNumber(0, 1, 11, 0), UpdateLogic.parseVersion("0.1.11"))
    assertEquals(UpdateLogic.VersionNumber(2, 0, 3, 42), UpdateLogic.parseVersion("V2.0.3-vela42"))
    assertNull(UpdateLogic.parseVersion("nightly"))
    assertNull(UpdateLogic.parseVersion(""))
    assertNull(UpdateLogic.parseVersion(null))
  }

  @Test
  fun newerVersionIsDetected() {
    assertTrue(UpdateLogic.isNewer("v0.1.10-vela10", "0.1.8"))
    assertTrue(UpdateLogic.isNewer("v0.2.0-vela20", "0.1.99"))
    assertFalse(UpdateLogic.isNewer("v0.1.8-vela8", "0.1.8"))
    assertFalse(UpdateLogic.isNewer("v0.1.7-vela7", "0.1.8"))
  }

  @Test
  fun malformedTagNeverLooksNewer() {
    assertFalse(UpdateLogic.isNewer("nightly", "0.1.8"))
    assertFalse(UpdateLogic.isNewer("", "0.1.8"))
  }

  // The bug from 0.1.10: the banner kept offering the version already installed,
  // because the release tag was compared with the build's git sha.

  @Test
  fun installedVersionNeverSeesItsOwnRelease() {
    assertFalse(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.10-vela10",
        installedVersionName = "0.1.10",
        installedTag = "v0.1.10-vela10",
        installedVersionCode = 230,
      ),
    )
    assertFalse(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.11-vela11",
        installedVersionName = "0.1.11",
        installedTag = "v0.1.11-vela11",
        installedVersionCode = 240,
      ),
    )
  }

  @Test
  fun olderInstallIsOfferedTheNewerRelease() {
    assertTrue(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.10-vela10",
        installedVersionName = "0.1.9",
        installedTag = "v0.1.9-vela9",
        installedVersionCode = 220,
      ),
    )
    assertTrue(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.11-vela11",
        installedVersionName = "0.1.10",
        installedTag = "v0.1.10-vela10",
        installedVersionCode = 230,
      ),
    )
  }

  @Test
  fun newerInstallIsNotOfferedAnOlderRelease() {
    assertFalse(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.9-vela9",
        installedVersionName = "0.1.10",
        installedTag = "v0.1.10-vela10",
        installedVersionCode = 230,
      ),
    )
    // 0.1.2 vs 0.1.10 must compare numerically, not as strings.
    assertFalse(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.2-vela2",
        installedVersionName = "0.1.10",
        installedTag = "v0.1.10-vela10",
        installedVersionCode = 230,
      ),
    )
  }

  @Test
  fun sameVersionWithAHigherVelaBuildIsAnUpdate() {
    assertTrue(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.11-vela12",
        installedVersionName = "0.1.11",
        installedTag = "v0.1.11-vela11",
        installedVersionCode = 240,
      ),
    )
    // Without an installed tag the versionCode is the fallback guard.
    assertFalse(
      UpdateLogic.isUpdateAvailable(
        remoteTag = "v0.1.11-vela11",
        installedVersionName = "0.1.11",
        installedTag = "",
        installedVersionCode = 240,
      ),
    )
  }

  @Test
  fun unreadableTagsNeverShowABanner() {
    assertFalse(UpdateLogic.isUpdateAvailable("nightly", "0.1.10", "v0.1.10-vela10", 230))
    assertFalse(UpdateLogic.isUpdateAvailable("", "0.1.10", "v0.1.10-vela10", 230))
    assertFalse(UpdateLogic.isUpdateAvailable("v0.1.11-vela11", "unknown", "", 240))
    assertFalse(UpdateLogic.isUpdateAvailable(null, null, null, 0))
  }

  @Test
  fun arm64AssetIsPreferred() {
    val names = listOf("app-release.apk.sha256", "app-release.apk", "app-arm64-v8a-release.apk")
    assertEquals("app-arm64-v8a-release.apk", UpdateLogic.pickApk(names))
    assertEquals("app-release.apk", UpdateLogic.pickApk(listOf("app-release.apk", "app-release.apk.sha256")))
    assertNull(UpdateLogic.pickApk(listOf("app-release.apk.sha256")))
  }

  @Test
  fun shaAssetPrefersArm64AndParsesHashFileFormat() {
    val names = listOf("app-release.apk.sha256", "app-arm64-v8a-release.apk.sha256")
    assertEquals("app-arm64-v8a-release.apk.sha256", UpdateLogic.pickSha(names))
    // `sha256sum` output is "<hash>  <file>"
    assertTrue(UpdateLogic.matches("ABC123  app-release.apk", "abc123"))
  }

  @Test
  fun hashMismatchAndMissingHashAreRejected() {
    assertFalse(UpdateLogic.matches("abc123", "def456"))
    assertFalse(UpdateLogic.matches(null, "abc123"))
    assertFalse(UpdateLogic.matches("abc123", null))
    assertFalse(UpdateLogic.matches("", ""))
  }

  @Test
  fun truncatedDownloadIsImplausible() {
    assertFalse(UpdateLogic.plausibleSize(5_735))
    assertTrue(UpdateLogic.plausibleSize(68_277_160))
  }

  private val assets = listOf(
    "vela-0.1.23.apk",
    "vela-0.1.23.apk.sha256",
    "vela-0.1.23-arm64.apk",
    "vela-0.1.23-arm64.apk.sha256",
  )

  @Test
  fun anArm64DeviceTakesTheSmallBuild() {
    assertEquals("vela-0.1.23-arm64.apk", UpdateLogic.pickApk(assets, preferArm64 = true))
    assertEquals(
      "vela-0.1.23-arm64.apk.sha256",
      UpdateLogic.pickSha(assets, preferArm64 = true),
    )
  }

  @Test
  fun theChecksumAlwaysMatchesTheChosenApk() {
    val armApk = UpdateLogic.pickApk(assets, preferArm64 = true)!!
    val armSha = UpdateLogic.pickSha(assets, preferArm64 = true)!!
    assertEquals("$armApk.sha256", armSha)
  }

  @Test
  fun aUniversalReleaseStillUpdates() {
    val universalOnly = listOf("vela-0.1.23.apk", "vela-0.1.23.apk.sha256")
    assertEquals("vela-0.1.23.apk", UpdateLogic.pickApk(universalOnly, preferArm64 = true))
    assertEquals("vela-0.1.23.apk.sha256", UpdateLogic.pickSha(universalOnly, preferArm64 = true))
  }

  @Test
  fun checksumsAreNeverMistakenForAnApk() {
    val chosen = UpdateLogic.pickApk(assets, preferArm64 = false)
    assertTrue(chosen == null || !chosen.endsWith(".sha256"))
  }
}
