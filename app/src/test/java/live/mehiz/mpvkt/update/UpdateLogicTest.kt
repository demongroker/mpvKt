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
}
