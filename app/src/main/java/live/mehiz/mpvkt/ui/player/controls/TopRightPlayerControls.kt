package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.RotationMode
import live.mehiz.mpvkt.ui.player.controls.components.ControlsButton

@Composable
fun TopRightPlayerControls(
  // decoder
  decoder: Decoder,
  onDecoderClick: () -> Unit,
  onDecoderLongClick: () -> Unit,
  // chapters
  isChaptersVisible: Boolean,
  onChaptersClick: () -> Unit,
  // subtitles
  onSubtitlesClick: () -> Unit,
  onSubtitlesLongClick: () -> Unit,
  // audio
  onAudioClick: () -> Unit,
  onAudioLongClick: () -> Unit,
  // more
  onMoreClick: () -> Unit,
  onMoreLongClick: () -> Unit,
  // queue and rotation
  isShuffled: Boolean = false,
  onShuffleClick: () -> Unit = {},
  rotationMode: RotationMode = RotationMode.Auto,
  onRotateClick: () -> Unit = {},

  modifier: Modifier = Modifier
) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    ControlsButton(
      decoder.title,
      onClick = onDecoderClick,
      onLongClick = onDecoderLongClick,
    )
    if (isChaptersVisible) {
      ControlsButton(
        Icons.Default.Bookmarks,
        onClick = onChaptersClick,
      )
    }
    ControlsButton(
      Icons.Default.Subtitles,
      onClick = onSubtitlesClick,
      onLongClick = onSubtitlesLongClick,
    )
    ControlsButton(
      Icons.Default.Audiotrack,
      onClick = onAudioClick,
      onLongClick = onAudioLongClick,
    )
    ControlsButton(
      if (isShuffled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
      onClick = onShuffleClick,
    )
    // The icon is the state: screen-rotation means auto, the lock means held.
    ControlsButton(
      if (rotationMode == RotationMode.Locked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
      onClick = onRotateClick,
    )
    ControlsButton(
      Icons.Default.MoreVert,
      onClick = onMoreClick,
      onLongClick = onMoreLongClick,
    )
  }
}
