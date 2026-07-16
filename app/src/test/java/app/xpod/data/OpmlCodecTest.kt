package app.xpod.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlCodecTest {
  @Test
  fun preservesUniqueFeedUrlsSoRejectedImportsCanBeReported() {
    val source =
        "<opml><body><outline xmlUrl=\"https://one.example/feed.xml\"/><outline xmlUrl=\"https://one.example/feed.xml\"/><outline xmlUrl=\"http://unsafe.example/feed.xml\"/></body></opml>"
    val urls = OpmlCodec.read(ByteArrayInputStream(source.toByteArray()))
    assertEquals(
        listOf("https://one.example/feed.xml", "http://unsafe.example/feed.xml"),
        urls,
    )

    val output = ByteArrayOutputStream()
    OpmlCodec.write(output, listOf(PodcastEntity("id", urls.first(), "One", "", "", null)))
    assertEquals(
        listOf("https://one.example/feed.xml"),
        OpmlCodec.read(ByteArrayInputStream(output.toByteArray())),
    )
  }

  @Test
  fun exportsPodcastAndArticleFeedsTogether() {
    val output = ByteArrayOutputStream()
    OpmlCodec.write(
        output,
        podcasts =
            listOf(PodcastEntity("podcast", "https://pod.example/feed", "Podcast", "", "", null)),
        articleFeeds =
            listOf(
                ArticleFeedEntity("articles", "https://news.example/feed", "News", "", "", null)
            ),
    )
    val urls = OpmlCodec.read(ByteArrayInputStream(output.toByteArray()))
    assertEquals(listOf("https://pod.example/feed", "https://news.example/feed"), urls)
    assertTrue(String(output.toByteArray()).contains("News"))
  }
}
