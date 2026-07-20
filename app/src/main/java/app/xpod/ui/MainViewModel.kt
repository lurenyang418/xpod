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
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoDrafts
import app.xpod.data.CloudMemoState
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.CloudMemosConnection
import app.xpod.data.CloudMemosHttpException
import app.xpod.data.CloudMemosNotConfiguredException
import app.xpod.data.CloudMemosProtocolException
import app.xpod.data.CloudMemosRecycleBinUnsupportedException
import app.xpod.data.CloudMemosRepository
import app.xpod.data.DownloadRepository
import app.xpod.data.EpisodeEntity
import app.xpod.data.FeedHttpException
import app.xpod.data.InvalidCloudMemosTokenException
import app.xpod.data.InvalidCloudMemosUrlException
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
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val unplayedEpisodeCounts: Map<String, Int> = emptyMap(),
    val selectedPodcastId: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
    val articleFeeds: List<ArticleFeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val isRefreshingArticles: Boolean = false,
    val status: String? = null,
)

data class CloudMemosUiState(
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
    val isBusy: Boolean = false,
)

data class MemosUiState(
    val items: List<CloudMemo> = emptyList(),
    val draft: String = "",
    val query: String = "",
    val appliedQuery: String = "",
    val selectedTag: String? = null,
    val appliedTag: String? = null,
    val knownTags: List<String> = emptyList(),
    val visibility: CloudMemoVisibility = CloudMemoVisibility.Private,
    val nextCursor: String? = null,
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isCreating: Boolean = false,
    val busyMemoIds: Set<String> = emptySet(),
    val pendingPrivateShareMemoId: String? = null,
    val pendingDeleteMemoId: String? = null,
    val archivedMemoForUndo: CloudMemo? = null,
    val archivedMemoUndoSequence: Long = 0,
    val error: String? = null,
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
  private val status = MutableStateFlow<String?>(null)
  private val refreshingArticles = MutableStateFlow(false)
  private val cloudMemosBusy = MutableStateFlow(false)
  private val _memosState = MutableStateFlow(MemosUiState())
  private val _bulkActionsState = MutableStateFlow(BulkActionsUiState())
  private var pendingBulkUndo: PendingBulkUndo? = null
  private var bulkEventSequence = 0L
  private var memosLoadJob: Job? = null
  private var memosCreateJob: Job? = null
  private val memosMutationJobs = mutableMapOf<String, Job>()
  private var memosAccountGeneration = 0L
  private var memosLoadGeneration = 0L
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
            unplayedEpisodeCounts =
                library.filterNot { it.isPlayed }.groupingBy { it.podcastId }.eachCount(),
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
  val cloudMemosState: StateFlow<CloudMemosUiState> =
      combine(cloudMemos.connection, cloudMemosBusy) { connection, busy ->
            connection.toUiState(busy)
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5_000),
              CloudMemosUiState(),
          )
  val memosState: StateFlow<MemosUiState> = _memosState
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
                status.value = context.getString(R.string.could_not_mark_all)
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
                status.value = context.getString(R.string.bulk_mark_undone)
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
                status.value = context.getString(R.string.could_not_undo_bulk_mark)
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
        cancelMemosOperations()
        try {
          runCatchingCancellable { cloudMemos.configure(baseUrl, token.ifBlank { null }) }
              .fold(
                  {
                    _memosState.value = MemosUiState()
                    status.value = context.getString(R.string.cloud_memos_connected)
                    onSuccess()
                  },
                  { error ->
                    status.value =
                        context.getString(
                            R.string.cloud_memos_connection_failed_reason,
                            cloudMemosFailureReason(error),
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
    cancelMemosOperations()
    try {
      cloudMemos.disconnect()
      _memosState.value = MemosUiState()
      status.value = context.getString(R.string.cloud_memos_disconnected)
    } finally {
      cloudMemosBusy.value = false
    }
  }

  fun saveEpisodeToCloudMemos(episode: EpisodeEntity, podcastTitle: String?) =
      saveToCloudMemos(CloudMemoDrafts.episode(episode, podcastTitle))

  fun saveArticleToCloudMemos(article: ArticleEntity, feedTitle: String?) =
      saveToCloudMemos(CloudMemoDrafts.article(article, feedTitle))

  fun setMemoDraft(value: String) {
    _memosState.value = _memosState.value.copy(draft = value.take(MAX_MEMO_CHARACTERS))
  }

  fun setMemoQuery(value: String) {
    _memosState.value = _memosState.value.copy(query = value.take(MAX_MEMO_QUERY_CHARACTERS))
  }

  fun selectMemoTag(value: String?) {
    val tag = value?.trim()?.take(MAX_MEMO_TAG_CHARACTERS)?.takeIf(String::isNotEmpty)
    if (_memosState.value.selectedTag == tag) return
    _memosState.value = _memosState.value.copy(selectedTag = tag)
    startMemosLoad(reset = true)
  }

  fun setMemoVisibility(value: CloudMemoVisibility) {
    _memosState.value = _memosState.value.copy(visibility = value)
  }

  fun requestPrivateMemoShare(memoId: String) {
    val current = _memosState.value
    if (
        current.items.none { memo ->
          memo.id == memoId && memo.visibility == CloudMemoVisibility.Private
        }
    ) {
      return
    }
    _memosState.value = current.copy(pendingPrivateShareMemoId = memoId, pendingDeleteMemoId = null)
  }

  fun dismissPrivateMemoShare() {
    if (_memosState.value.pendingPrivateShareMemoId == null) return
    _memosState.value = _memosState.value.copy(pendingPrivateShareMemoId = null)
  }

  fun requestMemoDelete(memoId: String) {
    val current = _memosState.value
    if (memoId in current.busyMemoIds || current.items.none { it.id == memoId }) return
    _memosState.value = current.copy(pendingDeleteMemoId = memoId, pendingPrivateShareMemoId = null)
  }

  fun dismissMemoDelete() {
    if (_memosState.value.pendingDeleteMemoId == null) return
    _memosState.value = _memosState.value.copy(pendingDeleteMemoId = null)
  }

  fun archiveMemo(memoId: String) {
    val current = _memosState.value
    val memo = current.items.firstOrNull { it.id == memoId } ?: return
    if (
        memo.state != CloudMemoState.Active ||
            memoId in current.busyMemoIds ||
            memosMutationJobs.containsKey(memoId)
    ) {
      return
    }
    _memosState.value =
        current.copy(
            busyMemoIds = current.busyMemoIds + memoId,
            pendingPrivateShareMemoId =
                current.pendingPrivateShareMemoId.takeUnless { it == memoId },
            pendingDeleteMemoId = current.pendingDeleteMemoId.takeUnless { it == memoId },
            error = null,
        )
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result =
              cloudMemos.updateMemoState(
                  memoId = memo.id,
                  version = memo.version,
                  state = CloudMemoState.Archived,
              )
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              { archivedMemo ->
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        items = latest.items.filterNot { it.id == memoId },
                        busyMemoIds = latest.busyMemoIds - memoId,
                        archivedMemoForUndo = archivedMemo,
                        archivedMemoUndoSequence = latest.archivedMemoUndoSequence + 1,
                    )
                startMemosLoad(reset = true)
              },
              { error ->
                handleMemoMutationFailure(memoId, error, R.string.cloud_memo_archive_failed_reason)
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun restoreArchivedMemo(memoId: String) {
    val current = _memosState.value
    val memo = current.archivedMemoForUndo?.takeIf { it.id == memoId } ?: return
    if (memoId in current.busyMemoIds || memosMutationJobs.containsKey(memoId)) return
    _memosState.value = current.copy(busyMemoIds = current.busyMemoIds + memoId, error = null)
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result =
              cloudMemos.updateMemoState(
                  memoId = memo.id,
                  version = memo.version,
                  state = CloudMemoState.Active,
              )
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              {
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        busyMemoIds = latest.busyMemoIds - memoId,
                        archivedMemoForUndo =
                            latest.archivedMemoForUndo?.takeUnless { it.id == memoId },
                    )
                status.value = context.getString(R.string.cloud_memo_archive_undone)
                startMemosLoad(reset = true)
              },
              { error ->
                val latest = _memosState.value
                val isVersionConflict =
                    error is CloudMemosHttpException && error.errorCode == "VERSION_CONFLICT"
                _memosState.value = latest.afterArchiveRestoreFailure(memoId, isVersionConflict)
                handleMemoMutationFailure(
                    memoId,
                    error,
                    R.string.cloud_memo_restore_failed_reason,
                )
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun dismissArchivedMemoUndo(memoId: String) {
    val current = _memosState.value
    if (current.archivedMemoForUndo?.id != memoId) return
    _memosState.value = current.copy(archivedMemoForUndo = null)
  }

  fun moveMemoToTrash(memoId: String) {
    val current = _memosState.value
    if (current.pendingDeleteMemoId != memoId) return
    if (
        current.items.none { it.id == memoId } ||
            memoId in current.busyMemoIds ||
            memosMutationJobs.containsKey(memoId)
    ) {
      return
    }
    _memosState.value =
        current.copy(
            busyMemoIds = current.busyMemoIds + memoId,
            pendingDeleteMemoId = null,
            error = null,
        )
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result = cloudMemos.deleteMemo(memoId)
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              {
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        items = latest.items.filterNot { it.id == memoId },
                        busyMemoIds = latest.busyMemoIds - memoId,
                        pendingPrivateShareMemoId =
                            latest.pendingPrivateShareMemoId.takeUnless { it == memoId },
                    )
                status.value = context.getString(R.string.cloud_memo_moved_to_trash)
                startMemosLoad(reset = true)
              },
              { error ->
                handleMemoMutationFailure(
                    memoId,
                    error,
                    R.string.cloud_memo_delete_failed_reason,
                )
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun loadMemos() {
    if (_memosState.value.hasLoaded) return
    refreshMemos()
  }

  fun refreshMemos() = startMemosLoad(reset = true)

  fun searchMemos() = startMemosLoad(reset = true)

  fun loadMoreMemos() {
    val current = _memosState.value
    if (current.isRefreshing || current.isLoadingMore || current.nextCursor == null) return
    startMemosLoad(reset = false)
  }

  fun createMemo() {
    val current = _memosState.value
    val content = current.draft.trim()
    if (content.isEmpty() || current.isCreating) return
    _memosState.value = current.copy(isCreating = true, error = null)
    val accountGeneration = memosAccountGeneration
    memosCreateJob = viewModelScope.launch {
      val result = cloudMemos.createMemo(content, current.visibility)
      if (accountGeneration != memosAccountGeneration) return@launch
      result.fold(
          {
            _memosState.value = _memosState.value.copy(draft = "", isCreating = false)
            status.value = context.getString(R.string.cloud_memos_saved)
            startMemosLoad(reset = true)
          },
          { error ->
            _memosState.value =
                _memosState.value.copy(
                    isCreating = false,
                    error =
                        context.getString(
                            R.string.cloud_memos_save_failed_reason,
                            cloudMemosFailureReason(error),
                        ),
                )
          },
      )
    }
  }

  private fun startMemosLoad(reset: Boolean) {
    memosLoadJob?.cancel()
    val accountGeneration = memosAccountGeneration
    val loadGeneration = ++memosLoadGeneration
    _memosState.value = _memosState.value.copy(isRefreshing = false, isLoadingMore = false)
    memosLoadJob = viewModelScope.launch { fetchMemos(reset, accountGeneration, loadGeneration) }
  }

  private suspend fun fetchMemos(
      reset: Boolean,
      accountGeneration: Long,
      loadGeneration: Long,
  ) {
    val current = _memosState.value
    if (current.isRefreshing || current.isLoadingMore) return
    if (!reset && current.nextCursor == null) return
    _memosState.value =
        current.copy(
            isRefreshing = reset,
            isLoadingMore = !reset,
            error = null,
            nextCursor = if (reset) null else current.nextCursor,
        )
    val requestQuery = if (reset) current.query.trim() else current.appliedQuery
    val requestTag = if (reset) current.selectedTag else current.appliedTag
    val result =
        cloudMemos.listMemos(
            query = requestQuery.takeIf(String::isNotEmpty),
            tag = requestTag,
            cursor = if (reset) null else current.nextCursor,
        )
    if (accountGeneration != memosAccountGeneration || loadGeneration != memosLoadGeneration) {
      return
    }
    result.fold(
        { page ->
          _memosState.value =
              _memosState.value.copy(
                  items =
                      if (reset) page.items else (current.items + page.items).distinctBy { it.id },
                  appliedQuery = if (reset) requestQuery else current.appliedQuery,
                  appliedTag = if (reset) requestTag else current.appliedTag,
                  knownTags =
                      (_memosState.value.knownTags + page.items.flatMap { it.tags })
                          .distinctBy { it.lowercase(Locale.ROOT) }
                          .sortedBy { it.lowercase(Locale.ROOT) },
                  nextCursor = page.nextCursor,
                  hasLoaded = true,
                  isRefreshing = false,
                  isLoadingMore = false,
                  pendingPrivateShareMemoId =
                      if (reset) null else _memosState.value.pendingPrivateShareMemoId,
                  pendingDeleteMemoId = if (reset) null else _memosState.value.pendingDeleteMemoId,
              )
        },
        { error ->
          _memosState.value =
              _memosState.value.copy(
                  isRefreshing = false,
                  isLoadingMore = false,
                  nextCursor = current.nextCursor,
                  error =
                      context.getString(
                          R.string.cloud_memos_load_failed_reason,
                          cloudMemosFailureReason(error),
                      ),
              )
        },
    )
  }

  private fun cancelMemosOperations() {
    memosAccountGeneration++
    memosLoadGeneration++
    memosLoadJob?.cancel()
    memosLoadJob = null
    memosCreateJob?.cancel()
    memosCreateJob = null
    memosMutationJobs.values.forEach(Job::cancel)
    memosMutationJobs.clear()
    _memosState.value =
        _memosState.value.copy(
            isRefreshing = false,
            isLoadingMore = false,
            isCreating = false,
            busyMemoIds = emptySet(),
            pendingPrivateShareMemoId = null,
            pendingDeleteMemoId = null,
            archivedMemoForUndo = null,
            archivedMemoUndoSequence = 0,
        )
  }

  private fun handleMemoMutationFailure(memoId: String, error: Throwable, messageRes: Int) {
    val latest = _memosState.value
    _memosState.value = latest.copy(busyMemoIds = latest.busyMemoIds - memoId)
    if (error is CloudMemosHttpException && error.errorCode == "VERSION_CONFLICT") {
      status.value = context.getString(R.string.cloud_memo_version_conflict)
      startMemosLoad(reset = true)
    } else {
      _memosState.value =
          _memosState.value.copy(
              error = context.getString(messageRes, cloudMemosFailureReason(error))
          )
    }
  }

  private fun saveToCloudMemos(content: String) = viewModelScope.launch {
    if (cloudMemosBusy.value) return@launch
    cloudMemosBusy.value = true
    try {
      cloudMemos
          .createMemo(content)
          .fold(
              { status.value = context.getString(R.string.cloud_memos_saved) },
              { error ->
                status.value =
                    context.getString(
                        R.string.cloud_memos_save_failed_reason,
                        cloudMemosFailureReason(error),
                    )
              },
          )
    } finally {
      cloudMemosBusy.value = false
    }
  }

  private fun cloudMemosFailureReason(error: Throwable): String =
      when (error) {
        is InvalidCloudMemosUrlException ->
            context.getString(R.string.cloud_memos_error_https_required)
        is InvalidCloudMemosTokenException -> context.getString(R.string.cloud_memos_error_token)
        is CloudMemosNotConfiguredException ->
            context.getString(R.string.cloud_memos_error_not_configured)
        is CloudMemosRecycleBinUnsupportedException ->
            context.getString(R.string.cloud_memos_error_recycle_bin_required)
        is CloudMemosHttpException ->
            when {
              error.statusCode == 401 || error.errorCode == "INVALID_API_TOKEN" ->
                  context.getString(R.string.cloud_memos_error_unauthorized)
              error.errorCode == "INSUFFICIENT_SCOPE" ->
                  context.getString(R.string.cloud_memos_error_scope)
              error.statusCode >= 500 ->
                  context.getString(R.string.cloud_memos_error_server, error.statusCode)
              else -> context.getString(R.string.cloud_memos_error_http, error.statusCode)
            }
        is CloudMemosProtocolException -> context.getString(R.string.cloud_memos_error_response)
        is IOException -> context.getString(R.string.cloud_memos_error_network)
        else -> context.getString(R.string.cloud_memos_error_response)
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

  private companion object {
    const val MAX_MEMO_CHARACTERS = 100_000
    const val MAX_MEMO_QUERY_CHARACTERS = 100
    const val MAX_MEMO_TAG_CHARACTERS = 80
  }
}

private fun CloudMemosConnection.toUiState(isBusy: Boolean): CloudMemosUiState =
    CloudMemosUiState(baseUrl = baseUrl, isConfigured = isConfigured, isBusy = isBusy)

internal fun MemosUiState.afterArchiveRestoreFailure(
    memoId: String,
    isVersionConflict: Boolean,
): MemosUiState {
  val isCurrentUndo = archivedMemoForUndo?.id == memoId
  return copy(
      archivedMemoForUndo = if (isVersionConflict && isCurrentUndo) null else archivedMemoForUndo,
      archivedMemoUndoSequence =
          if (!isVersionConflict && isCurrentUndo) {
            archivedMemoUndoSequence + 1
          } else {
            archivedMemoUndoSequence
          },
  )
}
