package app.xpod.data

import androidx.room.ColumnInfo
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
    indices =
        [
            Index("podcastId"),
            Index(value = ["podcastId", "stableKey"], unique = true),
            Index("publishedEpochMs"),
            Index(value = ["podcastId", "publishedEpochMs"]),
        ],
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

@Entity(indices = [Index(value = ["feedUrl"], unique = true)])
data class ArticleFeedEntity(
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
        [ForeignKey(ArticleFeedEntity::class, ["id"], ["feedId"], onDelete = ForeignKey.CASCADE)],
    indices =
        [
            Index("feedId"),
            Index(value = ["feedId", "stableKey"], unique = true),
            Index("publishedEpochMs"),
            Index(value = ["feedId", "publishedEpochMs"]),
        ],
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val stableKey: String,
    val title: String,
    val author: String,
    val content: String,
    val url: String?,
    val publishedEpochMs: Long,
    val artworkUrl: String?,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
)

@Entity(indices = [Index("treeUri"), Index("title"), Index("relativePath")])
data class LocalTrackEntity(
    @PrimaryKey val id: String,
    val documentUri: String,
    val treeUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val modifiedEpochMs: Long,
    val relativePath: String = "",
)

enum class PlaybackMediaType {
  Podcast,
  Music;

  companion object {
    fun fromStored(value: String): PlaybackMediaType =
        entries.firstOrNull { it.name == value } ?: Podcast

    fun fromMediaId(mediaId: String?): PlaybackMediaType =
        if (mediaId?.startsWith(LOCAL_TRACK_ID_PREFIX) == true) Music else Podcast
  }
}

const val LOCAL_TRACK_ID_PREFIX = "local:"

data class PlaybackItem(
    val id: String,
    val mediaType: PlaybackMediaType,
    val title: String,
    val subtitle: String,
    val uri: String,
    val artworkUri: String? = null,
    val sourceId: String? = null,
    val durationMs: Long? = null,
)

fun EpisodeEntity.asPlaybackItem(): PlaybackItem =
    PlaybackItem(
        id = id,
        mediaType = PlaybackMediaType.Podcast,
        title = title,
        subtitle = description,
        uri = audioUrl,
        artworkUri = artworkUrl,
        sourceId = podcastId,
        durationMs = durationMs?.takeIf { it > 0L },
    )

fun LocalTrackEntity.asPlaybackItem(): PlaybackItem =
    PlaybackItem(
        id = id,
        mediaType = PlaybackMediaType.Music,
        title = title,
        subtitle = artist.ifBlank { album },
        uri = documentUri,
        durationMs = durationMs.takeIf { it > 0L },
    )

@Entity
data class PlaybackStateEntity(
    @PrimaryKey val key: String,
    // Retain the legacy column name so existing playback rows migrate without a destructive rename.
    @ColumnInfo(name = "episodeId") val mediaId: String?,
    val mediaType: String,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val updatedAtEpochMs: Long = 0,
)

@Entity(indices = [Index(value = ["mediaType", "position"], unique = true)])
data class QueueItemEntity(
    // Retain the legacy column name so existing queue rows migrate without a destructive rename.
    @PrimaryKey @ColumnInfo(name = "episodeId") val mediaId: String,
    val mediaType: String,
    val position: Int,
)

data class PlaybackReference(
    val mediaId: String,
    val mediaType: PlaybackMediaType,
)

enum class BookFormat {
  EPUB,
  PDF,
}

// BooksViewModel filters and sorts the (small) library in memory, so secondary indices would
// only slow down rescans.
@Entity
data class LocalBookEntity(
    @PrimaryKey val id: String,
    val documentUri: String,
    val treeUri: String,
    val title: String,
    val author: String,
    val language: String,
    val format: String,
    val fileSizeBytes: Long,
    val modifiedEpochMs: Long,
    val relativePath: String = "",
    val coverCachePath: String? = null,
    val addedEpochMs: Long = 0,
    val lastOpenedEpochMs: Long = 0,
    val isFavorite: Boolean = false,
)

@Entity
data class BookProgressEntity(
    @PrimaryKey val bookId: String,
    val positionVersion: Int = 1,
    val positionJson: String = "{}",
    val sourceModifiedEpochMs: Long = 0,
    val updatedEpochMs: Long = 0,
    val readingSeconds: Long = 0,
)

data class BookWithProgress(
    val id: String,
    val documentUri: String,
    val treeUri: String,
    val title: String,
    val author: String,
    val language: String,
    val format: String,
    val fileSizeBytes: Long,
    val modifiedEpochMs: Long,
    val relativePath: String,
    val coverCachePath: String?,
    val addedEpochMs: Long,
    val lastOpenedEpochMs: Long,
    val isFavorite: Boolean,
    val positionVersion: Int?,
    val positionJson: String?,
    val sourceModifiedEpochMs: Long?,
    val progressUpdatedEpochMs: Long?,
    val readingSeconds: Long?,
)
