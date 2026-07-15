package app.xpod.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["feedUrl"], unique = true)])
data class PodcastEntity(
    @PrimaryKey val id: String,
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val lastRefreshEpochMs: Long = 0,
    val lastError: String? = null,
)

@Entity(
    foreignKeys =
        [ForeignKey(PodcastEntity::class, ["id"], ["podcastId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("podcastId"), Index(value = ["podcastId", "stableKey"], unique = true)],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val stableKey: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishedEpochMs: Long,
    val durationMs: Long?,
    val artworkUrl: String?,
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false,
    val isNew: Boolean = false,
    val lastPlayedEpochMs: Long = 0,
)

@Entity
data class PlaybackStateEntity(
    @PrimaryKey val key: String = "active",
    val episodeId: String?,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val updatedAtEpochMs: Long = 0,
)

@Entity(indices = [Index(value = ["position"], unique = true)])
data class QueueItemEntity(
    @PrimaryKey val episodeId: String,
    val position: Int,
)
