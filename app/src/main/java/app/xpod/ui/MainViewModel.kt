package app.xpod.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.data.DownloadRepository
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.data.FeedHttpException
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.ThemeMode
import app.xpod.playback.PlaybackController
import app.xpod.R
import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import app.xpod.util.runCatchingCancellable

data class MainUiState(
    val podcasts: List<PodcastEntity> = emptyList(),
    val selectedPodcastId: String? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val libraryEpisodes: List<EpisodeEntity> = emptyList(),
    val status: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val podcasts: PodcastRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val player: PlaybackController,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val status = MutableStateFlow<String?>(null)
    private val episodes = selected.flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else podcasts.episodes(id) }
    val state: StateFlow<MainUiState> = combine(podcasts.podcasts(), selected, episodes, podcasts.allEpisodes(), status) { all, id, items, library, message ->
        MainUiState(all, id, items, library, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
    val dynamicColor = settings.useDynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val appTheme = settings.appTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    val wifiOnlyDownloads = settings.useWifiOnlyDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val nowPlaying = player.nowPlaying
    val queue = player.queue
    val downloadStates = downloads.states

    init {
        viewModelScope.launch { settings.useWifiOnlyDownloads.collect { downloads.setWifiOnly(it) } }
    }

    fun selectPodcast(id: String?) { selected.value = id }
    fun removePodcast(id: String) = viewModelScope.launch {
        podcasts.remove(id)
        if (selected.value == id) selected.value = null
        status.value = context.getString(R.string.subscription_removed)
    }
    fun addFeed(url: String, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        podcasts.addOrRefresh(url).fold(
            {
                status.value = context.getString(R.string.added_and_refreshed)
                onSuccess()
            },
            { error ->
                Log.e("XPOD", "Unable to add feed", error)
                status.value = context.getString(R.string.could_not_add_feed_reason, feedFailureReason(error))
            },
        )
    }
    fun refresh(feedUrl: String) = viewModelScope.launch { podcasts.addOrRefresh(feedUrl).onFailure { status.value = context.getString(R.string.could_not_refresh_feed) } }
    fun toggleFavorite(id: String) = viewModelScope.launch { podcasts.toggleFavorite(id) }
    fun markPlayed(id: String, played: Boolean) = viewModelScope.launch { podcasts.setPlayed(id, played) }
    fun download(episode: EpisodeEntity) {
        if (downloads.states.value[episode.id]?.isCompleted == true) {
            downloads.remove(episode.id)
            status.value = context.getString(R.string.download_removed)
        } else if (downloads.states.value[episode.id]?.phase == app.xpod.data.DownloadPhase.Failed) {
            downloads.retry(episode).fold(
                { status.value = context.getString(R.string.download_queued) },
                { status.value = context.getString(R.string.could_not_download) },
            )
        } else if (downloads.states.value[episode.id] != null) {
            status.value = context.getString(R.string.download_in_progress)
        } else downloads.enqueue(episode).fold(
            { status.value = context.getString(R.string.download_queued) },
            { status.value = context.getString(R.string.could_not_download) },
        )
    }
    fun removeDownload(episodeId: String) {
        downloads.remove(episodeId)
        status.value = context.getString(R.string.download_removed)
    }
    fun play(episode: EpisodeEntity) = viewModelScope.launch { runCatchingCancellable { player.play(episode) }.onFailure { status.value = context.getString(R.string.could_not_start_playback) } }
    fun playQueueItem(episodeId: String) = viewModelScope.launch { runCatchingCancellable { player.playQueueItem(episodeId) }.onFailure { status.value = context.getString(R.string.could_not_start_playback) } }
    fun togglePlayback() = viewModelScope.launch { runCatchingCancellable { player.toggle() }.onFailure { status.value = context.getString(R.string.could_not_control_playback) } }
    fun seekTo(positionMs: Long) = viewModelScope.launch { runCatchingCancellable { player.seekTo(positionMs) }.onFailure { status.value = context.getString(R.string.could_not_seek_playback) } }
    fun seekBy(deltaMs: Long) = viewModelScope.launch { runCatchingCancellable { player.seekBy(deltaMs) }.onFailure { status.value = context.getString(R.string.could_not_seek_playback) } }
    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch { runCatchingCancellable { player.setSpeed(speed) }.onFailure { status.value = context.getString(R.string.could_not_change_speed) } }
    fun playNext(episode: EpisodeEntity) = viewModelScope.launch { runCatchingCancellable { player.playNext(episode) }.onSuccess { status.value = context.getString(R.string.added_next) }.onFailure { status.value = context.getString(R.string.could_not_update_queue) } }
    fun addToQueue(episode: EpisodeEntity) = viewModelScope.launch { runCatchingCancellable { player.addToQueue(episode) }.onSuccess { status.value = context.getString(R.string.added_to_queue) }.onFailure { status.value = context.getString(R.string.could_not_update_queue) } }
    fun removeFromQueue(episodeId: String) = viewModelScope.launch { player.removeFromQueue(episodeId) }
    fun clearQueue() = viewModelScope.launch { player.clearQueue() }
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = viewModelScope.launch { player.moveQueueItem(fromIndex, toIndex) }
    fun importOpml(uri: Uri) = viewModelScope.launch { context.contentResolver.openInputStream(uri)?.use { podcasts.importOpml(it) }?.fold({ status.value = context.getString(R.string.imported_subscriptions, it) }, { status.value = context.getString(R.string.could_not_add_feed) }) }
    fun exportOpml(uri: Uri) = viewModelScope.launch { context.contentResolver.openOutputStream(uri)?.use { podcasts.exportOpml(it); status.value = context.getString(R.string.subscriptions_exported) } }
    fun dismissStatus() { status.value = null }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setAppTheme(theme: ThemeMode) = viewModelScope.launch { settings.setAppTheme(theme) }
    fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnlyDownloads(enabled) }

    private fun feedFailureReason(error: Throwable): String = when (error) {
        is FeedHttpException -> context.getString(R.string.feed_error_http, error.statusCode)
        is IOException -> context.getString(R.string.feed_error_network)
        is XmlPullParserException -> context.getString(R.string.feed_error_format)
        is IllegalArgumentException -> context.getString(R.string.feed_error_format)
        else -> context.getString(R.string.feed_error_format)
    }
}
