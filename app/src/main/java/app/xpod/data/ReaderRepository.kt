package app.xpod.data

import androidx.room.withTransaction
import app.xpod.util.runCatchingCancellable
import java.io.ByteArrayInputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Singleton
class ReaderRepository
@Inject
constructor(
    private val database: XpodDatabase,
    private val feedFetcher: FeedFetcher,
    private val parser: ArticleFeedParser,
    private val clock: Clock,
) {
  fun feeds(): Flow<List<ArticleFeedEntity>> = database.articleFeeds().observeAll()

  fun articles(): Flow<List<ArticleEntity>> = database.articles().observeAll()

  suspend fun addOrRefresh(url: String): Result<Unit> =
      withContext(Dispatchers.IO) {
        runCatchingCancellable {
          val bytes = feedFetcher.fetch(url, FeedRequestType.Articles)
          val parsed = parser.parse(ByteArrayInputStream(bytes), url)
          save(url, parsed)
        }
      }

  internal suspend fun save(url: String, parsed: ParsedArticleFeed) {
    val feedId = FeedId.from(url)
    database.withTransaction {
      val existing = database.articles().allForFeed(feedId).associateBy { it.stableKey }
      database
          .articleFeeds()
          .upsert(
              ArticleFeedEntity(
                  feedId,
                  url,
                  parsed.title,
                  parsed.author,
                  parsed.description,
                  parsed.artworkUrl,
                  clock.millis(),
              )
          )
      database
          .articles()
          .upsertAll(
              parsed.articles.map { article ->
                val previous = existing[article.stableKey]
                ArticleEntity(
                    FeedId.from("$feedId:${article.stableKey}"),
                    feedId,
                    article.stableKey,
                    article.title,
                    article.author,
                    article.content,
                    article.url,
                    article.publishedEpochMs,
                    article.artworkUrl,
                    previous?.isRead ?: false,
                    previous?.isFavorite ?: false,
                )
              }
          )
    }
  }

  suspend fun refresh(feedUrls: List<String>): List<Throwable> = coroutineScope {
    val concurrency = Semaphore(MAX_CONCURRENT_REFRESHES)
    feedUrls
        .map { url -> async { concurrency.withPermit { addOrRefresh(url).exceptionOrNull() } } }
        .awaitAll()
        .filterNotNull()
  }

  suspend fun refreshAll(): FeedRefreshResult {
    val errors = refresh(database.articleFeeds().all().map(ArticleFeedEntity::feedUrl))
    return FeedRefreshResult(errors.any(::shouldRetryFeedRefresh))
  }

  suspend fun markRead(id: String) = database.articles().markRead(id)

  suspend fun setRead(id: String, read: Boolean) = database.articles().setRead(id, read)

  suspend fun toggleFavorite(id: String) = database.articles().toggleFavorite(id)

  suspend fun remove(feedId: String) =
      withContext(Dispatchers.IO) { database.articleFeeds().delete(feedId) }

  suspend fun allFeeds(): List<ArticleFeedEntity> = database.articleFeeds().all()

  private companion object {
    const val MAX_CONCURRENT_REFRESHES = 4
  }
}
