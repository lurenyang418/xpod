package app.xpod.data

import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ParsedArticleFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val articles: List<ParsedArticle>,
)

data class ParsedArticle(
    val stableKey: String,
    val title: String,
    val author: String,
    val content: String,
    val url: String?,
    val publishedEpochMs: Long,
    val artworkUrl: String?,
)

class ArticleFeedParser @Inject constructor() {
  fun parse(input: InputStream, sourceUrl: String? = null): ParsedArticleFeed {
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG) {
        return when (parser.name.lowercase()) {
          "rss",
          "rdf:rdf" -> parseRss(parser, sourceUrl)
          "feed" -> parseAtom(parser, sourceUrl)
          else -> {
            parser.next()
            continue
          }
        }
      }
      parser.next()
    }
    throw IllegalArgumentException("Unsupported feed format")
  }

  private fun parseRss(parser: XmlPullParser, sourceUrl: String?): ParsedArticleFeed {
    var title = "Untitled feed"
    var author = ""
    var description = ""
    var image: String? = null
    val articles = mutableListOf<ParsedArticle>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name.lowercase()) {
        "title" ->
            if (title == "Untitled feed") title = parser.nextText().trim() else parser.nextText()
        "description",
        "subtitle" -> if (description.isBlank()) description = plainText(parser.nextText())
        "managingeditor",
        "author",
        "itunes:author" -> if (author.isBlank()) author = parser.nextText().trim()
        "image",
        "itunes:image" ->
            image =
                parser.getAttributeValue(null, "href")
                    ?: parser.getAttributeValue(null, "url")
                    ?: image
        "item" -> articles += parseRssItem(parser, sourceUrl)
      }
    }
    return ParsedArticleFeed(
        title,
        author,
        description,
        image,
        articles.distinctBy { it.stableKey },
    )
  }

  private fun parseRssItem(parser: XmlPullParser, sourceUrl: String?): ParsedArticle {
    var title = "Untitled article"
    var author = ""
    var summary = ""
    var fullContent = ""
    var guid = ""
    var url: String? = null
    var image: String? = null
    var published = 0L
    val depth = parser.depth
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (
          parser.eventType == XmlPullParser.END_TAG &&
              parser.depth == depth &&
              parser.name.equals("item", true)
      )
          break
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name.lowercase()) {
        "title" -> title = parser.nextText().trim()
        "description",
        "summary" -> if (summary.isBlank()) summary = parser.nextText().trim()
        "content:encoded",
        "encoded" -> if (fullContent.isBlank()) fullContent = parser.nextText().trim()
        "guid" -> guid = parser.nextText().trim()
        "link" -> url = normalizeArticleUrl(parser.nextText(), sourceUrl) ?: url
        "author",
        "dc:creator" -> author = parser.nextText().trim()
        "pubdate",
        "dc:date",
        "published",
        "updated" -> published = parseDate(parser.nextText())
        "image",
        "itunes:image",
        "media:thumbnail" ->
            image =
                parser.getAttributeValue(null, "href")
                    ?: parser.getAttributeValue(null, "url")
                    ?: image
      }
    }
    val key = guid.ifBlank { url.orEmpty() }.ifBlank { "$title:$published" }
    return ParsedArticle(key, title, author, fullContent.ifBlank { summary }, url, published, image)
  }

  private fun parseAtom(parser: XmlPullParser, sourceUrl: String?): ParsedArticleFeed {
    var title = "Untitled feed"
    var author = ""
    var description = ""
    var image: String? = null
    val articles = mutableListOf<ParsedArticle>()
    val depth = parser.depth
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (
          parser.eventType == XmlPullParser.END_TAG &&
              parser.depth == depth &&
              parser.name.equals("feed", true)
      )
          break
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name.lowercase()) {
        "title" -> title = parser.nextText().trim()
        "subtitle" -> description = parser.nextText().trim()
        "icon",
        "logo" -> image = parser.nextText().trim()
        "author" -> author = parseAuthor(parser)
        "entry" -> articles += parseAtomEntry(parser, sourceUrl)
      }
    }
    return ParsedArticleFeed(
        title,
        author,
        description,
        image,
        articles.distinctBy { it.stableKey },
    )
  }

  private fun parseAtomEntry(parser: XmlPullParser, sourceUrl: String?): ParsedArticle {
    var title = "Untitled article"
    var author = ""
    var summary = ""
    var fullContent = ""
    var id = ""
    var url: String? = null
    var image: String? = null
    var published = 0L
    val depth = parser.depth
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (
          parser.eventType == XmlPullParser.END_TAG &&
              parser.depth == depth &&
              parser.name.equals("entry", true)
      )
          break
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name.lowercase()) {
        "title" -> title = parser.nextText().trim()
        "id" -> id = parser.nextText().trim()
        "summary" -> {
          val value = readAtomTextConstruct(parser)
          if (summary.isBlank()) summary = value
        }
        "content" -> {
          val value = readAtomTextConstruct(parser)
          if (fullContent.isBlank()) fullContent = value
        }
        "published",
        "updated" -> published = parseDate(parser.nextText())
        "author" -> author = parseAuthor(parser)
        "link" ->
            if (parser.getAttributeValue(null, "rel") != "self")
                url = normalizeArticleUrl(parser.getAttributeValue(null, "href"), sourceUrl) ?: url
        "icon",
        "logo" -> image = parser.nextText().trim()
      }
    }
    val key = id.ifBlank { url.orEmpty() }.ifBlank { "$title:$published" }
    return ParsedArticle(key, title, author, fullContent.ifBlank { summary }, url, published, image)
  }

  private fun parseAuthor(parser: XmlPullParser): String {
    val depth = parser.depth
    var name = ""
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
      if (parser.eventType == XmlPullParser.START_TAG && parser.name.equals("name", true))
          name = parser.nextText().trim()
    }
    return name
  }

  private fun parseDate(value: String): Long {
    val text = value.trim()
    return listOf<() -> Long>(
            {
              ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                  .toInstant()
                  .toEpochMilli()
            },
            { Instant.parse(text).toEpochMilli() },
            { OffsetDateTime.parse(text).toInstant().toEpochMilli() },
            { LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        .firstNotNullOfOrNull { runCatching { it() }.getOrNull() } ?: 0L
  }

  private fun readAtomTextConstruct(parser: XmlPullParser): String {
    if (!parser.getAttributeValue(null, "src").isNullOrBlank()) {
      readTextContent(parser)
      return ""
    }
    return when (parser.getAttributeValue(null, "type")?.trim()?.lowercase() ?: "text") {
      "html",
      "text/html" -> readTextContent(parser).trim()
      "xhtml",
      "application/xhtml+xml" -> readMarkupContent(parser).trim()
      else -> escapeHtml(readTextContent(parser).trim())
    }
  }

  private fun readTextContent(parser: XmlPullParser): String {
    val depth = parser.depth
    return buildString {
      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
        if (
            parser.eventType in
                setOf(
                    XmlPullParser.TEXT,
                    XmlPullParser.CDSECT,
                    XmlPullParser.ENTITY_REF,
                    XmlPullParser.IGNORABLE_WHITESPACE,
                )
        ) {
          append(parser.text.orEmpty())
        }
      }
    }
  }

  private fun readMarkupContent(parser: XmlPullParser): String {
    val depth = parser.depth
    return buildString {
      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
          XmlPullParser.START_TAG -> {
            append('<').append(parser.name)
            repeat(parser.attributeCount) { index ->
              append(' ')
                  .append(parser.getAttributeName(index))
                  .append("=\"")
                  .append(escapeAttribute(parser.getAttributeValue(index)))
                  .append('"')
            }
            append('>')
          }
          XmlPullParser.END_TAG -> {
            if (parser.depth == depth) break
            append("</").append(parser.name).append('>')
          }
          XmlPullParser.TEXT,
          XmlPullParser.CDSECT,
          XmlPullParser.ENTITY_REF,
          XmlPullParser.IGNORABLE_WHITESPACE -> append(escapeHtml(parser.text.orEmpty()))
        }
      }
    }
  }

  private fun escapeHtml(value: String): String =
      value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  private fun escapeAttribute(value: String): String = escapeHtml(value).replace("\"", "&quot;")

  private fun plainText(value: String) = value.replace(Regex("<[^>]*>"), "").trim()

  private fun normalizeArticleUrl(value: String?, sourceUrl: String?): String? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
          val source = sourceUrl?.let(::URI)
          val resolved = source?.resolve(text) ?: URI(text)
          if (
              source?.scheme.equals("https", ignoreCase = true) &&
                  resolved.scheme.equals("http", ignoreCase = true) &&
                  source?.host.equals(resolved.host, ignoreCase = true)
          ) {
            URI(
                    "https",
                    resolved.userInfo,
                    resolved.host,
                    if (resolved.port == 80) -1 else resolved.port,
                    resolved.path,
                    resolved.query,
                    resolved.fragment,
                )
                .toString()
          } else {
            resolved.toString()
          }
        }
        .getOrDefault(text)
  }
}
