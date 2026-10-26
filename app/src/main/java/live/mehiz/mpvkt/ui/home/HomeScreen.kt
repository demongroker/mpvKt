package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.BuildConfig
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.library.LibraryHome
import live.mehiz.mpvkt.library.playLibraryVideo
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.update.ChangelogScreen
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.update.GhRelease
import live.mehiz.mpvkt.update.UpdateLogic
import live.mehiz.mpvkt.update.Updater
import org.koin.compose.koinInject

/** Update banner: verify the download before handing it to the installer. */
@Composable
private fun UpdateCard(
  release: GhRelease,
  context: Context,
  onChangelog: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var downloading by remember { mutableStateOf(false) }
  var checking by remember { mutableStateOf(false) }
  val apk = Updater.downloadedFile(context, release)
  val hasFile = apk.exists() && apk.length() > 0

  fun restart() {
    Updater.deleteDownload(context, release)
    downloading = Updater.startDownload(context, release) != null
  }

  Card(modifier = modifier.fillMaxWidth().padding(12.dp)) {
    Column(Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Update available — ${release.tag}",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Clear, null) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onChangelog) { Text("What's new") }
        if (!hasFile) {
          Button(
            enabled = !downloading,
            onClick = {
              downloading = Updater.startDownload(context, release) != null
              Toast.makeText(
                context,
                "Downloading in background — it resumes if the connection drops",
                Toast.LENGTH_LONG,
              ).show()
            },
          ) { Text(if (downloading) "Downloading…" else "Download") }
        } else {
          Button(
            enabled = !checking,
            onClick = {
              checking = true
              scope.launch {
                val verdict = Updater.verify(apk, release)
                if (verdict == Updater.Verify.OK) {
                  Updater.install(context, apk)
                } else {
                  val why = when (verdict) {
                    Updater.Verify.TRUNCATED -> "Download was cut short — fetching it again"
                    Updater.Verify.HASH_MISMATCH -> "Download was corrupted — fetching it again"
                    else -> "Couldn't confirm the download — fetching it again"
                  }
                  Toast.makeText(context, why, Toast.LENGTH_LONG).show()
                  restart()
                }
                checking = false
              }
            },
          ) { Text(if (checking) "Checking…" else "Install") }
        }
      }
    }
  }
}

/** URL playback lives behind the + button now, so it opens in a dialog. */
@Composable
private fun UrlDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
  val url = rememberTextFieldState()
  val text = url.text.toString()
  val showError = UrlValidation.showError(text, PROTOCOLS.toList())
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.home_open_url)) },
    text = {
      OutlinedTextField(
        state = url,
        label = { Text(stringResource(R.string.home_url_input_label)) },
        supportingText = {
          Text(if (showError) stringResource(R.string.home_invalid_protocol) else "")
        },
        trailingIcon = { if (showError) Icon(Icons.Filled.Info, null) },
        isError = showError,
      )
    },
    confirmButton = {
      TextButton(
        enabled = UrlValidation.isPlayable(text, PROTOCOLS.toList()),
        onClick = { onPlay(text) },
      ) { Text(stringResource(R.string.home_open_url)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val advancedPreferences = koinInject<AdvancedPreferences>()
    var update by remember { mutableStateOf<GhRelease?>(null) }
    var urlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      // Re-checked on every launch; no "update available" flag is cached, so a
      // stale answer can never keep the banner alive after an update.
      val latest = Updater.latest()
      val available = latest != null && UpdateLogic.isUpdateAvailable(
        remoteTag = latest.tag,
        installedVersionName = BuildConfig.VERSION_NAME,
        installedTag = BuildConfig.GIT_TAG,
        installedVersionCode = BuildConfig.VERSION_CODE,
      )
      update = if (available) latest else null
    }
    val dismissedTag by advancedPreferences.dismissedUpdateTag.collectAsState()

    val documentPicker = rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      playFile(uri.toString(), context)
    }
    val fileManager = FileManager(context)
    val directoryPicker = rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      backstack.add(FilePickerScreen(fileManager.fromUri(uri)!!.getFullPath()))
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.app_name)) },
          actions = {
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
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LibraryHome(
          onPlayVideo = { video -> playLibraryVideo(context, video) },
          onOpenUrl = { urlDialog = true },
          onOpenFile = { documentPicker.launch(arrayOf("*/*")) },
          onBrowseFolder = { directoryPicker.launch(null) },
        )
        update?.let { rel ->
          if (dismissedTag != rel.tag) {
            UpdateCard(
              release = rel,
              context = context,
              onChangelog = { backstack.add(ChangelogScreen) },
              onDismiss = { advancedPreferences.dismissedUpdateTag.set(rel.tag) },
              modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }

    if (urlDialog) {
      UrlDialog(
        onDismiss = { urlDialog = false },
        onPlay = { url ->
          urlDialog = false
          playFile(url, context)
        },
      )
    }
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
