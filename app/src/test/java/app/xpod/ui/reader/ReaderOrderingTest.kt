package app.xpod.ui.reader

import app.xpod.data.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderOrderingTest {
  @Test
  fun unreadArticlesAppearBeforeReadArticlesAndKeepNewestFirstWithinStatus() {
    val articles =
        listOf(
            article(id = "read-new", isRead = true, publishedEpochMs = 300L),
            article(id = "unread-old", isRead = false, publishedEpochMs = 100L),
            article(id = "unread-new", isRead = false, publishedEpochMs = 200L),
            article(id = "read-old", isRead = true, publishedEpochMs = 50L),
        )

    assertEquals(
        listOf("unread-new", "unread-old", "read-new", "read-old"),
        orderReaderArticles(articles).map(ArticleEntity::id),
    )
  }

  private fun article(id: String, isRead: Boolean, publishedEpochMs: Long) =
      ArticleEntity(
          id = id,
          feedId = "feed",
          stableKey = id,
          title = id,
          author = "author",
          content = "content",
          url = null,
          publishedEpochMs = publishedEpochMs,
          artworkUrl = null,
          isRead = isRead,
      )
}
