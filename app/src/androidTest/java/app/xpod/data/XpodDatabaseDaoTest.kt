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
        .insertQueue(listOf(QueueItemEntity(first.id, 0), QueueItemEntity(second.id, 1)))
    database
        .playback()
        .save(PlaybackStateEntity(episodeId = first.id, positionMs = 12_345L, speed = 1.25f))

    database.playback().removeQueueEpisodesForPodcast("podcast")
    database.playback().clearStateForPodcast("podcast", updatedAtEpochMs = 99L)

    assertEquals(listOf(second.id), database.playback().queue().map(QueueItemEntity::episodeId))
    val state = database.playback().current()
    assertNull(state?.episodeId)
    assertEquals(0L, state?.positionMs)
    assertEquals(99L, state?.updatedAtEpochMs)
  }

  @Test
  fun playbackRepositoryDoesNotPersistMissingEpisodes() = runBlocking {
    val repository =
        PlaybackRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(123L), ZoneOffset.UTC),
        )

    repository.save("missing", positionMs = 456L, speed = 1.5f)

    val state = database.playback().current()
    assertNull(state?.episodeId)
    assertEquals(0L, state?.positionMs)
    assertEquals(123L, state?.updatedAtEpochMs)
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
}
