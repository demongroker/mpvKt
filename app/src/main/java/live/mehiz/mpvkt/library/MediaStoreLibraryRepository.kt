package live.mehiz.mpvkt.library

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import live.mehiz.mpvkt.domain.resume.repository.ResumeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A source of things that must not appear in the library. Hidden folders feed it
 * today; the locked-folder feature will provide the same type later, which is why
 * nothing else knows what locking is.
 */
fun interface LibraryExclusionSource {
  suspend fun exclusions(): LibraryExclusions

  companion object {
    val None = LibraryExclusionSource { LibraryExclusions() }
  }
}

/**
 * The one place media queries happen. Everything the library shows comes through
 * here, joined with resume state, so a future filter (locked folders) has a single
 * seam instead of a dozen call sites.
 */
class MediaStoreLibraryRepository(
  private val context: Context,
  private val resumeRepository: ResumeRepository,
) {
  suspend fun loadVideos(): List<LibraryVideo> = withContext(Dispatchers.IO) {
    val resume = resumeRepository.getAll().associateBy { it.fileKey }
    queryMediaStore().map { video ->
      val state = resume[video.fileKey]
      video.copy(
        positionMs = state?.positionMs ?: 0,
        lastPlayedAt = state?.lastPlayedAt ?: 0,
        completed = state?.completed ?: false,
      )
    }
  }

  private fun queryMediaStore(): List<LibraryVideo> {
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
      MediaStore.Video.Media.DATE_ADDED,
      MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
      MediaStore.Video.Media.DATA,
    )
    val videos = mutableListOf<LibraryVideo>()
    context.contentResolver.query(
      collection,
      projection,
      null,
      null,
      "${MediaStore.Video.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
      val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
      val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
      val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
      val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
      val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
      val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
      val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
      while (cursor.moveToNext()) {
        val id = cursor.getLong(idColumn)
        val path = if (dataColumn >= 0) cursor.getString(dataColumn).orEmpty() else ""
        val rawName = cursor.getString(nameColumn) ?: "video"
        val uri = ContentUris.withAppendedId(collection, id).toString()
        videos += LibraryVideo(
          fileKey = LibraryLogic.fileKey(id, path.ifBlank { uri }),
          mediaId = id,
          uri = uri,
          name = rawName,
          folder = LibraryLogic.folderName(if (bucketColumn >= 0) cursor.getString(bucketColumn) else null, path),
          path = path,
          durationMs = cursor.getLong(durationColumn),
          sizeBytes = cursor.getLong(sizeColumn),
          dateAddedSec = cursor.getLong(dateColumn),
        )
      }
    }
    return videos
  }

  /** Resume state for one file, used by the player before it starts. */
  suspend fun resumeFor(fileKey: String) = resumeRepository.get(fileKey)

  suspend fun saveResume(
    fileKey: String,
    positionMs: Long,
    durationMs: Long,
    playedAtMs: Long = System.currentTimeMillis(),
  ) {
    resumeRepository.save(
      live.mehiz.mpvkt.database.entities.ResumeStateEntity(
        fileKey = fileKey,
        positionMs = positionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        lastPlayedAt = playedAtMs,
        completed = LibraryLogic.isCompleted(positionMs, durationMs),
      ),
    )
  }

  fun uriOf(video: LibraryVideo) = video.uri.toUri()
}
