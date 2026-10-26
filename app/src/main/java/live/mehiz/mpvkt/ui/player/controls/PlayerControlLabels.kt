package live.mehiz.mpvkt.ui.player.controls

/**
 * Accessibility labels for the player's icon-only buttons.
 *
 * `ControlsButton` passes its `title` to `Icon(contentDescription = …)`, so these
 * strings are what TalkBack reads. They are constants so a test can prove none of
 * them is blank or duplicated.
 */
object PlayerControlLabels {
  const val PICTURE_IN_PICTURE = "Picture in picture"
  const val VIDEO_ASPECT_RATIO = "Video aspect ratio"

  const val PREVIOUS_FILE = "Previous file"
  const val NEXT_FILE = "Next file"
  const val SKIP_BACK_10 = "Skip back 10 seconds"
  const val SKIP_FORWARD_10 = "Skip forward 10 seconds"
  const val AUTO_ROTATE = "Auto rotate"
  const val LOCK_ROTATION = "Lock rotation"
  const val SHUFFLE = "Shuffle queue"

  val all: List<String> = listOf(
    PICTURE_IN_PICTURE,
    VIDEO_ASPECT_RATIO,
    PREVIOUS_FILE,
    NEXT_FILE,
    SKIP_BACK_10,
    SKIP_FORWARD_10,
    AUTO_ROTATE,
    LOCK_ROTATION,
    SHUFFLE,
  )
}
