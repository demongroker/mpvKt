package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import android.widget.Toast
import live.mehiz.mpvkt.BuildConfig
import live.mehiz.mpvkt.ui.update.ChangelogScreen
import live.mehiz.mpvkt.update.GhRelease
import live.mehiz.mpvkt.update.Updater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.app_name)) },
          actions = {
            IconButton(onClick = { backstack.add(VideoLibraryScreen) }) {
              Icon(Icons.Default.VideoLibrary, null)
            }
            IconButton(onClick = { backstack.add(PreferencesScreen) }) {
              Icon(Icons.Default.Settings, null)
            }
          },
          navigationIcon = {
            Image(
              painter = painterResource(id = R.drawable.ic_launcher_foreground),
              contentDescription = "app_logo",
            )
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        val uri = rememberTextFieldState()
        var update by remember { mutableStateOf<GhRelease?>(null) }
        var downloading by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
          val latest = Updater.latest()
          if (latest != null && Updater.releaseSha(latest) != BuildConfig.GIT_SHA) {
            update = latest
          }
        }
        update?.let { rel ->
          Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
              Text(
                text = "Update available — ${rel.tag}",
                style = MaterialTheme.typography.titleSmall,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { backstack.add(ChangelogScreen) }) { Text("What's new") }
                val apk = Updater.downloadedFile(context, rel)
                if (apk.exists() && apk.length() > 0) {
                  Button(onClick = { Updater.install(context, apk) }) { Text("Install") }
                } else {
                  Button(
                    enabled = !downloading,
                    onClick = {
                      downloading = Updater.startDownload(context, rel) != null
                      Toast.makeText(
                        context,
                        "Downloading in background — it resumes if the connection drops",
                        Toast.LENGTH_LONG,
                      ).show()
                    },
                  ) { Text(if (downloading) "Downloading…" else "Download") }
                }
              }
            }
          }
        }
        var isUrlValid by remember { mutableStateOf(true) }
        LaunchedEffect(uri.text) {
          isUrlValid = uri.text.isNotEmpty() || isURLValid(uri.text.toString())
        }
        OutlinedTextField(
          state = uri,
          label = { Text(stringResource(R.string.home_url_input_label)) },
          supportingText = {
            Text(if (isUrlValid) "" else stringResource(R.string.home_invalid_protocol))
          },
          trailingIcon = {
            if (!isUrlValid) Icon(Icons.Filled.Info, null)
          },
          isError = !isUrlValid
        )
        Button(
          onClick = { playFile(uri.text.toString(), context) },
          enabled = uri.text.isNotBlank() && isUrlValid,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Link, null)
            Text(text = stringResource(R.string.home_open_url))
          }
        }
        val documentPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocument(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          playFile(it.toString(), context)
        }
        OutlinedButton(
          onClick = { documentPicker.launch(arrayOf("*/*")) },
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FileOpen, null)
            Text(text = stringResource(R.string.home_pick_file))
          }
        }
        val fileManager = FileManager(context)
        val directoryPicker = rememberLauncherForActivityResult(
          ActivityResultContracts.OpenDocumentTree(),
        ) {
          if (it == null) return@rememberLauncherForActivityResult
          backstack.add(FilePickerScreen(fileManager.fromUri(it)!!.getFullPath()))
        }
        OutlinedButton(onClick = { directoryPicker.launch(null) }) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.FolderOpen, null)
            Text(text = stringResource(R.string.home_open_file_picker))
          }
        }
      }
    }
  }

  // Basically a copy of:
  // https://github.com/mpv-android/mpv-android/blob/32cbff3cedea73b4616b34542cb95bf1d00504cc/app/src/main/java/is/xyz/mpv/Utils.kt#L406
  private fun isURLValid(url: String): Boolean {
    val uri = url.toUri()
    return uri.isHierarchical && !uri.isRelative &&
      !(uri.host.isNullOrBlank() && uri.path.isNullOrBlank()) &&
      PROTOCOLS.contains(uri.scheme)
  }

  fun playFile(
    filepath: String,
    context: Context,
  ) {
    val i = Intent(Intent.ACTION_VIEW, filepath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    context.startActivity(i)
  }
}
