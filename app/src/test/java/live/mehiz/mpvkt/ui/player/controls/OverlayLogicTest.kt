package live.mehiz.mpvkt.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLogicTest {

  @Test
  fun theOverlayStaysWhileTheGestureIsRecent() {
    assertFalse(OverlayLogic.shouldHide(isShown = true, elapsedSinceLastGestureMs = 0))
    assertFalse(OverlayLogic.shouldHide(isShown = true, elapsedSinceLastGestureMs = 1_499))
  }

  @Test
  fun theOverlayHidesAfterFifteenHundredMilliseconds() {
    assertTrue(OverlayLogic.shouldHide(isShown = true, elapsedSinceLastGestureMs = 1_500))
    assertTrue(OverlayLogic.shouldHide(isShown = true, elapsedSinceLastGestureMs = 4_000))
  }

  @Test
  fun anOverlayThatIsAlreadyHiddenStaysHidden() {
    assertFalse(OverlayLogic.shouldHide(isShown = false, elapsedSinceLastGestureMs = 10_000))
  }

  @Test
  fun aTapDismissesTheOverlayImmediately() {
    assertEquals(false, OverlayLogic.afterTap())
  }
}
