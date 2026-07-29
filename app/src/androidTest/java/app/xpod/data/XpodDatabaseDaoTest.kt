package app.xpod.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XpodDatabaseDaoTest {
  private lateinit var database: XpodDatabase

  @Before
  fun createDatabase() {
    database =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                XpodDatabase::class.java,
            )
            .build()
  }

  @After
  fun closeDatabase() {
    database.close()
  }

  @Test
  fun refreshingParentRowsDoesNotCascadeDeleteChildren() = runBlocking {
    val podcast = podcast()
    val episode = episode()
    val articleFeed = articleFeed()
    val article = article()
    database.podcasts().upsert(podcast)
    database.episodes().upsertAll(listOf(episode))
    database.articleFeeds().upsert(articleFeed)
    database.articles().upsertAll(listOf(article))

    database.podcasts().upsert(podcast.copy(title = "Updated podcast"))
    database.articleFeeds().upsert(articleFeed.copy(title = "Updated articles"))

    assertEquals(episode, database.episodes().find(episode.id))
    assertEquals(article, database.articles().find(article.id))
  }

  @Test
  fun removedEpisodesAreDroppedFromQueueAndPlaybackState() = runBlocking {
    val first = episode()
    val second =
        episode(
            id = "episode-2",
            podcastId = "podcast-2",
            stableKey = "stable-2",
        )
    database.podcasts().upsert(podcast())
    database
        .podcasts()
        .upsert(podcast(id = "podcast-2", feedUrl = "https://example.com/podcast-2.xml"))
    database.episodes().upsertAll(listOf(first, second))
    database
        .playback()
        .insertQueue(
            listOf(
                QueueItemEntity(first.id, PlaybackMediaType.Podcast.name, 0),
                QueueItemEntity(second.id, PlaybackMediaType.Podcast.name, 1),
            )
        )
    database
        .playback()
        .save(
            PlaybackStateEntity(
                key = PlaybackMediaType.Podcast.name,
                mediaId = first.id,
                mediaType = PlaybackMediaType.Podcast.name,
                positionMs = 12_345L,
                speed = 1.25f,
                updatedAtEpochMs = 10L,
            )
        )
    database
        .playback()
        .save(
            PlaybackStateEntity(
                key = PlaybackMediaType.Music.name,
                mediaId = null,
                mediaType = PlaybackMediaType.Music.name,
                updatedAtEpochMs = 20L,
            )
        )

    database.playback().removeQueueEpisodesForPodcast("podcast")
    database.playback().clearStateForPodcast("podcast")

    assertEquals(
        listOf(second.id),
        database.playback().queue(PlaybackMediaType.Podcast.name).map(QueueItemEntity::mediaId),
    )
    val state = database.playback().state(PlaybackMediaType.Podcast.name)
    assertNull(state?.mediaId)
    assertEquals(0L, state?.positionMs)
    assertEquals(10L, state?.updatedAtEpochMs)
    assertEquals(PlaybackMediaType.Music.name, database.playback().current()?.mediaType)
  }

  @Test
  fun playbackRepositoryDoesNotPersistMissingEpisodes() = runBlocking {
    val repository =
        PlaybackRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(123L), ZoneOffset.UTC),
        )

    repository.save(
        "missing",
        PlaybackMediaType.Podcast,
        positionMs = 456L,
        speed = 1.5f,
    )

    val state = database.playback().current()
    assertNull(state?.mediaId)
    assertEquals(0L, state?.positionMs)
    assertEquals(123L, state?.updatedAtEpochMs)
  }

  @Test
  fun playbackRepositoryKeepsSameMillisecondSavesInOrder() = runBlocking {
    val repository =
        PlaybackRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(123L), ZoneOffset.UTC),
        )
    val episode = episode()
    val track = localTrack(id = "track", title = "Track", artist = "Artist")
    database.podcasts().upsert(podcast())
    database.episodes().upsertAll(listOf(episode))
    database.localTracks().upsertAll(listOf(track))

    repository.save(
        episode.id,
        PlaybackMediaType.Podcast,
        positionMs = 1_000L,
        speed = 1.25f,
    )
    repository.save(
        track.id,
        PlaybackMediaType.Music,
        positionMs = 2_000L,
        speed = 1f,
    )

    assertEquals(PlaybackMediaType.Music.name, repository.state()?.mediaType)
    assertEquals(123L, repository.state(PlaybackMediaType.Podcast)?.updatedAtEpochMs)
    assertEquals(124L, repository.state(PlaybackMediaType.Music)?.updatedAtEpochMs)
  }

  @Test
  fun localTracksAreOrderedAndExposeStableIds() = runBlocking {
    val second = localTrack(id = "second", title = "zebra", artist = "Beta")
    val first = localTrack(id = "first", title = "Alpha", artist = "Gamma")
    database.localTracks().upsertAll(listOf(second, first))

    assertEquals(listOf(first, second), database.localTracks().all())
    assertEquals(setOf("first", "second"), database.localTracks().ids().toSet())
    assertEquals(second, database.localTracks().find("second"))

    database.localTracks().clear()

    assertEquals(emptyList<LocalTrackEntity>(), database.localTracks().all())
  }

  @Test
  fun podcastEpisodesCanBeReadOneOrderedPageAtATime() = runBlocking {
    database.podcasts().upsert(podcast())
    val oldest = episode(id = "oldest", stableKey = "oldest").copy(publishedEpochMs = 1L)
    val middle = episode(id = "middle", stableKey = "middle").copy(publishedEpochMs = 2L)
    val newest = episode(id = "newest", stableKey = "newest").copy(publishedEpochMs = 3L)
    database.episodes().upsertAll(listOf(oldest, newest, middle))

    assertEquals(
        listOf(newest, middle),
        database.episodes().pageForPodcast("podcast", limit = 2, offset = 0),
    )
    assertEquals(
        listOf(oldest),
        database.episodes().pageForPodcast("podcast", limit = 2, offset = 2),
    )
  }

  private fun podcast(
      id: String = "podcast",
      feedUrl: String = "https://example.com/podcast.xml",
  ) =
      PodcastEntity(
          id = id,
          feedUrl = feedUrl,
          title = "Podcast",
          author = "Author",
          description = "Description",
          artworkUrl = null,
      )

  private fun episode(
      id: String = "episode",
      podcastId: String = "podcast",
      stableKey: String = "stable",
  ) =
      EpisodeEntity(
          id = id,
          podcastId = podcastId,
          stableKey = stableKey,
          title = "Episode",
          description = "Description",
          audioUrl = "https://example.com/episode.mp3",
          publishedEpochMs = 1L,
          durationMs = 2L,
          artworkUrl = null,
          isPlayed = true,
          isFavorite = true,
          isNew = true,
          lastPlayedEpochMs = 3L,
      )

  private fun articleFeed() =
      ArticleFeedEntity(
          id = "articles",
          feedUrl = "https://example.com/articles.xml",
          title = "Articles",
          author = "Author",
          description = "Description",
          artworkUrl = null,
      )

  private fun article() =
      ArticleEntity(
          id = "article",
          feedId = "articles",
          stableKey = "stable",
          title = "Article",
          author = "Author",
          content = "Content",
          url = "https://example.com/article",
          publishedEpochMs = 1L,
          artworkUrl = null,
          isRead = true,
          isFavorite = true,
      )

  private fun localTrack(
      id: String,
      title: String,
      artist: String,
  ) =
      LocalTrackEntity(
          id = id,
          documentUri = "content://provider/document/$id",
          treeUri = "content://provider/tree/music",
          title = title,
          artist = artist,
          album = "Album",
          durationMs = 1_000L,
          modifiedEpochMs = 2L,
      )
}
