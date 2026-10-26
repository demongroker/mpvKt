package live.mehiz.mpvkt.domain.resume.repository

import live.mehiz.mpvkt.database.entities.ResumeStateEntity

/** Where the owner stopped watching, per file. */
interface ResumeRepository {
  suspend fun save(state: ResumeStateEntity)

  suspend fun get(fileKey: String): ResumeStateEntity?

  suspend fun getAll(): List<ResumeStateEntity>

  suspend fun recentlyPlayed(since: Long, limit: Int = 20): List<ResumeStateEntity>

  suspend fun clear(fileKey: String)
}
