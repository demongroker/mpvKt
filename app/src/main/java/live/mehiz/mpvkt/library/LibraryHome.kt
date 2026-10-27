package live.mehiz.mpvkt.library

import android.Manifest
import android.provider.MediaStore
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState as collectFlowAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.ui.player.PlayerActivity
import org.koin.compose.koinInject

private const val VIDEO_ASPECT = 16f / 9f
private const val FOLDERS_PER_PAGE = 12
private const val CONTINUE_FOLDER = "Continue watching"
private const val LOCK_TAG = "VelaLock"

/**
 * The library is the home screen: folders first, an expandable grid inside each
 * one, and the "open something else" entries behind the + button.
 */
@Suppress("LongMethod")
@Composable
fun LibraryHome(
  onPlayVideo: (LibraryVideo, PlaybackQueue?) -> Unit,
  onOpenUrl: () -> Unit,
  onOpenFile: () -> Unit,
  onBrowseFolder: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val repository = koinInject<MediaStoreLibraryRepository>()
  val exclusionSource = koinInject<LibraryExclusionSource>()
  val preferences = koinInject<LibraryPreferences>()
  val thumbnails = koinInject<ThumbnailCache>()
  val lockPreferences = koinInject<LockPreferences>()
  val lockSession = koinInject<LockSession>()

  val permission = if (Build.VERSION.SDK_INT >= 33) {
    Manifest.permission.READ_MEDIA_VIDEO
  } else {
    Manifest.permission.READ_EXTERNAL_STORAGE
  }
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { ok -> granted = ok }
  LaunchedEffect(granted) {
    if (!granted) permissionLauncher.launch(permission)
  }

  var videos by remember { mutableStateOf<List<LibraryVideo>>(emptyList()) }
  var fromSource by remember { mutableStateOf(LibraryExclusions()) }
  var loading by remember { mutableStateOf(true) }
  var query by remember { mutableStateOf("") }
  var menuOpen by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf(setOf<String>()) }
  var loadedFolders by remember { mutableIntStateOf(FOLDERS_PER_PAGE) }
  val scope = rememberCoroutineScope()
  var pendingDelete by remember { mutableStateOf<LibraryVideo?>(null) }
  var confirmDelete by remember { mutableStateOf(false) }
  var pendingLockFolder by remember { mutableStateOf<String?>(null) }
  var showSetPin by remember { mutableStateOf(false) }
  var showUnlockPin by remember { mutableStateOf(false) }
  val lockedFolders by lockPreferences.lockedFolders.collectAsState()
  val unlockedLocked by lockSession.unlockedFolders.collectFlowAsState()

  // Deleting is the system's job: MediaStore shows its own dialog, and only a
  // RESULT_OK from it may touch the library list or the resume row.
  val deleteLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val target = pendingDelete
    pendingDelete = null
    if (target == null) return@rememberLauncherForActivityResult
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      scope.launch {
        videos = LibraryDeletion.applySuccess(videos, target.fileKey) { key -> repository.clearResume(key) }
      }
    } else {
      videos = LibraryDeletion.applyCancel(videos, target.fileKey)
    }
  }

  val sortName by preferences.sortOrder.collectAsState()
  val pinnedFolders by preferences.pinnedFolders.collectAsState()
  val hiddenFolders by preferences.hiddenFolders.collectAsState()
  val shuffledFolders by preferences.shuffledFolders.collectAsState()
  val sort = LibrarySort.fromName(sortName)

  LaunchedEffect(granted) {
    if (!granted) return@LaunchedEffect
    loading = true
    fromSource = exclusionSource.exclusions()
    videos = repository.loadVideos()
    loading = false
  }

  // The exclusion source is the single authority for locking. It has to be re-asked
  // whenever a folder is unlocked or re-locked: fromSource was captured at load time,
  // so leaving it stale kept hiding a folder the owner had just opened.
  suspend fun refreshExclusions() {
    fromSource = exclusionSource.exclusions()
    Log.d(LOCK_TAG, "exclusions refreshed: source=${fromSource.folders.size} open=${unlockedLocked.size}")
  }

  LaunchedEffect(granted, unlockedLocked, lockedFolders) {
    if (!granted) return@LaunchedEffect
    refreshExclusions()
  }

  val exclusions = fromSource + LibraryExclusions(folders = hiddenFolders)
  val folders = remember(videos, exclusions, pinnedFolders, query, sort) {
    LibraryLogic.groupByFolder(
      videos = videos,
      exclusions = exclusions,
      pinnedFolders = pinnedFolders,
      query = query,
      sort = sort,
    )
  }
  val shownFolders = folders.take(loadedFolders)

  // "Continue watching" is built from the excluded-aware list, otherwise a file inside a
  // locked folder would leak into the strip at the top of the library. It also only makes
  // sense on the unfiltered view, so a search hides it.
  val visibleVideos = remember(videos, exclusions) { LibraryLogic.applyExclusions(videos, exclusions) }
  val continueWatching = remember(visibleVideos, query) {
    if (query.isBlank()) ContinueWatching.pick(visibleVideos) else emptyList()
  }
  val continueQueue = remember(continueWatching) {
    QueueLogic.queueFor(
      videos = continueWatching,
      folder = CONTINUE_FOLDER,
      shuffled = false,
      seed = 0L,
    )
  }

  /** The queue for a folder: its files in the library's own order, unless shuffled. */
  fun queueForFolder(folder: LibraryFolder): PlaybackQueue = QueueLogic.queueFor(
    videos = folder.videos,
    folder = folder.name,
    shuffled = folder.name in shuffledFolders,
    seed = preferences.shuffleSeed(folder.name).get(),
  )

  if (!granted) {
    PermissionPrompt(onRequest = { permissionLauncher.launch(permission) }, modifier = modifier)
    return
  }

  Box(modifier = modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 160.dp),
      contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        LibrarySearchRow(
          query = query,
          onQueryChange = { query = it },
          sort = sort,
          onSortChange = { preferences.sortOrder.set(it.name) },
        )
      }
      if (continueWatching.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "continue-watching") {
          ContinueWatchingRow(
            videos = continueWatching,
            context = context,
            thumbnails = thumbnails,
            onPlay = { video -> onPlayVideo(video, continueQueue) },
            onDelete = { pendingDelete = it },
          )
        }
      }
      if (loading) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Scanning…", Modifier.padding(16.dp)) }
      } else if (folders.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Text("No videos found on this device", Modifier.padding(16.dp))
        }
      }
      shownFolders.forEach { folder ->
        item(span = { GridItemSpan(maxLineSpan) }, key = "folder-${folder.name}") {
          FolderHeader(
            folder = folder,
            expanded = folder.name in expanded,
            context = context,
            thumbnails = thumbnails,
            onToggle = {
              expanded = if (folder.name in expanded) expanded - folder.name else expanded + folder.name
            },
            onPin = {
              preferences.pinnedFolders.set(
                if (folder.name in pinnedFolders) pinnedFolders - folder.name else pinnedFolders + folder.name,
              )
            },
            onHide = { preferences.hiddenFolders.set(hiddenFolders + folder.name) },
            onLock = {
              if (lockPreferences.hasPin()) {
                lockPreferences.lockedFolders.set(lockedFolders + folder.name)
                Log.d(LOCK_TAG, "locked ${folder.name}; total=${lockPreferences.lockedFolders.get().size}")
                scope.launch { refreshExclusions() }
              } else {
                pendingLockFolder = folder.name
                showSetPin = true
              }
            },
          )
        }
        if (folder.name in expanded) {
          items(folder.videos, key = { it.fileKey }) { video ->
            VideoCard(
              video = video,
              context = context,
              thumbnails = thumbnails,
              onPlay = { onPlayVideo(video, queueForFolder(folder)) },
              onDelete = { pendingDelete = video },
            )
          }
        }
      }
      if (folders.size > shownFolders.size) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          TextButton(onClick = { loadedFolders += FOLDERS_PER_PAGE }) {
            Text("Load more folders (${folders.size - shownFolders.size} left)")
          }
        }
      }
    }

    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
      FloatingActionButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Add, null) }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text("Open a link") },
          onClick = {
            menuOpen = false
            onOpenUrl()
          },
        )
        DropdownMenuItem(
          text = { Text("Pick a file") },
          onClick = {
            menuOpen = false
            onOpenFile()
          },
        )
        DropdownMenuItem(
          text = { Text("Browse folders") },
          onClick = {
            menuOpen = false
            onBrowseFolder()
          },
        )
        if (lockedFolders.isNotEmpty()) {
          DropdownMenuItem(
            text = { Text("Unlock locked folders (${lockedFolders.size})") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            onClick = {
              menuOpen = false
              Log.d(LOCK_TAG, "unlock requested: locked=${lockedFolders.size} open=${unlockedLocked.size}")
              showUnlockPin = true
            },
          )
          if (unlockedLocked.isNotEmpty()) {
            DropdownMenuItem(
              text = { Text("Lock them again (${lockedFolders.size})") },
              leadingIcon = { Icon(Icons.Default.LockOpen, null) },
              onClick = {
                menuOpen = false
                Log.d(LOCK_TAG, "relock requested: open=${unlockedLocked.size}")
                lockSession.lockAll()
                scope.launch { refreshExclusions() }
              },
            )
          }
        }
        if (hiddenFolders.isNotEmpty()) {
          DropdownMenuItem(
            text = { Text("Unhide all folders (${hiddenFolders.size})") },
            onClick = {
              menuOpen = false
              preferences.hiddenFolders.set(emptySet())
            },
          )
        }
      }
    }
  }

  // Locked folders re-lock themselves while the app is open.
  LaunchedEffect(lockedFolders) {
    while (true) {
      lockSession.enforceTimeout()
      kotlinx.coroutines.delay(30_000)
    }
  }

  if (showSetPin) {
    SetPinDialog(
      onDismiss = {
        showSetPin = false
        pendingLockFolder = null
      },
      onSave = { pin ->
        lockPreferences.setPin(pin)
        pendingLockFolder?.let { name ->
          lockPreferences.lockedFolders.set(lockedFolders + name)
        }
        pendingLockFolder = null
      },
    )
  }

  if (showUnlockPin) {
    PinPromptDialog(
      title = "Unlock locked folders",
      subtitle = "${lockedFolders.size} folder(s) are hidden.",
      onDismiss = { showUnlockPin = false },
      onSubmit = { pin ->
        val ok = LibraryLock.verify(
          pin = pin,
          salt = lockPreferences.pinSalt.get(),
          expected = lockPreferences.pinHash.get(),
        )
        Log.d(
          LOCK_TAG,
          "pin result=$ok hashSet=${lockPreferences.pinHash.get().isNotEmpty()} " +
            "locked=${lockPreferences.lockedFolders.get().size}",
        )
        if (ok) {
          lockSession.unlockAll()
          Log.d(LOCK_TAG, "unlocked all: open=${lockSession.unlockedFolders.value.size}")
          scope.launch { refreshExclusions() }
        }
        ok
      },
    )
  }

  pendingDelete?.let { target ->
    DeleteConfirmDialog(
      video = target,
      onConfirm = { confirmDelete = true },
      onDismiss = { pendingDelete = null },
    )
  }

  // Our own confirmation runs first; the system dialog only appears afterwards.
  LaunchedEffect(confirmDelete, pendingDelete) {
    val target = pendingDelete ?: return@LaunchedEffect
    if (!confirmDelete) return@LaunchedEffect
    confirmDelete = false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      Toast.makeText(context, "Deleting needs Android 11 or newer", Toast.LENGTH_LONG).show()
      pendingDelete = null
      return@LaunchedEffect
    }
    val launched = runCatching {
      val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(target.uri.toUri()))
      deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }.isSuccess
    if (!launched) {
      Toast.makeText(context, "Couldn't ask the system to delete that file", Toast.LENGTH_LONG).show()
      pendingDelete = null
    }
  }
}

/** The in-app confirmation that sits in front of the system's own dialog. */
@Composable
private fun DeleteConfirmDialog(video: LibraryVideo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete permanently?") },
    text = { Text("Delete “${video.name}”? This removes the file from your phone. It cannot be undone.") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Delete", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Default.VideoLibrary, null)
    Text("Vela needs access to your videos to build the library.")
    Button(onClick = onRequest) { Text("Allow access") }
  }
}

@Composable
private fun LibrarySearchRow(
  query: String,
  onQueryChange: (String) -> Unit,
  sort: LibrarySort,
  onSortChange: (LibrarySort) -> Unit,
) {
  var sortMenu by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      singleLine = true,
      leadingIcon = { Icon(Icons.Default.Search, null) },
      label = { Text("Search folders and videos") },
      modifier = Modifier.weight(1f),
    )
    Box {
      IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, null) }
      DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
        LibrarySort.entries.forEach { option ->
          DropdownMenuItem(
            text = { Text(if (option == sort) "✓ ${option.name}" else option.name) },
            onClick = {
              sortMenu = false
              onSortChange(option)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun FolderHeader(
  folder: LibraryFolder,
  expanded: Boolean,
  context: Context,
  thumbnails: ThumbnailCache,
  onToggle: () -> Unit,
  onPin: () -> Unit,
  onHide: () -> Unit,
  onLock: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val thumb by rememberThumb(folder.cover, context, thumbnails)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = thumb
      if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } else {
        Icon(Icons.Default.Folder, null)
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (folder.pinned) Icon(Icons.Default.PushPin, null, modifier = Modifier.size(14.dp))
        Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Text("${folder.count} videos", style = MaterialTheme.typography.bodySmall)
    }
    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(
        text = { Text(if (folder.pinned) "Unpin folder" else "Pin to top") },
        onClick = {
          menu = false
          onPin()
        },
      )
      DropdownMenuItem(
        text = { Text("Hide folder") },
        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
        onClick = {
          menu = false
          onHide()
        },
      )
      DropdownMenuItem(
        text = { Text("Lock folder (PIN)") },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        onClick = {
          menu = false
          onLock()
        },
      )
    }
  }
}

@Composable
private fun VideoCard(
  video: LibraryVideo,
  context: Context,
  thumbnails: ThumbnailCache,
  onPlay: () -> Unit,
  onDelete: () -> Unit,
) {
  val thumb by rememberThumb(video, context, thumbnails)
  var menu by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .combinedClickable(onClick = onPlay, onLongClick = { menu = true }),
  ) {
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(
        text = { Text("Delete…") },
        leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
        onClick = {
          menu = false
          onDelete()
        },
      )
    }
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = thumb
      if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } else {
        Icon(Icons.Default.VideoLibrary, null)
      }
      Text(
        text = formatDuration(video.durationMs),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(6.dp)
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
          .padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
    val progress = LibraryLogic.progressFraction(video.positionMs, video.durationMs)
    if (progress > 0f) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(3.dp),
      )
    }
    Text(
      text = video.name,
      style = MaterialTheme.typography.bodySmall,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(8.dp),
    )
  }
}

@Composable
private fun ContinueWatchingRow(
  videos: List<LibraryVideo>,
  context: Context,
  thumbnails: ThumbnailCache,
  onPlay: (LibraryVideo) -> Unit,
  onDelete: (LibraryVideo) -> Unit,
) {
  Column(modifier = Modifier.padding(bottom = 8.dp)) {
    Text(
      text = "Continue watching",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listItems(videos, key = { it.fileKey }) { video ->
        Box(modifier = Modifier.width(160.dp)) {
          VideoCard(
            video = video,
            context = context,
            thumbnails = thumbnails,
            onPlay = { onPlay(video) },
            onDelete = { onDelete(video) },
          )
        }
      }
    }
  }
}

@Composable
private fun rememberThumb(video: LibraryVideo?, context: Context, thumbnails: ThumbnailCache) =
  produceState<ImageBitmap?>(initialValue = null, video?.fileKey) {
    value = if (video == null) null else withContext(Dispatchers.IO) { thumbnails.load(context, video) }
  }

private fun formatDuration(ms: Long): String {
  if (ms <= 0) return "--:--"
  val totalSeconds = ms / 1000
  val h = totalSeconds / 3600
  val m = (totalSeconds % 3600) / 60
  val s = totalSeconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Starts playback with the identity the resume table expects, and hands the player
 * the folder queue so "next", shuffle and delete can work from inside playback.
 */
fun playLibraryVideo(
  context: Context,
  video: LibraryVideo,
  queue: PlaybackQueue? = null,
) {
  val intent = Intent(Intent.ACTION_VIEW, video.uri.toUri())
  intent.setClass(context, PlayerActivity::class.java)
  intent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, video.mediaId)
  intent.putExtra(PlayerActivity.EXTRA_MEDIA_PATH, video.path.ifBlank { video.uri })
  queue?.let { intent.putExtra(PlayerActivity.EXTRA_QUEUE, it.toJson()) }
  context.startActivity(intent)
}
