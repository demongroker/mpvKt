package live.mehiz.mpvkt.database

import androidx.room.Database
import androidx.room.RoomDatabase
import live.mehiz.mpvkt.database.dao.CustomButtonDao
import live.mehiz.mpvkt.database.dao.PlaybackStateDao
import live.mehiz.mpvkt.database.dao.ResumeStateDao
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.database.entities.ResumeStateEntity

const val DATABASE_VERSION = 6

@Database(
  entities = [PlaybackStateEntity::class, CustomButtonEntity::class, ResumeStateEntity::class],
  version = DATABASE_VERSION,
)
abstract class MpvKtDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao
  abstract fun customButtonDao(): CustomButtonDao
  abstract fun resumeStateDao(): ResumeStateDao
}
