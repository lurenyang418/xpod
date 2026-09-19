package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudMemoDraftsTest {
  @Test
  fun markdownNoteIncludesSeparateTitleAndPreservesMarkdownBody() {
    assertEquals(
        CloudMemoNoteDraft("# Local title\n\n## Section\nBody", omittedLocalImages = false),
        CloudMemoDrafts.markdownNote(" Local title ", "## Section\nBody"),
    )
  }

  @Test
  fun markdownNoteDoesNotDuplicateMatchingHeading() {
    assertEquals(
        CloudMemoNoteDraft("# Local title\n\nBody", omittedLocalImages = false),
        CloudMemoDrafts.markdownNote("Local title", "# Local title\n\nBody"),
    )
  }

  @Test
  fun markdownNoteReplacesLocalImageWithAltText() {
    val result =
        CloudMemoDrafts.markdownNote(
            "Note",
            "Before ![photo\\] one](xpod-attachment://01234567-89ab-cdef-0123-456789abcdef.png) after",
        )

    assertEquals("# Note\n\nBefore photo\\] one after", result.content)
    assertEquals(true, result.omittedLocalImages)
  }

  @Test
  fun markdownNoteLeavesEmptyUntitledContentEmpty() {
    assertEquals(
        CloudMemoNoteDraft("", omittedLocalImages = false),
        CloudMemoDrafts.markdownNote("  ", ""),
    )
  }

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
