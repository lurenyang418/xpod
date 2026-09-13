package app.xpod.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemosRepository
import app.xpod.data.DownloadRepository
import app.xpod.data.EpisodeEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastRepository
import app.xpod.data.ReaderRepository
import app.xpod.data.SubscriptionRepository
import app.xpod.data.UnsupportedFeedUrlException
import app.xpod.playback.NowPlaying
import app.xpod.playback.PlaybackController
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException

data class MainUiState(
    val podcasts: List<PodcastEntity> = emptyList(),
    val isRefreshingPodcasts: Boolean = false,
    val newEpisodeCounts: Map<String, Int> = emptyMap(),
    val unplayedEpisodeCounts: Map<String, Int> = emptyMap(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
    val articleFeeds: List<ArticleFeedEntity> = emptyList(),
    val articles: List<ArticleEntity> = emptyList(),
    val isRefreshingArticles: Boolean = false,
    val status: UiStatus? = null,
)

internal data class PodcastSelectionUiState(
    val selectedPodcastId: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val isLoading: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun podcastSelectionFlow(
    selectedPodcastId: Flow<String?>,
    episodesForPodcast: (String) -> Flow<List<EpisodeEntity>>,
): Flow<PodcastSelectionUiState> = selectedPodcastId.flatMapLatest { id ->
  if (id == null) {
    flowOf(PodcastSelectionUiState())
  } else {
    flow {
      emit(PodcastSelectionUiState(selectedPodcastId = id, isLoading = true))
      emitAll(
          episodesForPodcast(id).map { episodes ->
            PodcastSelectionUiState(
                selectedPodcastId = id,
                episodes = episodes,
                isLoading = false,
            )
          }
      )
    }
  }
}

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val podcasts: PodcastRepository,
    private val reader: ReaderRepository,
    private val subscriptions: SubscriptionRepository,
    private val downloads: DownloadRepository,
    private val cloudMemos: CloudMemosRepository,
    private val player: PlaybackController,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val _navigation = MutableStateFlow(loadNavigationState())
  internal val navigation: StateFlow<MainNavigationState> = _navigation.asStateFlow()
  private val status = MutableStateFlow<UiStatus?>(null)
  private val bulkMarkController =
      BulkMarkController(podcasts, reader, context, viewModelScope) { message, severity ->
        showStatus(message, severity)
      }
  private val cloudMemosController =
      CloudMemosController(cloudMemos, context, viewModelScope) { message, severity ->
        showStatus(message, severity)
      }
  private val podcastContentController =
      PodcastContentController(podcasts, subscriptions, context, viewModelScope) { message, severity ->
        showStatus(message, severity)
      }
  private val readerContentController =
      ReaderContentController(reader, context, viewModelScope) { message, severity ->
        showStatus(message, severity)
      }
  private val downloadActionsController =
      DownloadActionsController(downloads, context, viewModelScope) { message, severity ->
        showStatus(message, severity)
      }
  private val selectedPodcastId =
      navigation.map { it.podcast.selectedPodcastId }.distinctUntilChanged()
  private val podcastSelectionSource = podcastSelectionFlow(selectedPodcastId, podcasts::episodes)
  internal val podcastSelection: StateFlow<PodcastSelectionUiState> =
      podcastSelectionSource.stateIn(
          viewModelScope,
          SharingStarted.Eagerly,
          PodcastSelectionUiState(),
      )
  val state: StateFlow<MainUiState> =
      combine(podcastContentController.state, readerContentController.state, status) {
              podcastState,
              readerState,
              message ->
            MainUiState(
                podcasts = podcastState.podcasts,
                isRefreshingPodcasts = podcastState.isRefreshing,
                newEpisodeCounts = podcastState.newEpisodeCounts,
                unplayedEpisodeCounts = podcastState.unplayedEpisodeCounts,
                libraryEpisodes = podcastState.libraryEpisodes,
                articleFeeds = readerState.articleFeeds,
                articles = readerState.articles,
                isRefreshingArticles = readerState.isRefreshing,
                status = message,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
  val cloudMemosState: StateFlow<CloudMemosUiState> = cloudMemosController.state
  val bulkActionsState: StateFlow<BulkActionsUiState> = bulkMarkController.state
  val nowPlaying = player.nowPlaying

  /**
   * [nowPlaying] with the 500 ms position ticks stripped out. Everything except the full player
   * (the only position consumer) should collect this one, so playback does not recompose the
   * navigation shell and every visible list row twice a second.
   */
  val nowPlayingDisplay: StateFlow<NowPlaying?> =
      player.nowPlaying
          .map { it?.copy(positionMs = 0L, durationMs = 0L) }
          .distinctUntilChanged()
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5_000),
              player.nowPlaying.value?.copy(positionMs = 0L, durationMs = 0L),
          )

  val queue = player.queue
  val musicPlaybackSettings = player.musicPlaybackSettings
  val downloadStates = downloadActionsController.states

  val articleSummaries: StateFlow<Map<String, String>> = readerContentController.articleSummaries

  private fun loadNavigationState(): MainNavigationState = restoreNavigationState(savedStateHandle)

  private fun dispatchNavigation(action: NavigationAction) {
    // Navigation callbacks and viewModelScope use the main dispatcher. Keep this read-modify-write
    // on that dispatcher so each user action is applied to the latest state.
    val updated = reduceNavigation(_navigation.value, action)
    if (updated == _navigation.value) return
    _navigation.value = updated
    persistNavigationState(savedStateHandle, updated)
  }

  internal fun selectDestination(route: AppRoute, visibleRoutes: List<AppRoute>) {
    dispatchNavigation(NavigationAction.SelectDestination(resolveRoute(route, visibleRoutes)))
  }

  internal fun selectPodcastSubView(value: PodcastSubView) {
    dispatchNavigation(NavigationAction.SelectPodcastSubView(value))
  }

  internal fun openPodcast(id: String, visibleRoutes: List<AppRoute>) {
    if (AppRoute.Podcasts !in visibleRoutes) {
      selectDestination(AppRoute.Podcasts, visibleRoutes)
      showStatus(context.getString(R.string.podcasts_hidden_in_navigation))
      return
    }
    dispatchNavigation(NavigationAction.OpenPodcast(id))
    viewModelScope.launch { podcasts.markPodcastSeen(id) }
  }

  fun openEpisode(id: String) {
    dispatchNavigation(NavigationAction.OpenEpisode(id))
  }

  fun openArticle(id: String) {
    dispatchNavigation(NavigationAction.OpenArticle(id))
  }

  fun openBook(id: String) {
    dispatchNavigation(NavigationAction.OpenBook(id))
  }

  fun openFullPlayer() {
    dispatchNavigation(NavigationAction.OpenFullPlayer)
  }

  fun closeFullPlayer() {
    dispatchNavigation(NavigationAction.CloseFullPlayer)
  }

  fun navigateBack() {
    dispatchNavigation(NavigationAction.NavigateBack)
  }

  fun removePodcast(id: String) = viewModelScope.launch {
    runCatchingCancellable { podcasts.remove(id) }
        .onSuccess { removedEpisodeIds ->
          player.removeDeletedEpisodes(removedEpisodeIds)
          if (_navigation.value.podcast.selectedPodcastId == id) {
            dispatchNavigation(NavigationAction.ClearPodcastSelection)
          }
          _navigation.value.selectedEpisodeId?.takeIf(removedEpisodeIds::contains)?.let {
            dispatchNavigation(NavigationAction.ClearEpisodeSelection)
          }
          showStatus(context.getString(R.string.subscription_removed))
        }
        .onFailure { error ->
          Log.e("XPOD", "Unable to remove podcast subscription", error)
          showError(context.getString(R.string.could_not_remove_subscription))
        }
  }

  fun removeArticleFeed(id: String) = readerContentController.removeFeed(id)

  fun requestPodcastMarkAllPlayed(podcastId: String) {
    val current = state.value
    bulkMarkController.requestPodcastMarkAllPlayed(
        podcast = current.podcasts.firstOrNull { it.id == podcastId },
        episodes = current.libraryEpisodes,
    )
  }

  fun requestArticlesMarkAllRead(feedId: String?) {
    val current = state.value
    bulkMarkController.requestArticlesMarkAllRead(
        feedId = feedId,
        feedTitle = feedId?.let { id -> current.articleFeeds.firstOrNull { it.id == id }?.title },
        articles = current.articles,
    )
  }

  fun dismissBulkMarkRequest() {
    bulkMarkController.dismissRequest()
  }

  fun confirmBulkMark() {
    bulkMarkController.confirm()
  }

  fun undoBulkMark(eventId: Long) {
    bulkMarkController.undo(eventId)
  }

  fun dismissBulkUndo(eventId: Long) {
    bulkMarkController.dismissUndo(eventId)
  }

  fun addFeed(url: String, onComplete: (Boolean) -> Unit = {}) =
      podcastContentController.addFeed(url, onComplete)

  fun refresh(feedUrl: String) = podcastContentController.refresh(feedUrl)

  fun refreshAllPodcasts() = podcastContentController.refreshAll()

  fun markArticleRead(id: String) = readerContentController.markRead(id)

  fun refreshArticles(feedUrl: String?) = readerContentController.refresh(feedUrl)

  fun setArticleRead(id: String, read: Boolean) = readerContentController.setRead(id, read)

  fun toggleArticleFavorite(id: String) = readerContentController.toggleFavorite(id)

  fun toggleFavorite(id: String) = podcastContentController.toggleFavorite(id)

  fun markPlayed(id: String, played: Boolean) = podcastContentController.markPlayed(id, played)

  fun download(episode: EpisodeEntity) = downloadActionsController.download(episode)

  fun removeDownload(episodeId: String) = downloadActionsController.remove(episodeId)

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
    runCatchingCancellable {
          requireNotNull(context.contentResolver.openOutputStream(uri)) {
                "Unable to open the selected OPML file"
              }
              .use { podcasts.exportOpml(it, reader.allFeeds()) }
        }
        .fold(
            { showStatus(context.getString(R.string.subscriptions_exported)) },
            { error ->
              Log.e("XPOD", "Unable to export OPML", error)
              showError(context.getString(R.string.could_not_export_subscriptions))
            },
        )
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

  fun configureCloudMemos(baseUrl: String, token: String, onSuccess: () -> Unit = {}) =
      cloudMemosController.configure(baseUrl, token, onSuccess)

  fun disconnectCloudMemos() = cloudMemosController.disconnect()

  fun saveEpisodeToCloudMemos(episode: EpisodeEntity, podcastTitle: String?) =
      cloudMemosController.saveEpisode(episode, podcastTitle)

  fun saveArticleToCloudMemos(article: ArticleEntity, feedTitle: String?) =
      cloudMemosController.saveArticle(article, feedTitle)

}
