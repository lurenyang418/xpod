package app.xpod.ui

import app.xpod.data.ArticleEntity
import app.xpod.data.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkMarkScopeTest {
  @Test
  fun podcastScopeCountsOnlyUnplayedEpisodesFromTheSelectedSubscription() {
    val episodes =
        listOf(
            episode("episode-a", "podcast-a", played = false),
            episode("episode-b", "podcast-a", played = true),
            episode("episode-c", "podcast-b", played = false),
        )

    assertEquals(1, unplayedEpisodeCount(episodes, "podcast-a"))
    assertEquals(1, unplayedEpisodeCount(episodes, "podcast-b"))
    assertEquals(0, unplayedEpisodeCount(episodes, "missing"))
  }

  @Test
  fun articleScopeCountsAllFeedsOrOnlyTheSelectedFeed() {
    val articles =
        listOf(
            article("article-a", "feed-a", read = false),
            article("article-b", "feed-a", read = true),
            article("article-c", "feed-b", read = false),
        )

    assertEquals(2, unreadArticleCount(articles, null))
    assertEquals(1, unreadArticleCount(articles, "feed-a"))
    assertEquals(1, unreadArticleCount(articles, "feed-b"))
    assertEquals(0, unreadArticleCount(articles, "missing"))
  }

  private fun episode(id: String, podcastId: String, played: Boolean) =
      EpisodeEntity(
          id = id,
          podcastId = podcastId,
          stableKey = id,
          title = id,
          description = "",
          audioUrl = "https://example.com/$id.mp3",
          publishedEpochMs = 0,
          durationMs = null,
          artworkUrl = null,
          isPlayed = played,
      )

  private fun article(id: String, feedId: String, read: Boolean) =
      ArticleEntity(
          id = id,
          feedId = feedId,
          stableKey = id,
          title = id,
          author = "",
          content = "",
          url = null,
          publishedEpochMs = 0,
          artworkUrl = null,
          isRead = read,
      )
}
