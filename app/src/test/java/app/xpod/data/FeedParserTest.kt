package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    val result = parser.parse(feed.toByteArray())

    assertEquals("Example", result.title)
    assertEquals("Host", result.author)
    assertEquals(1, result.episodes.size)
    assertEquals("https://cdn.example.com/first.mp3", result.episodes.single().stableKey)
  }

  @Test
  fun skipsNonHttpsEpisodeAudioButKeepsValidSiblings() {
    val feed =
        """
        <rss><channel><title>Mixed</title>
        <item><title>Unsafe</title><enclosure url="http://example.com/file.mp3"/></item>
        <item><title>Safe</title><enclosure url="https://example.com/safe.mp3"/></item>
        </channel></rss>
        """
            .trimIndent()

    val result = parser.parse(feed.toByteArray())

    assertEquals("Mixed", result.title)
    assertEquals(listOf("Safe"), result.episodes.map { it.title })
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsXmlThatIsNotAnRssFeed() {
    parser.parse("<document><title>Not RSS</title></document>".toByteArray())
  }

  @Test
  fun skipsNonAudioEnclosuresAndItemsWithoutEnclosures() {
    val feed =
        """
        <rss><channel><title>Mixed</title>
        <item><title>Article attachment</title><enclosure url="https://example.com/image.jpg" type="image/jpeg"/></item>
        <item><title>No enclosure</title></item>
        <item><title>Audio</title><enclosure url="https://example.com/audio.mp3" type="audio/mpeg"/></item>
        </channel></rss>
        """
            .trimIndent()

    val result = parser.parse(feed.toByteArray())

    assertEquals(listOf("Audio"), result.episodes.map { it.title })
  }

  @Test
  fun acceptsUppercaseHttpsSchemeInEpisodeAudio() {
    val feed =
        "<rss><channel><item><title>Loud</title><enclosure url=\"HTTPS://example.com/file.mp3\"/></item></channel></rss>"

    val episode = parser.parse(feed.toByteArray()).episodes.single()

    assertEquals("HTTPS://example.com/file.mp3", episode.audioUrl)
  }

  @Test
  fun channelTitleSurvivesAnImageBlockTitle() {
    val feed =
        """
        <rss><channel><title>Show</title>
        <image><url>https://example.com/artwork.png</url><title>Show Artwork</title></image>
        <item><title>First</title><enclosure url="https://example.com/first.mp3"/></item>
        </channel></rss>
        """
            .trimIndent()

    val result = parser.parse(feed.toByteArray())

    assertEquals("Show", result.title)
    assertEquals("First", result.episodes.single().title)
  }

  @Test
  fun readsChannelArtworkFromImageUrlChildElement() {
    val feed =
        """
        <rss><channel><title>Show</title>
        <image><title>Show Artwork</title><url> https://example.com/artwork.png </url></image>
        </channel></rss>
        """
            .trimIndent()

    assertEquals("https://example.com/artwork.png", parser.parse(feed.toByteArray()).artworkUrl)
  }

  @Test
  fun rejectsFeedsWithInternalDtdEntityDeclarations() {
    val feed =
        """
        <?xml version="1.0"?>
        <!-- prolog comment -->
        <!DOCTYPE rss [<!ENTITY bomb "boom">]>
        <rss><channel><title>&bomb;</title></channel></rss>
        """
            .trimIndent()

    assertThrows(IllegalArgumentException::class.java) { parser.parse(feed.toByteArray()) }
  }

  @Test
  fun stripsShownoteHtml() {
    val feed =
        "<rss><channel><item><title>Notes</title><description><![CDATA[<p>Hello <b>world</b></p><p>Second line</p>]]></description><enclosure url=\"https://example.com/file.mp3\"/></item></channel></rss>"
    assertEquals(
        "Hello world\nSecond line",
        parser.parse(feed.toByteArray()).episodes.single().description,
    )
  }

  @Test
  fun stripsEntityEscapedShownoteHtml() {
    val feed =
        "<rss><channel><item><title>Notes</title><description>&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;</description><enclosure url=\"https://example.com/file.mp3\"/></item></channel></rss>"
    assertEquals(
        "Hello",
        parser.parse(feed.toByteArray()).episodes.single().description,
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

    val episode = parser.parse(feed.toByteArray()).episodes.single()

    assertEquals(1_784_032_200_000L, episode.publishedEpochMs)
    assertEquals(3_723_000L, episode.durationMs)
  }
}
