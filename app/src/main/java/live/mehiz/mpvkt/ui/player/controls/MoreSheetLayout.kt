package live.mehiz.mpvkt.ui.player.controls

/**
 * Layout constants for the More sheet.
 *
 * The sheet's action rows used to sit in one horizontal Row beside the title, which
 * squeezed their labels into a per-character column on a narrow portrait screen.
 * Each row now has a minimum width and wraps as a whole label, so the invariants the
 * sheet depends on are stated (and tested) here.
 */
object MoreSheetLayout {
  /** A row never gets narrower than this, whatever the screen or font scale. */
  const val MIN_ROW_WIDTH_DP = 160

  /** Below this a label could not fit a word like "Delete" at large font scales. */
  const val MIN_LABEL_WIDTH_DP = 120

  /** Labels wrap at word boundaries over at most this many lines. */
  const val MAX_LABEL_LINES = 2

  /** True when a label is short enough to render without being split per character. */
  fun labelFitsWordBoundaries(label: String, rowWidthDp: Int = MIN_ROW_WIDTH_DP): Boolean {
    val longestWord = label.split(' ').maxOfOrNull { it.length } ?: 0
    val needed = longestWord * APPROX_DP_PER_CHAR
    return rowWidthDp >= needed.coerceAtMost(MIN_LABEL_WIDTH_DP)
  }

  private const val APPROX_DP_PER_CHAR = 12
}

/** Which optional buttons the player shows, as one place a test can read. */
object PlayerControlVisibility {
  fun showSkipButtons(preference: Boolean): Boolean = preference

  fun showPrevious(hasPreviousInQueue: Boolean): Boolean = hasPreviousInQueue

  fun showNext(hasNextInQueue: Boolean): Boolean = hasNextInQueue
}
