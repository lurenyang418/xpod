package app.xpod.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, PlaybackStateEntity::class, QueueItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class XpodDatabase : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun episodes(): EpisodeDao
    abstract fun playback(): PlaybackDao
}
