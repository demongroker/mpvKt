package live.mehiz.mpvkt.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationLogicTest {

  @Test
  fun autoModeHandsOrientationToTheSensors() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR,
      RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.SensorLandscape),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_SENSOR,
      RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.SensorPortrait),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
      RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.Free),
    )
  }

  @Test
  fun lockedModeUsesTheCurrentOrientationExplicitly() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      RotationLogic.requestedOrientation(
        RotationMode.Locked,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        PlayerOrientation.SensorLandscape,
      ),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      RotationLogic.requestedOrientation(
        RotationMode.Locked,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        PlayerOrientation.SensorLandscape,
      ),
    )
  }

  @Test
  fun aDeliberatelyFixedPreferenceStaysFixed() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.Portrait),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.Landscape),
    )
  }

  @Test
  fun pressingTheButtonChangesTheRequestedOrientationBothWays() {
    val auto = RotationLogic.requestedOrientation(RotationMode.Auto, 0, PlayerOrientation.SensorLandscape)
    val locked = RotationLogic.requestedOrientation(
      RotationMode.Auto.toggled(),
      RotationLogic.lockTo(Configuration.ORIENTATION_LANDSCAPE),
      PlayerOrientation.SensorLandscape,
    )
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, auto)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, locked)
    assertFalse(RotationLogic.applied(auto, locked))
  }

  @Test
  fun lockingFromLandscapeAndPortraitPicksTheRightConstant() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      RotationLogic.lockTo(Configuration.ORIENTATION_LANDSCAPE),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      RotationLogic.lockTo(Configuration.ORIENTATION_PORTRAIT),
    )
  }

  @Test
  fun therequestedValueIsCheckedAgainstTheActivitysValue() {
    assertTrue(RotationLogic.applied(ActivityInfo.SCREEN_ORIENTATION_SENSOR, ActivityInfo.SCREEN_ORIENTATION_SENSOR))
    assertFalse(RotationLogic.applied(ActivityInfo.SCREEN_ORIENTATION_SENSOR, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE))
  }
}
