package live.mehiz.mpvkt.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shows exactly which folders are locked and lets the owner take one back without
 * unlocking the rest. Gates itself: it is only reached after the PIN is accepted.
 */
@Composable
internal fun LockManagerDialog(
  lockedFolders: Set<String>,
  onRemove: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Locked folders (${lockedFolders.size})") },
    text = {
      if (lockedFolders.isEmpty()) {
        Text("No folder is locked.")
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Remove one to make it visible again.")
          LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            items(lockedFolders.sorted(), key = { it }) { folder ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = folder,
                  modifier = Modifier.weight(1f),
                  maxLines = 2,
                )
                TextButton(onClick = { onRemove(folder) }) { Text("Remove") }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}
