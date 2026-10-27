package live.mehiz.mpvkt.preferences

import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import live.mehiz.mpvkt.preferences.preference.getEnum
import live.mehiz.mpvkt.ui.player.PlayerOrientation
import live.mehiz.mpvkt.ui.player.RotationMode
import live.mehiz.mpvkt.ui.player.VideoAspect

class PlayerPreferences(
  preferenceStore: PreferenceStore
) {
  val orientation = preferenceStore.getEnum("player_orientation", PlayerOrientation.SensorLandscape)

  /** Auto-rotate toggle state: [RotationMode.Auto] follows the sensors. */
  val rotationMode = preferenceStore.getEnum("player_rotation_mode", RotationMode.Auto)

  /** Orientation to hold while [rotationMode] is Locked; -1 means "not locked yet". */
  val lockedOrientation = preferenceStore.getInt("player_locked_orientation", -1)

  /** Play the next file in the folder when one ends. */
  val autoPlayNext = preferenceStore.getBoolean("player_auto_play_next", true)

  /** Keep going after the last file in the folder. */
  val loopFolder = preferenceStore.getBoolean("player_loop_folder", false)

  /** Show the ±10 s skip buttons beside Play in the centre row. */
  val showSkipButtons = preferenceStore.getBoolean("player_show_skip_buttons", false)
  val invertDuration = preferenceStore.getBoolean("invert_duration")
  val drawOverDisplayCutout = preferenceStore.getBoolean("draw_over_cutout", true)

  val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
  val horizontalSeekGesture = preferenceStore.getBoolean("horizontal_seek_gesture", true)
  val showSeekBarWhenSeeking = preferenceStore.getBoolean("show_seekbar_when_seeking")
  val preciseSeeking = preferenceStore.getBoolean("precise_seeking")
  val showDoubleTapOvals = preferenceStore.getBoolean("show_double_tap_ovals", true)
  val showSeekIcon = preferenceStore.getBoolean("show_seek_icons", true)
  val showSeekTimeWhileSeeking = preferenceStore.getBoolean("show_seek_time_while_seeking", true)

  val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
  val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)

  val videoAspect = preferenceStore.getEnum("video_aspect", VideoAspect.Fit)
  val currentChaptersIndicator = preferenceStore.getBoolean("show_video_chapter_indicator", true)
  val showChaptersButton = preferenceStore.getBoolean("show_video_chapters_button")

  val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
  val speedPresets = preferenceStore.getStringSet(
    "default_speed_presets",
    setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0")
  )
  val displayVolumeAsPercentage = preferenceStore.getBoolean("display_volume_as_percentage", true)
  val swapVolumeAndBrightness = preferenceStore.getBoolean("display_volume_on_right")
  val showLoadingCircle = preferenceStore.getBoolean("show_loading_circle", true)
  val savePositionOnQuit = preferenceStore.getBoolean("save_position", true)

  val automaticallyEnterPip = preferenceStore.getBoolean("automatic_pip")
  val automaticBackgroundPlayback = preferenceStore.getBoolean("automatic_background_playback")
  val closeAfterReachingEndOfVideo = preferenceStore.getBoolean("close_after_eof")

  val rememberBrightness = preferenceStore.getBoolean("remember_rightness")

  /** Keep the volume level between sessions instead of resetting to mpv's default. */
  val rememberVolume = preferenceStore.getBoolean("remember_volume", true)
  val lastVolume = preferenceStore.getInt("last_volume", 100)
  val defaultBrightness = preferenceStore.getFloat("default_brightness", -1f)

  val allowGesturesInPanels = preferenceStore.getBoolean("allow_gestures_in_panels")
  val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar")
  val reduceMotion = preferenceStore.getBoolean("reduce_motion")
  val playerTimeToDisappear = preferenceStore.getInt("player_time_to_disappear", 4000)

  val panelTransparency = preferenceStore.getFloat("panel_transparency", 0.6f)

  val primaryCustomButtonId = preferenceStore.getInt("player_custom_button_id", 0)
}
