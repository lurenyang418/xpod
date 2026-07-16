package app.xpod.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedParserTest {
  private val parser = FeedParser()

  @Test
  fun parsesRssAndUsesAudioUrlWhenGuidIsMissing() {
    val feed =
        """
        <rss><channel><title>Example</title><itunes:author xmlns:itunes="x">Host</itunes:author>
        <item><title>First</title><enclosure url="https://cdn.example.com/first.mp3" type="audio/mpeg"/></item>
        </channel></rss>
        """
            .trimIndent()

    val result = parser.parse(ByteArrayInputStream(feed.toByteArray()))

    assertEquals("Example", result.title)
    assertEquals("Host", result.author)
    assertEquals(1, result.episodes.size)
    assertEquals("https://cdn.example.com/first.mp3", result.episodes.single().stableKey)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsNonHttpsEpisodeAudio() {
    val feed =
        "<rss><channel><item><title>Unsafe</title><enclosure url=\"http://example.com/file.mp3\"/></item></channel></rss>"
    parser.parse(ByteArrayInputStream(feed.toByteArray()))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsXmlThatIsNotAnRssFeed() {
    parser.parse(ByteArrayInputStream("<document><title>Not RSS</title></document>".toByteArray()))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsNonAudioEnclosures() {
    val feed =
        "<rss><channel><item><title>Article attachment</title><enclosure url=\"https://example.com/image.jpg\" type=\"image/jpeg\"/></item></channel></rss>"
    parser.parse(ByteArrayInputStream(feed.toByteArray()))
  }

  @Test
  fun stripsShownoteHtml() {
    val feed =
        "<rss><channel><item><title>Notes</title><description><![CDATA[<p>Hello <b>world</b></p><p>Second line</p>]]></description><enclosure url=\"https://example.com/file.mp3\"/></item></channel></rss>"
    assertEquals(
        "Hello world\nSecond line",
        parser.parse(ByteArrayInputStream(feed.toByteArray())).episodes.single().description,
    )
  }

  @Test
  fun stripsEntityEscapedShownoteHtml() {
    val feed =
        "<rss><channel><item><title>Notes</title><description>&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;</description><enclosure url=\"https://example.com/file.mp3\"/></item></channel></rss>"
    assertEquals(
        "Hello",
        parser.parse(ByteArrayInputStream(feed.toByteArray())).episodes.single().description,
    )
  }

  @Test
  fun parsesEpisodePublicationDateAndDuration() {
    val feed =
        """
        <rss xmlns:itunes="x"><channel><item><title>Timed</title>
        <pubDate>Tue, 14 Jul 2026 12:30:00 GMT</pubDate><itunes:duration>1:02:03</itunes:duration>
        <enclosure url="https://example.com/file.mp3"/></item></channel></rss>
        """
            .trimIndent()

    val episode = parser.parse(ByteArrayInputStream(feed.toByteArray())).episodes.single()

    assertEquals(1_784_032_200_000L, episode.publishedEpochMs)
    assertEquals(3_723_000L, episode.durationMs)
  }
}
