package live.mehiz.mpvkt.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.subtitles.SubtitleHit
import live.mehiz.mpvkt.subtitles.SubtitleLogic
import live.mehiz.mpvkt.subtitles.SubtitleSearchState
import java.util.Locale

/**
 * "Download subtitles" sheet: searches OpenSubtitles with the current file name
 * and hands the picked result back for mpv to load beside the video.
 */
@Composable
fun SubtitleSearchSheet(
  state: SubtitleSearchState,
  onRetry: () -> Unit,
  onPick: (SubtitleHit) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  PlayerSheet(onDismissRequest) {
    Column(modifier.fillMaxWidth().heightIn(max = 420.dp)) {
      Text(
        text = "Download subtitles",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      )
      when (state) {
        is SubtitleSearchState.Idle -> BusyRow("Looking for “${state.query}”…")
        is SubtitleSearchState.Searching -> BusyRow("Looking for “${state.query}”…")
        is SubtitleSearchState.Downloading -> BusyRow("Downloading ${state.fileName}…")
        is SubtitleSearchState.Failed -> FailedRow(state.reason, onRetry)
        is SubtitleSearchState.Results -> ResultsList(state, onPick)
      }
    }
  }
}

@Composable
private fun BusyRow(message: String) {
  Row(
    modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun FailedRow(reason: String, onRetry: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(reason, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onRetry) { Text("Try again") }
  }
}

@Composable
private fun ResultsList(state: SubtitleSearchState.Results, onPick: (SubtitleHit) -> Unit) {
  Text(
    text = "${state.hits.size} found for “${state.query}”",
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
  )
  LazyColumn {
    items(state.hits, key = { it.id }) { hit ->
      SubtitleHitRow(hit) { onPick(hit) }
    }
  }
}

@Composable
private fun SubtitleHitRow(hit: SubtitleHit, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(
      text = hit.fileName,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = "${SubtitleLogic.languageLabel(hit.language)} · " +
        "${String.format(Locale.US, "%,d", hit.downloads)} downloads · ${hit.format}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
