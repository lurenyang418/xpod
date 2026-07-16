package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFeedParserTest {
  private val parser = SubscriptionFeedParser(FeedParser(), ArticleFeedParser())

  @Test
  fun classifiesPlayableAudioFeedAsPodcast() {
    val feed =
        """<rss><channel><title>Audio</title><item><guid>one</guid><title>One</title><enclosure url="https://example.com/one.mp3" type="audio/mpeg"/></item></channel></rss>"""

    val result = parser.parse(feed.toByteArray(), "https://example.com/feed.xml")

    assertTrue(result is ParsedSubscription.Podcast)
  }

  @Test
  fun classifiesAtomEntriesWithoutAudioAsArticles() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Writing</title><entry><id>one</id><title>One</title><content type="html">Body</content></entry></feed>"""

    val result = parser.parse(feed.toByteArray(), "https://example.com/feed.xml")

    assertTrue(result is ParsedSubscription.Articles)
  }

  @Test
  fun classifiesEmptyAtomFeedAsArticlesWithoutTryingPodcastParsing() {
    val feed =
        """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Coming soon</title></feed>"""

    val result = parser.parse(feed.toByteArray(), "https://example.com/feed.xml")

    assertTrue(result is ParsedSubscription.Articles)
    assertEquals(0, (result as ParsedSubscription.Articles).feed.articles.size)
  }

  @Test
  fun preservesEmptyPodcastFeedSupport() {
    val feed = """<rss><channel><title>Coming soon</title></channel></rss>"""

    val result = parser.parse(feed.toByteArray(), "https://example.com/feed.xml")

    assertTrue(result is ParsedSubscription.Podcast)
    assertEquals(0, (result as ParsedSubscription.Podcast).feed.episodes.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsXmlThatIsNeitherPodcastNorArticleFeed() {
    parser.parse(
        "<document><title>Not a feed</title></document>".toByteArray(),
        "https://example.com/not-a-feed.xml",
    )
  }
}
