package live.mehiz.mpvkt.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.utils.LocalBackStack

/** Column name of the video's parent folder; a literal so it also works pre-API 29. */
private const val COLUMN_BUCKET = "bucket_display_name"

/** Fallback folder label for videos that MediaStore cannot attribute to a folder. */
private const val OTHER_FOLDER = "Other"

data class DeviceVideo(
  val id: Long,
  val uri: Uri,
  val name: String,
  val durationMs: Long,
  val sizeBytes: Long,
  val folder: String,
)

data class VideoFolder(
  val name: String,
  val videos: List<DeviceVideo>,
)

@Serializable
object VideoLibraryScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_VIDEO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
      )
    }
    val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission(),
    ) { ok -> granted = ok }

    LaunchedEffect(granted) {
      if (!granted) launcher.launch(permission)
    }

    var videos by remember { mutableStateOf<List<DeviceVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openFolder by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(granted) {
      if (!granted) return@LaunchedEffect
      loading = true
      videos = withContext(Dispatchers.IO) { queryVideos(context) }
      loading = false
    }

    val folders = remember(videos) { groupByFolder(videos) }
    val inFolder = openFolder
    val shown = remember(folders, inFolder) {
      if (inFolder == null) emptyList() else folders.firstOrNull { it.name == inFolder }?.videos.orEmpty()
    }

    BackHandler(enabled = inFolder != null) { openFolder = null }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(inFolder ?: "Videos") },
          navigationIcon = {
            IconButton(
              onClick = {
                if (inFolder != null) openFolder = null else backstack.removeLastOrNull()
              },
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
          },
        )
      },
    ) { padding ->
      when {
        !granted -> Column(
          modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(Icons.Default.VideoLibrary, null)
          Text("Vela needs access to your videos to show them here.")
          Button(onClick = { launcher.launch(permission) }) { Text("Allow access") }
        }

        loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
          Text("Scanning…")
        }

        videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
          Text("No videos found on this device")
        }

        inFolder == null -> FolderGrid(
          folders = folders,
          onOpen = { name -> openFolder = name },
          modifier = Modifier.padding(padding),
        )

        else -> VideoGrid(
          videos = shown,
          onPlay = { video -> HomeScreen.playFile(video.uri.toString(), context) },
          modifier = Modifier.padding(padding),
        )
      }
    }
  }

  private fun queryVideos(context: Context): List<DeviceVideo> {
    val collection = if (Build.VERSION.SDK_INT >= 29) {
      MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DISPLAY_NAME,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.SIZE,
      COLUMN_BUCKET,
    )
    val out = mutableListOf<DeviceVideo>()
    context.contentResolver.query(
      collection,
      projection,
      null,
      null,
      "${MediaStore.Video.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
      val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
      val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
      val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
      val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
      val bucketCol = cursor.getColumnIndex(COLUMN_BUCKET)
      while (cursor.moveToNext()) {
        val id = cursor.getLong(idCol)
        val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
        out.add(
          DeviceVideo(
            id = id,
            uri = ContentUris.withAppendedId(collection, id),
            name = cursor.getString(nameCol) ?: "video",
            durationMs = cursor.getLong(durCol),
            sizeBytes = cursor.getLong(sizeCol),
            folder = bucket?.takeIf { it.isNotBlank() } ?: OTHER_FOLDER,
          ),
        )
      }
    }
    return out
  }
}

/** One entry per parent folder, folders sorted by name, videos sorted by name. */
private fun groupByFolder(videos: List<DeviceVideo>): List<VideoFolder> =
  videos
    .groupBy { it.folder }
    .map { (name, items) -> VideoFolder(name, items.sortedBy { it.name.lowercase() }) }
    .sortedBy { it.name.lowercase() }

@Composable
private fun FolderGrid(
  folders: List<VideoFolder>,
  onOpen: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    items(folders, key = { it.name }) { folder ->
      FolderCard(folder) { onOpen(folder.name) }
    }
  }
}

@Composable
private fun FolderCard(folder: VideoFolder, onClick: () -> Unit) {
  val context = LocalContext.current
  val cover = folder.videos.firstOrNull()
  val thumb by produceState<ImageBitmap?>(initialValue = null, folder.name, cover?.id) {
    value = if (cover != null) withContext(Dispatchers.IO) { loadThumb(context, cover) } else null
  }
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = thumb
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = folder.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(Icons.Default.Folder, null)
      }
      Text(
        text = "${folder.videos.size}",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(6.dp)
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
          .padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      Icon(
        Icons.Default.Folder,
        null,
        modifier = Modifier.padding(end = 4.dp),
      )
      Text(
        text = folder.name,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun VideoGrid(
  videos: List<DeviceVideo>,
  onPlay: (DeviceVideo) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    items(videos, key = { it.id }) { video ->
      VideoCard(video) { onPlay(video) }
    }
  }
}

@Composable
private fun VideoCard(video: DeviceVideo, onClick: () -> Unit) {
  val context = LocalContext.current
  val thumb by produceState<ImageBitmap?>(initialValue = null, video.id) {
    value = withContext(Dispatchers.IO) { loadThumb(context, video) }
  }
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = thumb
      if (bitmap != null) {
        Image(
          bitmap = bitmap,
          contentDescription = video.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
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
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      Text(
        text = video.name,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun loadThumb(context: Context, video: DeviceVideo): ImageBitmap? {
  return try {
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 29) {
      context.contentResolver.loadThumbnail(video.uri, Size(480, 270), null)
    } else {
      @Suppress("DEPRECATION")
      MediaStore.Video.Thumbnails.getThumbnail(
        context.contentResolver,
        video.id,
        MediaStore.Video.Thumbnails.MINI_KIND,
        null,
      )
    }
    bitmap?.asImageBitmap()
  } catch (_: Exception) {
    null
  }
}

private fun formatDuration(ms: Long): String {
  if (ms <= 0) return "--:--"
  val totalSeconds = ms / 1000
  val h = totalSeconds / 3600
  val m = (totalSeconds % 3600) / 60
  val s = totalSeconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
