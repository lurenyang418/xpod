package app.xpod.ui.podcasts

import android.content.Context
import android.util.Log
import app.xpod.R
import app.xpod.data.EpisodeEntity
import app.xpod.data.FeedHttpException
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastRepository
import app.xpod.data.SubscriptionRepository
import app.xpod.ui.shared.StatusSeverity
import app.xpod.data.UnsupportedFeedUrlException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException

internal data class PodcastContentState(
    val podcasts: List<PodcastEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val newEpisodeCounts: Map<String, Int> = emptyMap(),
    val unplayedEpisodeCounts: Map<String, Int> = emptyMap(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
)

/** Owns podcast/library data aggregation and podcast feed operations. */
internal class PodcastContentController(
    private val podcasts: PodcastRepository,
    private val subscriptions: SubscriptionRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showStatus: (String, StatusSeverity) -> Unit,
) {
  private val refreshing = MutableStateFlow(false)

  val state: StateFlow<PodcastContentState> =
      combine(podcasts.podcasts(), podcasts.allEpisodes(), refreshing) { all, library, isRefreshing ->
            PodcastContentState(
                podcasts = all,
                isRefreshing = isRefreshing,
                newEpisodeCounts =
                    library.filter { it.isNew }.groupingBy { it.podcastId }.eachCount(),
                unplayedEpisodeCounts =
                    library.filterNot { it.isPlayed }.groupingBy { it.podcastId }.eachCount(),
                libraryEpisodes = library,
            )
          }
          .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PodcastContentState())

  fun addFeed(url: String, onComplete: (Boolean) -> Unit = {}) = scope.launch {
    subscriptions
        .addOrRefresh(url)
        .fold(
            {
              showStatus(context.getString(R.string.added_and_refreshed), StatusSeverity.Info)
              onComplete(true)
            },
            { error ->
              Log.e("XPOD", "Unable to add feed", error)
              showStatus(
                  context.getString(
                      R.string.could_not_add_feed_reason,
                      feedFailureReason(error),
                  ),
                  StatusSeverity.Error,
              )
              onComplete(false)
            },
        )
  }

  fun refresh(feedUrl: String) = scope.launch {
    if (refreshing.value) return@launch
    refreshing.value = true
    try {
      podcasts.addOrRefresh(feedUrl).onFailure {
        showStatus(context.getString(R.string.could_not_refresh_feed), StatusSeverity.Error)
      }
    } finally {
      refreshing.value = false
    }
  }

  fun refreshAll() = scope.launch {
    if (refreshing.value) return@launch
    refreshing.value = true
    try {
      val result = podcasts.refreshAll()
      if (result.failureCount > 0) {
        val total = result.refreshedCount + result.failureCount
        showStatus(
            context.resources.getQuantityString(
                R.plurals.podcasts_refreshed_with_failures,
                total,
                result.refreshedCount,
                total,
                result.failureCount,
            ),
            StatusSeverity.Error,
        )
      }
    } finally {
      refreshing.value = false
    }
  }

  fun toggleFavorite(id: String) = scope.launch { podcasts.toggleFavorite(id) }

  fun markPlayed(id: String, played: Boolean) = scope.launch {
    podcasts.setPlayed(id, played)
  }

  private fun feedFailureReason(error: Throwable): String =
      when (error) {
        is UnsupportedFeedUrlException -> context.getString(R.string.feed_error_https_required)
        is FeedHttpException -> context.getString(R.string.feed_error_http, error.statusCode)
        is IOException -> context.getString(R.string.feed_error_network)
        is XmlPullParserException -> context.getString(R.string.feed_error_format)
        is IllegalArgumentException -> context.getString(R.string.feed_error_format)
        else -> context.getString(R.string.feed_error_format)
      }
}
