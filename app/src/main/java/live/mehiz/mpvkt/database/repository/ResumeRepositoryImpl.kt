package live.mehiz.mpvkt.database.repository

import live.mehiz.mpvkt.database.dao.ResumeStateDao
import live.mehiz.mpvkt.database.entities.ResumeStateEntity
import live.mehiz.mpvkt.domain.resume.repository.ResumeRepository

/**
 * Thin on purpose: every decision (what counts as completed, when to offer a
 * resume) lives in [live.mehiz.mpvkt.library.LibraryLogic] and has no Android in it.
 */
class ResumeRepositoryImpl(
  private val dao: ResumeStateDao,
) : ResumeRepository {
  override suspend fun save(state: ResumeStateEntity) = dao.upsert(state)

  override suspend fun get(fileKey: String): ResumeStateEntity? = dao.get(fileKey)

  override suspend fun getAll(): List<ResumeStateEntity> = dao.getAll()

  override suspend fun recentlyPlayed(since: Long, limit: Int): List<ResumeStateEntity> =
    dao.recentlyPlayed(since, limit)

  override suspend fun clear(fileKey: String) = dao.delete(fileKey)
}
