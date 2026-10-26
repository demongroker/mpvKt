package live.mehiz.mpvkt.ui.player

import android.content.pm.ActivityInfo

/**
 * Which `requestedOrientation` the player should use.
 *
 * Kept pure (just integers) so the rotate button's effect can be unit tested:
 * [RotationMode.Auto] hands orientation to the sensors, [RotationMode.Locked] pins
 * the orientation the video was in when the button was pressed.
 */
object RotationLogic {

  /** Sensor-driven, follows the physical screen position. */
  const val AUTO: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR

  /**
   * "No locked orientation yet". Note SCREEN_ORIENTATION_LANDSCAPE is 0, so 0 can
   * never be the "unset" sentinel.
   */
  const val UNSPECIFIED: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

  fun lockTo(currentOrientation: Int): Int =
    if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

  /**
   * Auto means "follow the screen" — except when the Orientation preference is a
   * deliberately fixed one, which stays fixed because that is what was chosen.
   */
  fun requestedOrientation(
    mode: RotationMode,
    lockedOrientation: Int,
    preference: PlayerOrientation,
  ): Int {
    if (mode == RotationMode.Locked && lockedOrientation != UNSPECIFIED) return lockedOrientation
    return when (preference) {
      PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
      PlayerOrientation.SensorLandscape -> AUTO
      PlayerOrientation.SensorPortrait -> AUTO
      PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
      PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
      PlayerOrientation.Video -> AUTO
    }
  }

  /** True when the value we asked for is the value the activity actually has. */
  fun applied(requestedOrientation: Int, expected: Int): Boolean = requestedOrientation == expected
}
