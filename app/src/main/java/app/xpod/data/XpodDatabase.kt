package app.xpod.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            PodcastEntity::class,
            EpisodeEntity::class,
            ArticleFeedEntity::class,
            ArticleEntity::class,
            PlaybackStateEntity::class,
            QueueItemEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class XpodDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao

  abstract fun episodes(): EpisodeDao

  abstract fun articleFeeds(): ArticleFeedDao

  abstract fun articles(): ArticleDao

  abstract fun playback(): PlaybackDao
}
