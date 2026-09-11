package app.xpod.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.ArticlesReadChange
import app.xpod.data.CloudMemoDrafts
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.CloudMemosConnection
import app.xpod.data.CloudMemosRepository
import app.xpod.data.DownloadRepository
import app.xpod.data.EpisodeEntity
import app.xpod.data.FeedHttpException
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastPlayedChange
import app.xpod.data.PodcastRepository
import app.xpod.data.ReaderRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.SubscriptionRepository
import app.xpod.data.ThemeMode
import app.xpod.data.UnsupportedFeedUrlException
import app.xpod.data.defaultTabOrder
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException

data class MainUiState(
    val podcasts: List<PodcastEntity> = emptyList(),
    val isRefreshingPodcasts: Boolean = false,
    val newEpisodeCounts: Map<String, Int> = emptyMap(),
    val unplayedEpisodeCounts: Map<String, Int> = emptyMap(),
    val selectedPodcastId: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
    val articleFeeds: List<ArticleFeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val isRefreshingArticles: Boolean = false,
    val status: UiStatus? = null,
)

data class CloudMemosUiState(
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
    val isBusy: Boolean = false,
)

sealed interface BulkMarkRequest {
  val count: Int

  data class Podcast(
      val podcastId: String,
      val podcastTitle: String,
      override val count: Int,
  ) : BulkMarkRequest

  data class Articles(
      val feedId: String?,
      val feedTitle: String?,
      override val count: Int,
  ) : BulkMarkRequest
}

enum class BulkMarkKind {
  PodcastEpisodes,
  Articles,
}

data class BulkUndoEvent(
    val id: Long,
    val kind: BulkMarkKind,
    val count: Int,
)

data class BulkActionsUiState(
    val pendingRequest: BulkMarkRequest? = null,
    val undoEvent: BulkUndoEvent? = null,
    val isBusy: Boolean = false,
)

private sealed interface BulkUndoChange {
  val count: Int

  data class Podcast(val change: PodcastPlayedChange) : BulkUndoChange {
    override val count = change.markedPlayedCount
  }

  data class Articles(val change: ArticlesReadChange) : BulkUndoChange {
    override val count = change.articleIds.size
  }
}

private data class PendingBulkUndo(
    val eventId: Long,
    val change: BulkUndoChange,
)

internal fun unplayedEpisodeCount(episodes: List<EpisodeEntity>, podcastId: String): Int =
    episodes.count {
      it.podcastId == podcastId && !it.isPlayed
    }

internal fun unreadArticleCount(articles: List<ArticleEntity>, feedId: String?): Int =
    articles.count {
      !it.isRead && (feedId == null || it.feedId == feedId)
    }

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val podcasts: PodcastRepository,
    private val reader: ReaderRepository,
    private val subscriptions: SubscriptionRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val cloudMemos: CloudMemosRepository,
    private val player: PlaybackController,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val selected = MutableStateFlow<String?>(null)
  private val status = MutableStateFlow<UiStatus?>(null)
  private val refreshingPodcasts = MutableStateFlow(false)
  private val refreshingArticles = MutableStateFlow(false)
  private val cloudMemosBusy = MutableStateFlow(false)
  private val _bulkActionsState = MutableStateFlow(BulkActionsUiState())
  private var pendingBulkUndo: PendingBulkUndo? = null
  private var bulkEventSequence = 0L
  @OptIn(ExperimentalCoroutinesApi::class)
  private val episodes = selected.flatMapLatest { id ->
    if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else podcasts.episodes(id)
  }
  private val libraryState =
      combine(podcasts.podcasts(), selected, episodes, podcasts.allEpisodes()) {
          all,
          id,
          items,
          library ->
        MainUiState(
            podcasts = all,
            newEpisodeCounts = library.filter { it.isNew }.groupingBy { it.podcastId }.eachCount(),
            unplayedEpisodeCounts =
                library.filterNot { it.isPlayed }.groupingBy { it.podcastId }.eachCount(),
            selectedPodcastId = id,
            episodes = items,
            libraryEpisodes = library,
        )
      }
  private val podcastState =
      combine(libraryState, refreshingPodcasts, status) { base, refreshing, message ->
        base.copy(isRefreshingPodcasts = refreshing, status = message)
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
  val cloudMemosState: StateFlow<CloudMemosUiState> =
      combine(cloudMemos.connection, cloudMemosBusy) { connection, busy ->
            connection.toUiState(busy)
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5_000),
              CloudMemosUiState(),
          )
  val bulkActionsState: StateFlow<BulkActionsUiState> = _bulkActionsState
  val tabOrder: StateFlow<List<AppTab>> =
      settings.tabOrder.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          defaultTabOrder,
      )
  val enabledTabs: StateFlow<Set<AppTab>> =
      settings.enabledTabs.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          defaultTabOrder.toSet(),
      )
  val nowPlaying = player.nowPlaying
  val queue = player.queue
  val musicPlaybackSettings = player.musicPlaybackSettings
  val downloadStates = downloads.states

  init {
    viewModelScope.launch {
      runCatching { settings.useWifiOnlyDownloads.first() }.getOrNull()?.let(downloads::setWifiOnly)
    }
  }

  fun selectPodcast(id: String?) {
    selected.value = id
    if (id != null) viewModelScope.launch { podcasts.markPodcastSeen(id) }
  }

  fun removePodcast(id: String) = viewModelScope.launch {
    runCatchingCancellable { podcasts.remove(id) }
        .onSuccess { removedEpisodeIds ->
          player.removeDeletedEpisodes(removedEpisodeIds)
          if (selected.value == id) selected.value = null
          showStatus(context.getString(R.string.subscription_removed))
        }
        .onFailure { error ->
          Log.e("XPOD", "Unable to remove podcast subscription", error)
          showError(context.getString(R.string.could_not_remove_subscription))
        }
  }

  fun removeArticleFeed(id: String) = viewModelScope.launch {
    reader.remove(id)
    showStatus(context.getString(R.string.subscription_removed))
  }

  fun requestPodcastMarkAllPlayed(podcastId: String) {
    if (_bulkActionsState.value.isBusy) return
    val current = state.value
    val podcast = current.podcasts.firstOrNull { it.id == podcastId } ?: return
    val count = unplayedEpisodeCount(current.libraryEpisodes, podcastId)
    if (count == 0) return
    _bulkActionsState.value =
        _bulkActionsState.value.copy(
            pendingRequest = BulkMarkRequest.Podcast(podcastId, podcast.title, count)
        )
  }

  fun requestArticlesMarkAllRead(feedId: String?) {
    if (_bulkActionsState.value.isBusy) return
    val current = state.value
    val feed = feedId?.let { id -> current.articleFeeds.firstOrNull { it.id == id } ?: return }
    val count = unreadArticleCount(current.articles, feedId)
    if (count == 0) return
    _bulkActionsState.value =
        _bulkActionsState.value.copy(
            pendingRequest = BulkMarkRequest.Articles(feedId, feed?.title, count)
        )
  }

  fun dismissBulkMarkRequest() {
    if (_bulkActionsState.value.isBusy) return
    _bulkActionsState.value = _bulkActionsState.value.copy(pendingRequest = null)
  }

  fun confirmBulkMark() {
    val request = _bulkActionsState.value.pendingRequest ?: return
    if (_bulkActionsState.value.isBusy) return
    pendingBulkUndo = null
    _bulkActionsState.value =
        _bulkActionsState.value.copy(pendingRequest = null, undoEvent = null, isBusy = true)
    viewModelScope.launch {
      runCatchingCancellable {
            when (request) {
              is BulkMarkRequest.Podcast ->
                  BulkUndoChange.Podcast(podcasts.markAllPlayed(request.podcastId))
              is BulkMarkRequest.Articles ->
                  BulkUndoChange.Articles(reader.markAllRead(request.feedId))
            }
          }
          .fold(
              { change ->
                if (change.count == 0) {
                  _bulkActionsState.value =
                      _bulkActionsState.value.copy(isBusy = false, undoEvent = null)
                  return@fold
                }
                val event =
                    BulkUndoEvent(
                        id = ++bulkEventSequence,
                        kind =
                            when (change) {
                              is BulkUndoChange.Podcast -> BulkMarkKind.PodcastEpisodes
                              is BulkUndoChange.Articles -> BulkMarkKind.Articles
                            },
                        count = change.count,
                    )
                pendingBulkUndo = PendingBulkUndo(event.id, change)
                _bulkActionsState.value =
                    _bulkActionsState.value.copy(isBusy = false, undoEvent = event)
              },
              { error ->
                Log.e("XPOD", "Unable to mark items in bulk", error)
                _bulkActionsState.value =
                    _bulkActionsState.value.copy(isBusy = false, undoEvent = null)
                showError(context.getString(R.string.could_not_mark_all))
              },
          )
    }
  }

  fun undoBulkMark(eventId: Long) {
    if (_bulkActionsState.value.isBusy) return
    val pending = pendingBulkUndo?.takeIf { it.eventId == eventId } ?: return
    _bulkActionsState.value = _bulkActionsState.value.copy(isBusy = true, undoEvent = null)
    viewModelScope.launch {
      runCatchingCancellable {
            when (val change = pending.change) {
              is BulkUndoChange.Podcast -> podcasts.restorePlayedChange(change.change)
              is BulkUndoChange.Articles -> reader.restoreReadChange(change.change)
            }
          }
          .fold(
              {
                pendingBulkUndo = null
                _bulkActionsState.value =
                    _bulkActionsState.value.copy(isBusy = false, undoEvent = null)
                showStatus(context.getString(R.string.bulk_mark_undone))
              },
              { error ->
                Log.e("XPOD", "Unable to undo bulk status change", error)
                val retryEvent =
                    BulkUndoEvent(
                        id = ++bulkEventSequence,
                        kind =
                            when (pending.change) {
                              is BulkUndoChange.Podcast -> BulkMarkKind.PodcastEpisodes
                              is BulkUndoChange.Articles -> BulkMarkKind.Articles
                            },
                        count = pending.change.count,
                    )
                pendingBulkUndo = PendingBulkUndo(retryEvent.id, pending.change)
                _bulkActionsState.value =
                    _bulkActionsState.value.copy(isBusy = false, undoEvent = retryEvent)
                showError(context.getString(R.string.could_not_undo_bulk_mark))
              },
          )
    }
  }

  fun dismissBulkUndo(eventId: Long) {
    if (pendingBulkUndo?.eventId != eventId) return
    pendingBulkUndo = null
    _bulkActionsState.value = _bulkActionsState.value.copy(undoEvent = null)
  }

  fun addFeed(url: String, onSuccess: () -> Unit = {}) = viewModelScope.launch {
    subscriptions
        .addOrRefresh(url)
        .fold(
            {
              showStatus(context.getString(R.string.added_and_refreshed))
              onSuccess()
            },
            { error ->
              Log.e("XPOD", "Unable to add feed", error)
              showError(
                  context.getString(
                      R.string.could_not_add_feed_reason,
                      feedFailureReason(error),
                  )
              )
            },
        )
  }

  fun refresh(feedUrl: String) = viewModelScope.launch {
    if (refreshingPodcasts.value) return@launch
    refreshingPodcasts.value = true
    try {
      podcasts.addOrRefresh(feedUrl).onFailure {
        showError(context.getString(R.string.could_not_refresh_feed))
      }
    } finally {
      refreshingPodcasts.value = false
    }
  }

  fun refreshAllPodcasts() = viewModelScope.launch {
    if (refreshingPodcasts.value) return@launch
    refreshingPodcasts.value = true
    try {
      val result = podcasts.refreshAll()
      if (result.failureCount > 0) {
        val total = result.refreshedCount + result.failureCount
        showError(
            context.resources.getQuantityString(
                R.plurals.podcasts_refreshed_with_failures,
                total,
                result.refreshedCount,
                total,
                result.failureCount,
            )
        )
      }
    } finally {
      refreshingPodcasts.value = false
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
      if (failures.isNotEmpty()) {
        showError(context.getString(R.string.could_not_refresh_feed))
      }
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
      showStatus(context.getString(R.string.download_removed))
    } else if (downloads.states.value[episode.id]?.phase == app.xpod.data.DownloadPhase.Failed) {
      downloads
          .retry(episode)
          .fold(
              { showStatus(context.getString(R.string.download_queued)) },
              { showError(context.getString(R.string.could_not_download)) },
          )
    } else if (downloads.states.value[episode.id] != null) {
      showStatus(context.getString(R.string.download_in_progress))
    } else
        downloads
            .enqueue(episode)
            .fold(
                { showStatus(context.getString(R.string.download_queued)) },
                { showError(context.getString(R.string.could_not_download)) },
            )
  }

  fun removeDownload(episodeId: String) {
    downloads.remove(episodeId)
    showStatus(context.getString(R.string.download_removed))
  }

  fun play(episode: EpisodeEntity) = viewModelScope.launch {
    val result = runCatchingCancellable { player.play(episode) }
    if (result.isFailure) {
      showError(context.getString(R.string.could_not_start_playback))
    } else {
      runCatchingCancellable { podcasts.recordPlayback(episode.id) }
          .onFailure { Log.w("XPOD", "Unable to record playback", it) }
    }
  }

  fun playQueueItem(mediaId: String) = viewModelScope.launch {
    val result = runCatchingCancellable { player.playQueueItem(mediaId) }
    if (result.isFailure) {
      showError(context.getString(R.string.could_not_start_playback))
    } else if (PlaybackMediaType.fromMediaId(mediaId) == PlaybackMediaType.Podcast) {
      runCatchingCancellable { podcasts.recordPlayback(mediaId) }
          .onFailure { Log.w("XPOD", "Unable to record playback", it) }
    }
  }

  fun togglePlayback() = viewModelScope.launch {
    runCatchingCancellable { player.toggle() }
        .onFailure { showError(context.getString(R.string.could_not_control_playback)) }
  }

  fun seekTo(positionMs: Long) = viewModelScope.launch {
    runCatchingCancellable { player.seekTo(positionMs) }
        .onFailure { showError(context.getString(R.string.could_not_seek_playback)) }
  }

  fun seekBy(deltaMs: Long) = viewModelScope.launch {
    runCatchingCancellable { player.seekBy(deltaMs) }
        .onFailure { showError(context.getString(R.string.could_not_seek_playback)) }
  }

  fun setPlaybackSpeed(speed: Float) = viewModelScope.launch {
    runCatchingCancellable { player.setSpeed(speed) }
        .onFailure { showError(context.getString(R.string.could_not_change_speed)) }
  }

  fun skipToNext() = viewModelScope.launch {
    runCatchingCancellable { player.skipToNext() }
        .onFailure { showError(context.getString(R.string.could_not_control_playback)) }
  }

  fun skipToPrevious() = viewModelScope.launch {
    runCatchingCancellable { player.skipToPrevious() }
        .onFailure { showError(context.getString(R.string.could_not_control_playback)) }
  }

  fun toggleMusicShuffle() = viewModelScope.launch {
    runCatchingCancellable { player.toggleMusicShuffle() }
        .onFailure { showError(context.getString(R.string.could_not_change_playback_mode)) }
  }

  fun cycleMusicRepeatMode() = viewModelScope.launch {
    runCatchingCancellable { player.cycleMusicRepeatMode() }
        .onFailure { showError(context.getString(R.string.could_not_change_playback_mode)) }
  }

  fun playNext(episode: EpisodeEntity) = viewModelScope.launch {
    runCatchingCancellable { player.playNext(episode) }
        .onSuccess { showStatus(context.getString(R.string.added_next)) }
        .onFailure { showError(context.getString(R.string.could_not_update_queue)) }
  }

  fun addToQueue(episode: EpisodeEntity) = viewModelScope.launch {
    runCatchingCancellable { player.addToQueue(episode) }
        .onSuccess { showStatus(context.getString(R.string.added_to_queue)) }
        .onFailure { showError(context.getString(R.string.could_not_update_queue)) }
  }

  fun removeFromQueue(mediaId: String) = viewModelScope.launch {
    player.removeFromQueue(mediaId)
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
          val message =
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
          if (report.failures.isEmpty()) showStatus(message) else showError(message)
        },
        { error ->
          Log.e("XPOD", "Unable to import OPML", error)
          showError(context.getString(R.string.could_not_add_feed))
        },
    )
  }

  fun exportOpml(uri: Uri) = viewModelScope.launch {
    context.contentResolver.openOutputStream(uri)?.use {
      podcasts.exportOpml(it, reader.allFeeds())
      showStatus(context.getString(R.string.subscriptions_exported))
    }
  }

  fun dismissStatus() {
    status.value = null
  }

  private fun showStatus(message: String, severity: StatusSeverity = StatusSeverity.Info) {
    status.value = UiStatus(message, severity)
  }

  private fun showError(message: String) {
    showStatus(message, StatusSeverity.Error)
  }

  fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
    settings.setDynamicColor(enabled)
  }

  fun setAppTheme(theme: ThemeMode) = viewModelScope.launch { settings.setAppTheme(theme) }

  fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch {
    settings.setWifiOnlyDownloads(enabled)
    downloads.setWifiOnly(enabled)
  }

  fun moveTab(tab: AppTab, offset: Int) = viewModelScope.launch {
    settings.moveTab(tab, offset)
  }

  fun setTabEnabled(tab: AppTab, enabled: Boolean) = viewModelScope.launch {
    settings.setTabEnabled(tab, enabled)
  }

  fun configureCloudMemos(baseUrl: String, token: String, onSuccess: () -> Unit = {}) =
      viewModelScope.launch {
        if (cloudMemosBusy.value) return@launch
        cloudMemosBusy.value = true
        try {
          runCatchingCancellable { cloudMemos.configure(baseUrl, token.ifBlank { null }) }
              .fold(
                  {
                    showStatus(context.getString(R.string.cloud_memos_connected))
                    onSuccess()
                  },
                  { error ->
                    showError(
                        context.getString(
                            R.string.cloud_memos_connection_failed_reason,
                            memosFailureReason(error),
                        )
                    )
                  },
              )
        } finally {
          cloudMemosBusy.value = false
        }
      }

  fun disconnectCloudMemos() = viewModelScope.launch {
    if (cloudMemosBusy.value) return@launch
    cloudMemosBusy.value = true
    try {
      cloudMemos.disconnect()
      showStatus(context.getString(R.string.cloud_memos_disconnected))
    } finally {
      cloudMemosBusy.value = false
    }
  }

  fun saveEpisodeToCloudMemos(episode: EpisodeEntity, podcastTitle: String?) =
      saveToCloudMemos(CloudMemoDrafts.episode(episode, podcastTitle))

  fun saveArticleToCloudMemos(article: ArticleEntity, feedTitle: String?) =
      saveToCloudMemos(CloudMemoDrafts.article(article, feedTitle))

  private fun saveToCloudMemos(content: String) = viewModelScope.launch {
    if (cloudMemosBusy.value) return@launch
    cloudMemosBusy.value = true
    try {
      cloudMemos
          .createMemo(content, CloudMemoVisibility.Private)
          .fold(
              { showStatus(context.getString(R.string.cloud_memos_saved)) },
              { error ->
                showError(
                    context.getString(
                        R.string.cloud_memos_save_failed_reason,
                        memosFailureReason(error),
                    )
                )
              },
          )
    } finally {
      cloudMemosBusy.value = false
    }
  }

  private fun memosFailureReason(error: Throwable): String =
      cloudMemosFailureReason(
          MemosStrings { resId, formatArgs -> context.getString(resId, *formatArgs) },
          error,
      )

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

private fun CloudMemosConnection.toUiState(isBusy: Boolean): CloudMemosUiState =
    CloudMemosUiState(baseUrl = baseUrl, isConfigured = isConfigured, isBusy = isBusy)
