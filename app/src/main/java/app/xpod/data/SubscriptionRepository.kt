package app.xpod.data

import app.xpod.util.runCatchingCancellable
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

sealed interface ParsedSubscription {
  data class Podcast(val feed: ParsedFeed) : ParsedSubscription

  data class Articles(val feed: ParsedArticleFeed) : ParsedSubscription
}

class SubscriptionFeedParser
@Inject
constructor(
    private val podcastParser: FeedParser,
    private val articleParser: ArticleFeedParser,
) {
  fun parse(bytes: ByteArray, sourceUrl: String): ParsedSubscription {
    if (rootElementName(bytes) == "feed") {
      return ParsedSubscription.Articles(
          articleParser.parse(ByteArrayInputStream(bytes), sourceUrl)
      )
    }
    val podcast = runCatching { podcastParser.parse(ByteArrayInputStream(bytes)) }
    podcast
        .getOrNull()
        ?.takeIf { it.episodes.isNotEmpty() }
        ?.let {
          return ParsedSubscription.Podcast(it)
        }

    val articles = runCatching { articleParser.parse(ByteArrayInputStream(bytes), sourceUrl) }
    articles
        .getOrNull()
        ?.takeIf { it.articles.isNotEmpty() }
        ?.let {
          return ParsedSubscription.Articles(it)
        }

    // Preserve support for valid podcast feeds that have not published an episode yet.
    podcast.getOrNull()?.let {
      return ParsedSubscription.Podcast(it)
    }
    articles.getOrNull()?.let {
      return ParsedSubscription.Articles(it)
    }

    throw unsupportedFeed(podcast.exceptionOrNull(), articles.exceptionOrNull())
  }

  private fun rootElementName(bytes: ByteArray): String? =
      runCatching {
            val parser =
                XmlPullParserFactory.newInstance().newPullParser().apply {
                  setInput(ByteArrayInputStream(bytes), null)
                }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
              if (parser.eventType == XmlPullParser.START_TAG)
                  return@runCatching parser.name.lowercase()
              parser.next()
            }
            null
          }
          .getOrNull()

  private fun unsupportedFeed(podcastError: Throwable?, articleError: Throwable?): Throwable {
    val error = IllegalArgumentException("Unsupported podcast or article feed", podcastError)
    if (articleError != null && articleError !== podcastError) error.addSuppressed(articleError)
    return error
  }
}

data class OpmlImportFailure(val url: String, val error: Throwable)

data class OpmlImportReport(
    val attempted: Int,
    val imported: Int,
    val failures: List<OpmlImportFailure>,
)

@Singleton
class SubscriptionRepository
@Inject
constructor(
    private val feedFetcher: FeedFetcher,
    private val parser: SubscriptionFeedParser,
    private val podcasts: PodcastRepository,
    private val reader: ReaderRepository,
) {
  suspend fun addOrRefresh(url: String): Result<Unit> =
      withContext(Dispatchers.IO) {
        runCatchingCancellable {
          when (
              val parsed = parser.parse(feedFetcher.fetch(url, FeedRequestType.Subscription), url)
          ) {
            is ParsedSubscription.Podcast -> podcasts.save(url, parsed.feed)
            is ParsedSubscription.Articles -> {
              reader.save(url, parsed.feed)
              podcasts.removeEmptySubscription(url)
            }
          }
        }
      }

  suspend fun importOpml(input: InputStream): Result<OpmlImportReport> =
      withContext(Dispatchers.IO) {
        runCatchingCancellable {
          val urls = OpmlCodec.read(input)
          val concurrency = Semaphore(MAX_CONCURRENT_IMPORTS)
          val results = coroutineScope {
            urls
                .map { url ->
                  async {
                    val result = concurrency.withPermit { addOrRefresh(url) }
                    url to result
                  }
                }
                .awaitAll()
          }
          val failures = results.mapNotNull { (url, result) ->
            result.exceptionOrNull()?.let { OpmlImportFailure(url, it) }
          }
          OpmlImportReport(
              attempted = urls.size,
              imported = urls.size - failures.size,
              failures = failures,
          )
        }
      }

  private companion object {
    const val MAX_CONCURRENT_IMPORTS = 4
  }
}
