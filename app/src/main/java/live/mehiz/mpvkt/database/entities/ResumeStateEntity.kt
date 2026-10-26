package live.mehiz.mpvkt.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Where the owner stopped watching a file.
 *
 * Keyed by [fileKey] (MediaStore id plus a hash of the path) rather than by title,
 * because two folders can hold files with the same name. Milliseconds on purpose:
 * the library draws a progress bar from this.
 */
@Entity(tableName = "resume_state")
data class ResumeStateEntity(
  @PrimaryKey val fileKey: String,
  val positionMs: Long,
  val durationMs: Long,
  val lastPlayedAt: Long,
  val completed: Boolean,
)
