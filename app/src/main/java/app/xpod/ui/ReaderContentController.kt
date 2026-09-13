package app.xpod.ui

import android.content.Context
import android.util.Log
import app.xpod.R
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.ReaderRepository
import app.xpod.util.runCatchingCancellable
import java.util.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ReaderContentState(
    val articleFeeds: List<ArticleFeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val isRefreshing: Boolean = false,
)

/** Owns article/feed aggregation, summaries, refresh and article status operations. */
internal class ReaderContentController(
    private val reader: ReaderRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showStatus: (String, StatusSeverity) -> Unit,
) {
  private val refreshing = MutableStateFlow(false)

  val state: StateFlow<ReaderContentState> =
      combine(reader.feeds(), reader.articles(), refreshing) { feeds, articles, isRefreshing ->
            ReaderContentState(
                articleFeeds = feeds,
                articles = articles,
                isRefreshing = isRefreshing,
            )
          }
          .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ReaderContentState())

  private val summaryParser = ArticleContentParser()
  private val summaryCache = HashMap<String, Pair<Int, String>>()
  val articleSummaries: StateFlow<Map<String, String>> =
      reader
          .articles()
          .map { articles ->
            val summaries = HashMap<String, String>(articles.size)
            articles.forEach { article ->
              val hash = article.content.hashCode()
              val cached = summaryCache[article.id]
              summaries[article.id] =
                  if (cached != null && cached.first == hash) cached.second
                  else
                      summaryParser.plainText(article.content).also {
                        summaryCache[article.id] = hash to it
                      }
            }
            summaryCache.keys.retainAll(summaries.keys)
            summaries
          }
          .flowOn(Dispatchers.Default)
          .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

  fun removeFeed(id: String) = scope.launch {
    reader.remove(id)
    showStatus(context.getString(R.string.subscription_removed), StatusSeverity.Info)
  }

  fun markRead(id: String) = scope.launch { reader.markRead(id) }

  fun refresh(feedUrl: String?) = scope.launch {
    if (refreshing.value) return@launch
    refreshing.value = true
    try {
      val urls = feedUrl?.let(::listOf) ?: reader.allFeeds().map(ArticleFeedEntity::feedUrl)
      val failures = reader.refresh(urls)
      failures.forEach { Log.w("XPOD", "Unable to refresh article feed", it) }
      if (failures.isNotEmpty()) {
        showStatus(context.getString(R.string.could_not_refresh_feed), StatusSeverity.Error)
      }
    } finally {
      refreshing.value = false
    }
  }

  fun setRead(id: String, read: Boolean) = scope.launch { reader.setRead(id, read) }

  fun toggleFavorite(id: String) = scope.launch { reader.toggleFavorite(id) }
}
