package app.xpod.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
  @Query("SELECT * FROM PodcastEntity ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<PodcastEntity>>

  @Query("SELECT * FROM PodcastEntity ORDER BY title COLLATE NOCASE")
  suspend fun all(): List<PodcastEntity>

  @Query("SELECT * FROM PodcastEntity WHERE id = :id") suspend fun find(id: String): PodcastEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(podcast: PodcastEntity)

  @Query("DELETE FROM PodcastEntity WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface EpisodeDao {
  @Query("SELECT * FROM EpisodeEntity WHERE podcastId = :podcastId ORDER BY publishedEpochMs DESC")
  fun observeForPodcast(podcastId: String): Flow<List<EpisodeEntity>>

  @Query("SELECT * FROM EpisodeEntity ORDER BY publishedEpochMs DESC")
  fun observeAll(): Flow<List<EpisodeEntity>>

  @Query("SELECT * FROM EpisodeEntity WHERE id = :id") suspend fun find(id: String): EpisodeEntity?

  @Query("SELECT * FROM EpisodeEntity WHERE podcastId = :podcastId")
  suspend fun allForPodcast(podcastId: String): List<EpisodeEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(episodes: List<EpisodeEntity>)

  @Query("UPDATE EpisodeEntity SET isPlayed = :played WHERE id = :id")
  suspend fun setPlayed(id: String, played: Boolean)

  @Query("UPDATE EpisodeEntity SET isFavorite = NOT isFavorite WHERE id = :id")
  suspend fun toggleFavorite(id: String)

  @Query("UPDATE EpisodeEntity SET isNew = 0 WHERE podcastId = :podcastId")
  suspend fun markPodcastSeen(podcastId: String)

  @Query("UPDATE EpisodeEntity SET lastPlayedEpochMs = :lastPlayedEpochMs WHERE id = :id")
  suspend fun recordPlayback(id: String, lastPlayedEpochMs: Long)

  @Query(
      "UPDATE EpisodeEntity SET isPlayed = 1, lastPlayedEpochMs = :lastPlayedEpochMs WHERE id = :id AND isPlayed = 0"
  )
  suspend fun markEpisodePlayed(id: String, lastPlayedEpochMs: Long): Int
}

@Dao
interface ArticleFeedDao {
  @Query("SELECT * FROM ArticleFeedEntity ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<ArticleFeedEntity>>

  @Query("SELECT * FROM ArticleFeedEntity ORDER BY title COLLATE NOCASE")
  suspend fun all(): List<ArticleFeedEntity>

  @Query("SELECT * FROM ArticleFeedEntity WHERE id = :id")
  suspend fun find(id: String): ArticleFeedEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(feed: ArticleFeedEntity)

  @Query("DELETE FROM ArticleFeedEntity WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ArticleDao {
  @Query("SELECT * FROM ArticleEntity ORDER BY publishedEpochMs DESC")
  fun observeAll(): Flow<List<ArticleEntity>>

  @Query("SELECT * FROM ArticleEntity WHERE feedId = :feedId ORDER BY publishedEpochMs DESC")
  fun observeForFeed(feedId: String): Flow<List<ArticleEntity>>

  @Query("SELECT * FROM ArticleEntity WHERE id = :id") suspend fun find(id: String): ArticleEntity?

  @Query("SELECT * FROM ArticleEntity WHERE feedId = :feedId")
  suspend fun allForFeed(feedId: String): List<ArticleEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<ArticleEntity>)

  @Query("UPDATE ArticleEntity SET isRead = 1 WHERE id = :id") suspend fun markRead(id: String)

  @Query("UPDATE ArticleEntity SET isRead = :read WHERE id = :id")
  suspend fun setRead(id: String, read: Boolean)

  @Query("UPDATE ArticleEntity SET isFavorite = NOT isFavorite WHERE id = :id")
  suspend fun toggleFavorite(id: String)
}

@Dao
interface PlaybackDao {
  @Query("SELECT * FROM PlaybackStateEntity WHERE key = 'active'")
  suspend fun current(): PlaybackStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(state: PlaybackStateEntity)

  @Query("SELECT * FROM QueueItemEntity ORDER BY position")
  suspend fun queue(): List<QueueItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQueue(items: List<QueueItemEntity>)

  @Query("DELETE FROM QueueItemEntity") suspend fun clearQueue()
}
