package app.xpod.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleFeedParserTest {
  private val parser = ArticleFeedParser()

  @Test
  fun parsesRssArticleContentAndPublicationDate() {
    val feed =
        """<rss><channel><title>News</title><item><guid>a</guid><title>Hello</title><description><![CDATA[<p>Body</p>]]></description><link>https://example.com/a</link><pubDate>Tue, 14 Jul 2026 12:30:00 GMT</pubDate></item></channel></rss>"""
    val result = parser.parse(ByteArrayInputStream(feed.toByteArray()))
    assertEquals("News", result.title)
    assertEquals(1, result.articles.size)
    assertEquals("a", result.articles.single().stableKey)
    assertEquals("<p>Body</p>", result.articles.single().content)
    assertEquals(1_784_032_200_000L, result.articles.single().publishedEpochMs)
  }

  @Test
  fun parsesAtomEntriesAndUsesLinkAsFallbackKey() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atoms</title><entry><title>One</title><link href="https://example.com/one"/><summary>Summary</summary><updated>2026-07-14T12:30:00Z</updated></entry></feed>"""
    val result = parser.parse(ByteArrayInputStream(feed.toByteArray()))
    assertEquals("Atoms", result.title)
    assertEquals("https://example.com/one", result.articles.single().stableKey)
    assertTrue(result.articles.single().content.contains("Summary"))
  }

  @Test
  fun prefersRssFullContentOverEarlierDescription() {
    val feed =
        """<rss xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>News</title><item><guid>a</guid><title>Hello</title><description>Short summary</description><content:encoded><![CDATA[<p>The complete article body.</p>]]></content:encoded></item></channel></rss>"""

    val article = parser.parse(ByteArrayInputStream(feed.toByteArray())).articles.single()

    assertEquals("<p>The complete article body.</p>", article.content)
  }

  @Test
  fun prefersAtomContentOverEarlierSummary() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atoms</title><entry><id>a</id><title>One</title><summary>Short summary</summary><content type="html"><![CDATA[<p>The first part.</p>]]><![CDATA[<p>The second part.</p>]]></content></entry></feed>"""

    val article = parser.parse(ByteArrayInputStream(feed.toByteArray())).articles.single()

    assertEquals("<p>The first part.</p><p>The second part.</p>", article.content)
  }

  @Test
  fun upgradesSameHostArticleLinksForHttpsFeeds() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atoms</title><entry><id>a</id><title>One</title><link rel="alternate" href="http://www.example.com/posts/one"/><content>Body</content></entry></feed>"""

    val article =
        parser
            .parse(
                ByteArrayInputStream(feed.toByteArray()),
                "https://www.example.com/feed.xml",
            )
            .articles
            .single()

    assertEquals("https://www.example.com/posts/one", article.url)
  }

  @Test
  fun escapesAtomTextContentInsteadOfTreatingItAsHtml() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atoms</title><entry><id>a</id><title>One</title><content type="text">Use &lt;strong&gt;literally&lt;/strong&gt;</content></entry></feed>"""

    val article = parser.parse(ByteArrayInputStream(feed.toByteArray())).articles.single()

    assertEquals("Use &lt;strong&gt;literally&lt;/strong&gt;", article.content)
  }

  @Test
  fun preservesAtomXhtmlContentStructure() {
    val feed =
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atoms</title><entry><id>a</id><title>One</title><content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hello <strong>reader</strong>.</p></div></content></entry></feed>"""

    val article = parser.parse(ByteArrayInputStream(feed.toByteArray())).articles.single()

    assertTrue(article.content.contains("<p>Hello <strong>reader</strong>.</p>"))
  }
}
