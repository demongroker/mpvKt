package live.mehiz.mpvkt.ui.player

/**
 * How the player decides its orientation while a video is playing.
 *
 * [Auto] follows the device sensors (the existing behaviour of the Orientation
 * preference is untouched); [Locked] holds the orientation the video was in when
 * the rotate button was pressed.
 */
enum class RotationMode {
  Auto,
  Locked,
  ;

  fun toggled(): RotationMode = if (this == Auto) Locked else Auto

  companion object {
    fun fromName(name: String?): RotationMode =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Auto
  }
}
