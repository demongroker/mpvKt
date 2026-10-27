package live.mehiz.mpvkt.library

/**
 * Remembering the player's volume across sessions. mpv's own default is 100; the
 * preference decides whether the stored value is applied at all, so turning the
 * option off must leave mpv alone rather than force a value.
 */
object VolumeMemory {

  const val DEFAULT_VOLUME = 100

  /** mpv's `--volume-max` default; anything stored outside this is nonsense. */
  const val MAX_VOLUME = 130

  fun clamp(volume: Int): Int = volume.coerceIn(0, MAX_VOLUME)

  /** What to persist when playback stops; always a legal volume. */
  fun toStore(volume: Int?): Int = clamp(volume ?: DEFAULT_VOLUME)

  /** What to apply on start: null means "don't touch mpv". */
  fun onStart(remember: Boolean, stored: Int?): Int? =
    if (remember && stored != null) clamp(stored) else null

  /** Human-readable percentage used by the volume slider. */
  fun percent(volume: Int): Int = clamp(volume)
}
