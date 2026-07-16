package app.xpod.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.DownloadRepository
import app.xpod.data.EpisodeEntity
import app.xpod.data.FeedHttpException
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastRepository
import app.xpod.data.ReaderRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.SubscriptionRepository
import app.xpod.data.ThemeMode
import app.xpod.data.UnsupportedFeedUrlException
import app.xpod.playback.PlaybackController
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException

data class MainUiState(
    val podcasts: List<PodcastEntity> = emptyList(),
    val newEpisodeCounts: Map<String, Int> = emptyMap(),
    val selectedPodcastId: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
    val articleFeeds: List<ArticleFeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val isRefreshingArticles: Boolean = false,
    val status: String? = null,
)

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val podcasts: PodcastRepository,
    private val reader: ReaderRepository,
    private val subscriptions: SubscriptionRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val player: PlaybackController,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val selected = MutableStateFlow<String?>(null)
  private val status = MutableStateFlow<String?>(null)
  private val refreshingArticles = MutableStateFlow(false)
  @OptIn(ExperimentalCoroutinesApi::class)
  private val episodes = selected.flatMapLatest { id ->
    if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else podcasts.episodes(id)
  }
  private val podcastState =
      combine(podcasts.podcasts(), selected, episodes, podcasts.allEpisodes(), status) {
          all,
          id,
          items,
          library,
          message ->
        MainUiState(
            podcasts = all,
            newEpisodeCounts = library.filter { it.isNew }.groupingBy { it.podcastId }.eachCount(),
            selectedPodcastId = id,
            episodes = items,
            libraryEpisodes = library,
            status = message,
        )
      }
  val state: StateFlow<MainUiState> =
      combine(podcastState, reader.feeds(), reader.articles(), refreshingArticles) {
              base,
              articleFeeds,
              articles,
              refreshing ->
            base.copy(
                articleFeeds = articleFeeds,
                articles = articles,
                isRefreshingArticles = refreshing,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
  val dynamicColor =
      settings.useDynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
  val appTheme =
      settings.appTheme.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          ThemeMode.System,
      )
  val wifiOnlyDownloads =
      settings.useWifiOnlyDownloads.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          true,
      )
  val nowPlaying = player.nowPlaying
  val queue = player.queue
  val downloadStates = downloads.states

  init {
    viewModelScope.launch { settings.useWifiOnlyDownloads.collect { downloads.setWifiOnly(it) } }
  }

  fun selectPodcast(id: String?) {
    selected.value = id
    if (id != null) viewModelScope.launch { podcasts.markPodcastSeen(id) }
  }

  fun removePodcast(id: String) = viewModelScope.launch {
    podcasts.remove(id)
    if (selected.value == id) selected.value = null
    status.value = context.getString(R.string.subscription_removed)
  }

  fun removeArticleFeed(id: String) = viewModelScope.launch {
    reader.remove(id)
    status.value = context.getString(R.string.subscription_removed)
  }

  fun addFeed(url: String, onSuccess: () -> Unit = {}) = viewModelScope.launch {
    subscriptions
        .addOrRefresh(url)
        .fold(
            {
              status.value = context.getString(R.string.added_and_refreshed)
              onSuccess()
            },
            { error ->
              Log.e("XPOD", "Unable to add feed", error)
              status.value =
                  context.getString(
                      R.string.could_not_add_feed_reason,
                      feedFailureReason(error),
                  )
            },
        )
  }

  fun refresh(feedUrl: String) = viewModelScope.launch {
    podcasts.addOrRefresh(feedUrl).onFailure {
      status.value = context.getString(R.string.could_not_refresh_feed)
    }
  }

  fun markArticleRead(id: String) = viewModelScope.launch { reader.markRead(id) }

  fun refreshArticles(feedUrl: String?) = viewModelScope.launch {
    if (refreshingArticles.value) return@launch
    refreshingArticles.value = true
    try {
      val urls = feedUrl?.let(::listOf) ?: reader.allFeeds().map(ArticleFeedEntity::feedUrl)
      val failures = reader.refresh(urls)
      failures.forEach { Log.w("XPOD", "Unable to refresh article feed", it) }
      status.value =
          context.getString(
              if (failures.isEmpty()) R.string.article_feeds_refreshed
              else R.string.could_not_refresh_feed
          )
    } finally {
      refreshingArticles.value = false
    }
  }

  fun setArticleRead(id: String, read: Boolean) = viewModelScope.launch { reader.setRead(id, read) }

  fun toggleArticleFavorite(id: String) = viewModelScope.launch { reader.toggleFavorite(id) }

  fun toggleFavorite(id: String) = viewModelScope.launch { podcasts.toggleFavorite(id) }

  fun markPlayed(id: String, played: Boolean) = viewModelScope.launch {
    podcasts.setPlayed(id, played)
  }

  fun download(episode: EpisodeEntity) {
    if (downloads.states.value[episode.id]?.isCompleted == true) {
      downloads.remove(episode.id)
      status.value = context.getString(R.string.download_removed)
    } else if (downloads.states.value[episode.id]?.phase == app.xpod.data.DownloadPhase.Failed) {
      downloads
          .retry(episode)
          .fold(
              { status.value = context.getString(R.string.download_queued) },
              { status.value = context.getString(R.string.could_not_download) },
          )
    } else if (downloads.states.value[episode.id] != null) {
      status.value = context.getString(R.string.download_in_progress)
    } else
        downloads
            .enqueue(episode)
            .fold(
                { status.value = context.getString(R.string.download_queued) },
                { status.value = context.getString(R.string.could_not_download) },
            )
  }

  fun removeDownload(episodeId: String) {
    downloads.remove(episodeId)
    status.value = context.getString(R.string.download_removed)
  }

  fun play(episode: EpisodeEntity) = viewModelScope.launch {
    val result = runCatchingCancellable { player.play(episode) }
    if (result.isFailure) {
      status.value = context.getString(R.string.could_not_start_playback)
    } else {
      runCatchingCancellable { podcasts.recordPlayback(episode.id) }
          .onFailure { Log.w("XPOD", "Unable to record playback", it) }
    }
  }

  fun playQueueItem(episodeId: String) = viewModelScope.launch {
    val result = runCatchingCancellable { player.playQueueItem(episodeId) }
    if (result.isFailure) {
      status.value = context.getString(R.string.could_not_start_playback)
    } else {
      runCatchingCancellable { podcasts.recordPlayback(episodeId) }
          .onFailure { Log.w("XPOD", "Unable to record playback", it) }
    }
  }

  fun togglePlayback() = viewModelScope.launch {
    runCatchingCancellable { player.toggle() }
        .onFailure { status.value = context.getString(R.string.could_not_control_playback) }
  }

  fun seekTo(positionMs: Long) = viewModelScope.launch {
    runCatchingCancellable { player.seekTo(positionMs) }
        .onFailure { status.value = context.getString(R.string.could_not_seek_playback) }
  }

  fun seekBy(deltaMs: Long) = viewModelScope.launch {
    runCatchingCancellable { player.seekBy(deltaMs) }
        .onFailure { status.value = context.getString(R.string.could_not_seek_playback) }
  }

  fun setPlaybackSpeed(speed: Float) = viewModelScope.launch {
    runCatchingCancellable { player.setSpeed(speed) }
        .onFailure { status.value = context.getString(R.string.could_not_change_speed) }
  }

  fun playNext(episode: EpisodeEntity) = viewModelScope.launch {
    runCatchingCancellable { player.playNext(episode) }
        .onSuccess { status.value = context.getString(R.string.added_next) }
        .onFailure { status.value = context.getString(R.string.could_not_update_queue) }
  }

  fun addToQueue(episode: EpisodeEntity) = viewModelScope.launch {
    runCatchingCancellable { player.addToQueue(episode) }
        .onSuccess { status.value = context.getString(R.string.added_to_queue) }
        .onFailure { status.value = context.getString(R.string.could_not_update_queue) }
  }

  fun removeFromQueue(episodeId: String) = viewModelScope.launch {
    player.removeFromQueue(episodeId)
  }

  fun clearQueue() = viewModelScope.launch { player.clearQueue() }

  fun moveQueueItem(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
    player.moveQueueItem(fromIndex, toIndex)
  }

  fun importOpml(uri: Uri) = viewModelScope.launch {
    val result = runCatchingCancellable {
      requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Unable to open the selected OPML file"
          }
          .use { subscriptions.importOpml(it).getOrThrow() }
    }
    result.fold(
        { report ->
          report.failures.forEach { failure ->
            Log.w("XPOD", "Unable to import ${failure.url}", failure.error)
          }
          val insecureFailures = report.failures.count { it.error is UnsupportedFeedUrlException }
          status.value =
              when {
                report.failures.isEmpty() ->
                    context.resources.getQuantityString(
                        R.plurals.imported_subscriptions,
                        report.imported,
                        report.imported,
                    )
                insecureFailures > 0 ->
                    context.resources.getQuantityString(
                        R.plurals.imported_subscriptions_with_insecure_failures,
                        report.attempted,
                        report.imported,
                        report.attempted,
                        report.failures.size,
                        insecureFailures,
                    )
                else ->
                    context.resources.getQuantityString(
                        R.plurals.imported_subscriptions_with_failures,
                        report.attempted,
                        report.imported,
                        report.attempted,
                        report.failures.size,
                    )
              }
        },
        { error ->
          Log.e("XPOD", "Unable to import OPML", error)
          status.value = context.getString(R.string.could_not_add_feed)
        },
    )
  }

  fun exportOpml(uri: Uri) = viewModelScope.launch {
    context.contentResolver.openOutputStream(uri)?.use {
      podcasts.exportOpml(it, reader.allFeeds())
      status.value = context.getString(R.string.subscriptions_exported)
    }
  }

  fun dismissStatus() {
    status.value = null
  }

  fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
    settings.setDynamicColor(enabled)
  }

  fun setAppTheme(theme: ThemeMode) = viewModelScope.launch { settings.setAppTheme(theme) }

  fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch {
    settings.setWifiOnlyDownloads(enabled)
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
