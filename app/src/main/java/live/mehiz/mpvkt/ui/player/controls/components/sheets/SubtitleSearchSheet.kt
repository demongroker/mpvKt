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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.presentation.components.PlayerSheet
import live.mehiz.mpvkt.subtitles.SubtitleHit
import live.mehiz.mpvkt.subtitles.SubtitleLogic
import live.mehiz.mpvkt.subtitles.SubtitleSearchState
import java.io.File
import java.util.Locale

/**
 * "Download subtitles" sheet: searches OpenSubtitles with the name of the file
 * that is playing and loads the picked result as the selected track.
 */
@Composable
fun SubtitleSearchSheet(
  query: String,
  onFind: suspend (String) -> List<SubtitleHit>,
  onSave: suspend (SubtitleHit, String) -> File?,
  onLoaded: (File) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf<SubtitleSearchState>(SubtitleSearchState.Idle(query)) }
  var attempt by remember { mutableIntStateOf(0) }

  LaunchedEffect(query, attempt) {
    if (query.isBlank()) {
      state = SubtitleSearchState.Failed(query, "Vela doesn't know this file's name — add a subtitle file instead")
      return@LaunchedEffect
    }
    state = SubtitleSearchState.Searching(query)
    val hits = runCatching { onFind(query) }.getOrDefault(emptyList())
    state = if (hits.isEmpty()) {
      SubtitleSearchState.Failed(query, "No subtitles found for “$query”")
    } else {
      SubtitleSearchState.Results(query, hits)
    }
  }

  fun pick(hit: SubtitleHit) {
    scope.launch {
      state = SubtitleSearchState.Downloading(hit.fileName)
      val file = runCatching { onSave(hit, query) }.getOrNull()
      if (file == null) {
        state = SubtitleSearchState.Failed(query, "That subtitle couldn't be downloaded — try another")
      } else {
        onLoaded(file)
        onDismissRequest()
      }
    }
  }

  PlayerSheet(onDismissRequest) {
    Column(modifier.fillMaxWidth().heightIn(max = 420.dp)) {
      Text(
        text = "Download subtitles",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      )
      when (val current = state) {
        is SubtitleSearchState.Idle -> BusyRow("Looking for “${current.query}”…")
        is SubtitleSearchState.Searching -> BusyRow("Looking for “${current.query}”…")
        is SubtitleSearchState.Downloading -> BusyRow("Downloading ${current.fileName}…")
        is SubtitleSearchState.Failed -> FailedRow(current.reason) { attempt++ }
        is SubtitleSearchState.Results -> ResultsList(current, ::pick)
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
