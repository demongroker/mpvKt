package live.mehiz.mpvkt.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import live.mehiz.mpvkt.database.entities.ResumeStateEntity

@Dao
interface ResumeStateDao {
  @Upsert
  suspend fun upsert(state: ResumeStateEntity)

  @Query("SELECT * FROM resume_state WHERE fileKey = :fileKey LIMIT 1")
  suspend fun get(fileKey: String): ResumeStateEntity?

  @Query("SELECT * FROM resume_state")
  suspend fun getAll(): List<ResumeStateEntity>

  @Query("SELECT * FROM resume_state WHERE lastPlayedAt > :since ORDER BY lastPlayedAt DESC LIMIT :limit")
  suspend fun recentlyPlayed(since: Long, limit: Int): List<ResumeStateEntity>

  @Query("DELETE FROM resume_state WHERE fileKey = :fileKey")
  suspend fun delete(fileKey: String)

  @Query("DELETE FROM resume_state")
  suspend fun clearAll()
}
