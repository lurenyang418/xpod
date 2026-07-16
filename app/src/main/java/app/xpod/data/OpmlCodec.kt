package app.xpod.data

import java.io.InputStream
import java.io.OutputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object OpmlCodec {
  fun read(input: InputStream): List<String> {
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
    val urls = linkedSetOf<String>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("outline", true)) {
        parser.getAttributeValue(null, "xmlUrl")?.trim()?.takeIf { it.isNotEmpty() }?.let(urls::add)
      }
    }
    return urls.toList()
  }

  fun write(
      output: OutputStream,
      podcasts: List<PodcastEntity>,
      articleFeeds: List<ArticleFeedEntity> = emptyList(),
  ) {
    fun escape(value: String) =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
    val content = buildString {
      append(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\"><head><title>XPOD subscriptions</title></head><body>\n"
      )
      podcasts.forEach {
        append("<outline text=\"")
            .append(escape(it.title))
            .append("\" type=\"rss\" xmlUrl=\"")
            .append(escape(it.feedUrl))
            .append("\"/>\n")
      }
      articleFeeds.forEach {
        append("<outline text=\"")
            .append(escape(it.title))
            .append("\" type=\"rss\" xmlUrl=\"")
            .append(escape(it.feedUrl))
            .append("\"/>")
            .append('\n')
      }
      append("</body></opml>\n")
    }
    output.write(content.toByteArray(Charsets.UTF_8))
  }
}
