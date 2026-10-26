package live.mehiz.mpvkt.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsUiTest {

  @Test
  fun theIconOnlyButtonsHaveAccessibilityLabels() {
    assertTrue(PlayerControlLabels.PICTURE_IN_PICTURE.isNotBlank())
    assertTrue(PlayerControlLabels.VIDEO_ASPECT_RATIO.isNotBlank())
    assertEquals("Picture in picture", PlayerControlLabels.PICTURE_IN_PICTURE)
    assertEquals("Video aspect ratio", PlayerControlLabels.VIDEO_ASPECT_RATIO)
  }

  @Test
  fun everyLabelIsDistinctAndNonBlank() {
    assertTrue(PlayerControlLabels.all.none { it.isBlank() })
    assertEquals(PlayerControlLabels.all.size, PlayerControlLabels.all.toSet().size)
  }

  @Test
  fun skipButtonsAreOffByDefaultAndFollowThePreference() {
    assertFalse(PlayerControlVisibility.showSkipButtons(preference = false))
    assertTrue(PlayerControlVisibility.showSkipButtons(preference = true))
  }

  @Test
  fun previousAndNextFollowTheQueue() {
    assertFalse(PlayerControlVisibility.showPrevious(hasPreviousInQueue = false))
    assertTrue(PlayerControlVisibility.showPrevious(hasPreviousInQueue = true))
    assertFalse(PlayerControlVisibility.showNext(hasNextInQueue = false))
    assertTrue(PlayerControlVisibility.showNext(hasNextInQueue = true))
  }

  @Test
  fun theMoreSheetRowsKeepAWorkableMinimumWidth() {
    assertTrue(MoreSheetLayout.MIN_ROW_WIDTH_DP >= MoreSheetLayout.MIN_LABEL_WIDTH_DP)
    // The longest action label must still fit a word per line at that width.
    assertTrue(MoreSheetLayout.labelFitsWordBoundaries("Delete file"))
    assertTrue(MoreSheetLayout.labelFitsWordBoundaries("Sleep timer"))
    assertTrue(MoreSheetLayout.MAX_LABEL_LINES >= 1)
  }
}
