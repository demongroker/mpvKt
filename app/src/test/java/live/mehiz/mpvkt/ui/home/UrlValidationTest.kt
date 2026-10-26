package live.mehiz.mpvkt.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidationTest {

  private val protocols = listOf("http", "https", "rtsp", "file", "content")

  @Test
  fun emptyFieldIsNeutral() {
    assertFalse(UrlValidation.showError("", protocols))
    assertFalse(UrlValidation.showError("   ", protocols))
    assertFalse(UrlValidation.isPlayable("", protocols))
  }

  @Test
  fun playableUrlsAreAccepted() {
    assertTrue(UrlValidation.isPlayable("https://example.com/video.mp4", protocols))
    assertTrue(UrlValidation.isPlayable("http://192.168.18.110:8097/Videos/1/stream", protocols))
    assertTrue(UrlValidation.isPlayable("  https://example.com/a.mkv  ", protocols))
    assertFalse(UrlValidation.showError("https://example.com/video.mp4", protocols))
  }

  @Test
  fun textThatCannotBePlayedShowsAnError() {
    assertTrue(UrlValidation.showError("not a url", protocols))
    assertTrue(UrlValidation.showError("example.com/video.mp4", protocols))
    assertTrue(UrlValidation.showError("ftp://example.com/video.mp4", protocols))
    assertTrue(UrlValidation.showError("/storage/emulated/0/video.mp4", protocols))
  }

  @Test
  fun schemeIsMatchedCaseInsensitively() {
    assertTrue(UrlValidation.isPlayable("HTTPS://example.com/video.mp4", protocols))
  }

  @Test
  fun halfTypedSchemeIsAnErrorOnlyOnceItHasText() {
    assertFalse(UrlValidation.showError("", protocols))
    assertTrue(UrlValidation.showError("htt", protocols))
    assertTrue(UrlValidation.showError("https://", protocols))
  }
}
