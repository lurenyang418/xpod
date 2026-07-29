package app.xpod.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class EpisodeBulkState(
    val id: String,
    val isPlayed: Boolean,
    val isNew: Boolean,
)

@Dao
interface PodcastDao {
  @Query("SELECT * FROM PodcastEntity ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<PodcastEntity>>

  @Query("SELECT * FROM PodcastEntity ORDER BY title COLLATE NOCASE")
  suspend fun all(): List<PodcastEntity>

  @Query("SELECT * FROM PodcastEntity WHERE id = :id") suspend fun find(id: String): PodcastEntity?

  @Upsert suspend fun upsert(podcast: PodcastEntity)

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

  @Query(
      "SELECT id, isPlayed, isNew FROM EpisodeEntity WHERE podcastId = :podcastId AND (isPlayed = 0 OR isNew = 1)"
  )
  suspend fun bulkStatesForPodcast(podcastId: String): List<EpisodeBulkState>

  @Query(
      "UPDATE EpisodeEntity SET isPlayed = 1, isNew = 0 WHERE podcastId = :podcastId AND (isPlayed = 0 OR isNew = 1)"
  )
  suspend fun markAllPlayed(podcastId: String)

  @Query("UPDATE EpisodeEntity SET isPlayed = :played, isNew = :isNew WHERE id IN (:ids)")
  suspend fun restoreBulkStates(ids: List<String>, played: Boolean, isNew: Boolean)

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

  @Upsert suspend fun upsert(feed: ArticleFeedEntity)

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

  @Query("UPDATE ArticleEntity SET isRead = :read WHERE id IN (:ids)")
  suspend fun setRead(ids: List<String>, read: Boolean)

  @Query("SELECT id FROM ArticleEntity WHERE isRead = 0") suspend fun unreadIds(): List<String>

  @Query("SELECT id FROM ArticleEntity WHERE feedId = :feedId AND isRead = 0")
  suspend fun unreadIdsForFeed(feedId: String): List<String>

  @Query("UPDATE ArticleEntity SET isRead = 1 WHERE isRead = 0") suspend fun markAllRead()

  @Query("UPDATE ArticleEntity SET isRead = 1 WHERE feedId = :feedId AND isRead = 0")
  suspend fun markFeedRead(feedId: String)

  @Query("UPDATE ArticleEntity SET isFavorite = NOT isFavorite WHERE id = :id")
  suspend fun toggleFavorite(id: String)
}

@Dao
interface LocalTrackDao {
  @Query("SELECT * FROM LocalTrackEntity ORDER BY title COLLATE NOCASE, artist COLLATE NOCASE")
  fun observeAll(): Flow<List<LocalTrackEntity>>

  @Query("SELECT * FROM LocalTrackEntity ORDER BY title COLLATE NOCASE, artist COLLATE NOCASE")
  suspend fun all(): List<LocalTrackEntity>

  @Query("SELECT * FROM LocalTrackEntity WHERE id = :id")
  suspend fun find(id: String): LocalTrackEntity?

  @Query("SELECT id FROM LocalTrackEntity") suspend fun ids(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<LocalTrackEntity>)

  @Query("DELETE FROM LocalTrackEntity") suspend fun clear()
}

@Dao
interface PlaybackDao {
  @Query("SELECT * FROM PlaybackStateEntity ORDER BY updatedAtEpochMs DESC LIMIT 1")
  suspend fun current(): PlaybackStateEntity?

  @Query("SELECT * FROM PlaybackStateEntity WHERE key = :mediaType")
  suspend fun state(mediaType: String): PlaybackStateEntity?

  @Query("SELECT MAX(updatedAtEpochMs) FROM PlaybackStateEntity")
  suspend fun latestUpdatedAt(): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(state: PlaybackStateEntity)

  @Query("SELECT * FROM QueueItemEntity WHERE mediaType = :mediaType ORDER BY position")
  suspend fun queue(mediaType: String): List<QueueItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQueue(items: List<QueueItemEntity>)

  @Query("DELETE FROM QueueItemEntity WHERE mediaType = :mediaType")
  suspend fun clearQueue(mediaType: String)

  @Query(
      "DELETE FROM QueueItemEntity WHERE mediaType = 'Podcast' AND episodeId IN (SELECT id FROM EpisodeEntity WHERE podcastId = :podcastId)"
  )
  suspend fun removeQueueEpisodesForPodcast(podcastId: String)

  @Query(
      "UPDATE PlaybackStateEntity SET episodeId = NULL, positionMs = 0 WHERE mediaType = 'Podcast' AND episodeId IN (SELECT id FROM EpisodeEntity WHERE podcastId = :podcastId)"
  )
  suspend fun clearStateForPodcast(podcastId: String)
}
