package live.mehiz.mpvkt.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.update.GhRelease
import live.mehiz.mpvkt.update.Updater

@Serializable
object ChangelogScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    var releases by remember { mutableStateOf<List<GhRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
      releases = Updater.recent(15)
      loading = false
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("What's new") },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      if (loading) {
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
          Text("Loading releases…")
        }
        return@Scaffold
      }
      if (releases.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
          Text("Could not reach GitHub. Check your connection and try again.")
        }
        return@Scaffold
      }
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(releases) { release ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(release.tag, style = MaterialTheme.typography.titleMedium)
              Text(
                release.publishedAt?.take(10).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              val body = release.body?.replace(Regex("(?m)^sha:[0-9a-fA-F]{40}$"), "")?.trim()
              if (!body.isNullOrBlank()) {
                Text(body, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }
    }
  }
}
