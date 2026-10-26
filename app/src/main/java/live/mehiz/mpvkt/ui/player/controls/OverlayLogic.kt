package live.mehiz.mpvkt.ui.player.controls

/**
 * The brightness / volume sliders hide themselves a moment after the last gesture
 * and disappear at once when the video is tapped.
 */
object OverlayLogic {

  /** How long the overlay stays after the last gesture. */
  const val HIDE_AFTER_MS = 1_500L

  fun shouldHide(isShown: Boolean, elapsedSinceLastGestureMs: Long): Boolean =
    isShown && elapsedSinceLastGestureMs >= HIDE_AFTER_MS

  /** A tap anywhere on the video dismisses the overlay immediately. */
  fun afterTap(): Boolean = false
}
