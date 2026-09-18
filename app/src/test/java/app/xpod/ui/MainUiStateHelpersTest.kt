package app.xpod.ui

import app.xpod.data.ArticleEntity
import app.xpod.data.EpisodeEntity
import app.xpod.ui.coordination.unplayedEpisodeCount
import app.xpod.ui.coordination.unreadArticleCount
import org.junit.Assert.assertEquals
import org.junit.Test

class MainUiStateHelpersTest {
  @Test
  fun unplayedEpisodeCountOnlyCountsUnplayedEpisodesOfThePodcast() {
    val episodes =
        listOf(
            episode("a", podcast = "p1", played = false),
            episode("b", podcast = "p1", played = true),
            episode("c", podcast = "p1", played = false),
            episode("d", podcast = "p2", played = false),
        )

    assertEquals(2, unplayedEpisodeCount(episodes, "p1"))
    assertEquals(1, unplayedEpisodeCount(episodes, "p2"))
  }

  @Test
  fun unplayedEpisodeCountIsZeroForUnknownPodcast() {
    assertEquals(0, unplayedEpisodeCount(emptyList(), "p1"))
    assertEquals(
        0,
        unplayedEpisodeCount(listOf(episode("a", podcast = "p2", played = false)), "p1"),
    )
  }

  @Test
  fun unreadArticleCountFiltersByFeedWhenGivenOne() {
    val articles =
        listOf(
            article("a", feed = "f1", read = false),
            article("b", feed = "f1", read = true),
            article("c", feed = "f2", read = false),
        )

    assertEquals(1, unreadArticleCount(articles, "f1"))
    assertEquals(1, unreadArticleCount(articles, "f2"))
    assertEquals(0, unreadArticleCount(articles, "f-missing"))
  }

  @Test
  fun unreadArticleCountCountsAllFeedsWhenFeedIsNull() {
    val articles =
        listOf(
            article("a", feed = "f1", read = false),
            article("b", feed = "f2", read = false),
            article("c", feed = "f3", read = true),
        )

    assertEquals(2, unreadArticleCount(articles, null))
  }

  private fun episode(
      id: String,
      podcast: String,
      played: Boolean,
  ): EpisodeEntity =
      EpisodeEntity(
          id = id,
          podcastId = podcast,
          stableKey = id,
          title = "Episode $id",
          description = "",
          audioUrl = "https://example.com/$id.mp3",
          publishedEpochMs = 1L,
          durationMs = null,
          artworkUrl = null,
          isPlayed = played,
      )

  private fun article(
      id: String,
      feed: String,
      read: Boolean,
  ): ArticleEntity =
      ArticleEntity(
          id = id,
          feedId = feed,
          stableKey = id,
          title = "Article $id",
          author = "",
          content = "",
          url = null,
          publishedEpochMs = 1L,
          artworkUrl = null,
          isRead = read,
      )
}
