package app.xpod.data

import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ParsedFeed(val title: String, val author: String, val description: String, val artworkUrl: String?, val episodes: List<ParsedEpisode>)
data class ParsedEpisode(val stableKey: String, val title: String, val description: String, val audioUrl: String, val publishedEpochMs: Long, val durationMs: Long?, val artworkUrl: String?)

class FeedParser @Inject constructor() {
    fun parse(input: InputStream): ParsedFeed {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
        var feedTitle = "Untitled podcast"
        var author = ""
        var description = ""
        var image: String? = null
        val episodes = mutableListOf<ParsedEpisode>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("item", true)) episodes += parseItem(parser)
            if (event == XmlPullParser.START_TAG && parser.name.equals("channel", true)) {
                val channel = parseChannel(parser)
                feedTitle = channel.title; author = channel.author; description = channel.description; image = channel.artworkUrl
                episodes += channel.episodes
            }
            event = parser.next()
        }
        return ParsedFeed(feedTitle, author, description, image, episodes.distinctBy { it.stableKey })
    }

    private fun parseChannel(parser: XmlPullParser): ParsedFeed {
        var title = "Untitled podcast"; var author = ""; var description = ""; var image: String? = null
        val episodes = mutableListOf<ParsedEpisode>()
        var depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth && parser.name.equals("channel", true)) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.lowercase()) {
                "title" -> title = parser.nextText().trim()
                "description", "subtitle", "summary" -> if (description.isBlank()) description = plainText(parser.nextText())
                "author", "itunes:author" -> author = parser.nextText().trim()
                "image", "itunes:image" -> image = parser.getAttributeValue(null, "href") ?: parser.getAttributeValue(null, "url") ?: image
                "item" -> episodes += parseItem(parser)
            }
        }
        return ParsedFeed(title, author, description, image, episodes)
    }

    private fun parseItem(parser: XmlPullParser): ParsedEpisode {
        var title = "Untitled episode"; var description = ""; var guid = ""; var audioUrl = ""; var image: String? = null
        var publishedEpochMs = 0L; var durationMs: Long? = null
        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth && parser.name.equals("item", true)) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.lowercase()) {
                "title" -> title = parser.nextText().trim()
                "description", "summary", "itunes:summary" -> if (description.isBlank()) description = plainText(parser.nextText())
                "guid" -> guid = parser.nextText().trim()
                "pubdate", "dc:date", "published", "updated" -> publishedEpochMs = parsePublishedEpochMs(parser.nextText())
                "itunes:duration", "duration" -> durationMs = parseDurationMs(parser.nextText())
                "enclosure" -> audioUrl = parser.getAttributeValue(null, "url") ?: audioUrl
                "image", "itunes:image" -> image = parser.getAttributeValue(null, "href") ?: parser.getAttributeValue(null, "url") ?: image
            }
        }
        require(audioUrl.startsWith("https://")) { "Episode audio must use HTTPS" }
        val key = guid.ifBlank { audioUrl }
        return ParsedEpisode(key, title, description, audioUrl, publishedEpochMs, durationMs, image)
    }

    fun id(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun parsePublishedEpochMs(value: String): Long {
        val text = value.trim()
        val parsers = listOf<() -> Long>(
            { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() },
            { Instant.parse(text).toEpochMilli() },
            { OffsetDateTime.parse(text).toInstant().toEpochMilli() },
            { LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        return parsers.firstNotNullOfOrNull { parser -> runCatching { parser() }.getOrNull() } ?: 0L
    }

    private fun parseDurationMs(value: String): Long? {
        val parts = value.trim().split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toDoubleOrNull() ?: return null)
            3 -> parts[0].toLongOrNull()?.times(3_600)?.plus(parts[1].toLongOrNull()?.times(60) ?: return null)?.plus(parts[2].toDoubleOrNull() ?: return null)
            else -> null
        } ?: return null
        return (seconds * 1_000).toLong().takeIf { it >= 0L }
    }

    private fun plainText(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("(?i)<(?:br\\s*/?|/p|/div|/li|/h[1-6])\\s*>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
