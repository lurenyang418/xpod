package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudMemoDraftsTest {
  @Test
  fun episodeDraftIncludesEscapedLinkSourceAndTags() {
    val episode =
        EpisodeEntity(
            id = "episode-id",
            podcastId = "podcast-id",
            stableKey = "stable-key",
            title = "An [episode]",
            description = "Description",
            audioUrl = "https://cdn.example.com/episode.mp3",
            publishedEpochMs = 1,
            durationMs = 2,
            artworkUrl = null,
        )

    assertEquals(
        "## [An \\[episode\\]](<https://cdn.example.com/episode.mp3>)\n\n" +
            "Podcast: Example Podcast\n\n#xpod #podcast",
        CloudMemoDrafts.episode(episode, "Example Podcast"),
    )
  }

  @Test
  fun articleDraftFallsBackToPlainTitleWithoutUrl() {
    val article =
        ArticleEntity(
            id = "article-id",
            feedId = "feed-id",
            stableKey = "stable-key",
            title = "Local article",
            author = "Author",
            content = "Content",
            url = null,
            publishedEpochMs = 1,
            artworkUrl = null,
        )

    assertEquals(
        "## Local article\n\nSource: Feed\n\nAuthor: Author\n\n#xpod #article",
        CloudMemoDrafts.article(article, "Feed"),
    )
  }

  @Test
  fun feedMetadataIsEscapedAsMarkdownLiteralText() {
    val article =
        ArticleEntity(
            id = "article-id",
            feedId = "feed-id",
            stableKey = "stable-key",
            title = "#Launch *notes*",
            author = "A_[uthor]\nTeam",
            content = "Content",
            url = null,
            publishedEpochMs = 1,
            artworkUrl = null,
        )

    assertEquals(
        "## \\#Launch \\*notes\\*\n\n" +
            "Source: Feed \\#One\n\n" +
            "Author: A\\_\\[uthor\\] Team\n\n#xpod #article",
        CloudMemoDrafts.article(article, "Feed #One"),
    )
  }
}
