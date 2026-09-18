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
            LocalTrackEntity::class,
            LocalVideoEntity::class,
            LocalBookEntity::class,
            BookProgressEntity::class,
            PlaybackStateEntity::class,
            QueueItemEntity::class,
        ],
    version = 7,
    exportSchema = true,
)
abstract class XpodDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao

  abstract fun episodes(): EpisodeDao

  abstract fun articleFeeds(): ArticleFeedDao

  abstract fun articles(): ArticleDao

  abstract fun localTracks(): LocalTrackDao

  abstract fun localVideos(): LocalVideoDao

  abstract fun localBooks(): LocalBookDao

  abstract fun bookProgress(): BookProgressDao

  abstract fun playback(): PlaybackDao
}
