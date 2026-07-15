package app.xpod.playback

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.xpod.data.EpisodeEntity
import app.xpod.data.PlaybackRepository
import app.xpod.data.PodcastRepository
import app.xpod.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class NowPlaying(
    val episode: EpisodeEntity,
    val isPlaying: Boolean,
    val speed: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class PlaybackQueue(val episodes: List<EpisodeEntity> = emptyList(), val currentEpisodeId: String? = null)

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val podcasts: PodcastRepository,
    private val settings: SettingsRepository,
) {
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()
    private var restoredQueueApplied = false

    init {
        scope.launch(Dispatchers.IO) {
            val episodes = playbackRepository.queue().mapNotNull { podcasts.episode(it) }
            val currentEpisodeId = playbackRepository.state()?.episodeId
            withContext(Dispatchers.Main.immediate) {
                if (!restoredQueueApplied) _queue.value = PlaybackQueue(episodes, currentEpisodeId)
            }
        }
    }

    private suspend fun controller(): MediaController = controller ?: suspendCancellableCoroutine { continuation ->
        val token = SessionToken(context, PlaybackService.component(context))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { created ->
                controller = created
                created.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                        _nowPlaying.value = _nowPlaying.value?.copy(speed = playbackParameters.speed)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val episode = _queue.value.episodes.firstOrNull { it.id == mediaItem?.mediaId } ?: return
                        _nowPlaying.value = NowPlaying(episode, isPlaying = created.isPlaying, speed = created.playbackParameters.speed)
                        _queue.value = _queue.value.copy(currentEpisodeId = episode.id)
                    }
                })
                if (continuation.isActive) continuation.resume(created)
            }.onFailure { if (continuation.isActive) continuation.cancel(it) }
        }, ContextCompat.getMainExecutor(context))
        continuation.invokeOnCancellation { future.cancel(true) }
    }

    suspend fun play(episode: EpisodeEntity) {
        restoredQueueApplied = true
        val player = controller()
        val media = mediaItem(episode)
        playbackRepository.replaceQueue(listOf(episode.id))
        val speed = settings.defaultSpeed.first()
        player.setMediaItem(media)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
        _nowPlaying.value = NowPlaying(episode, isPlaying = true, speed = speed)
        _queue.value = PlaybackQueue(listOf(episode), episode.id)
        startProgressUpdates()
    }

    suspend fun playQueueItem(episodeId: String) {
        restoredQueueApplied = true
        val episodes = _queue.value.episodes
        val index = episodes.indexOfFirst { it.id == episodeId }
        if (index < 0) return
        val player = controller()
        val speed = settings.defaultSpeed.first()
        player.setMediaItems(episodes.map(::mediaItem), index, 0L)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
        _nowPlaying.value = NowPlaying(episodes[index], isPlaying = true, speed = speed)
        _queue.value = PlaybackQueue(episodes, episodeId)
        startProgressUpdates()
    }

    suspend fun playNext(episode: EpisodeEntity) = insertIntoQueue(episode, controller().currentMediaItemIndex.coerceAtLeast(-1) + 1)

    suspend fun addToQueue(episode: EpisodeEntity) = insertIntoQueue(episode, _queue.value.episodes.size)

    suspend fun removeFromQueue(episodeId: String) {
        val player = controller()
        restoredQueueApplied = true
        ensurePlayerQueue(player)
        val index = _queue.value.episodes.indexOfFirst { it.id == episodeId }
        if (index < 0) return
        player.removeMediaItem(index)
        val updated = _queue.value.episodes.toMutableList().also { it.removeAt(index) }
        _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId)
        playbackRepository.replaceQueue(updated.map { it.id })
    }

    suspend fun clearQueue() {
        val player = controller()
        restoredQueueApplied = true
        player.stop()
        player.clearMediaItems()
        progressJob?.cancel()
        _queue.value = PlaybackQueue()
        _nowPlaying.value = null
        playbackRepository.replaceQueue(emptyList())
    }

    suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val episodes = _queue.value.episodes
        if (fromIndex !in episodes.indices || toIndex !in episodes.indices || fromIndex == toIndex) return
        val player = controller()
        restoredQueueApplied = true
        ensurePlayerQueue(player)
        player.moveMediaItem(fromIndex, toIndex)
        val updated = episodes.toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) }
        _queue.value = PlaybackQueue(updated, controller?.currentMediaItem?.mediaId)
        playbackRepository.replaceQueue(updated.map { it.id })
    }

    suspend fun toggle() {
        controller().let { player ->
            val shouldPlay = !player.isPlaying
            if (shouldPlay) player.play() else player.pause()
            _nowPlaying.value = _nowPlaying.value?.copy(isPlaying = shouldPlay)
        }
    }

    suspend fun seekTo(positionMs: Long) {
        controller().let { player ->
            player.seekTo(positionMs)
            updateProgress(player)
        }
    }

    suspend fun seekBy(deltaMs: Long) {
        controller().let { player -> seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L)) }
    }

    suspend fun setSpeed(speed: Float) {
        settings.setDefaultSpeed(speed)
        controller().setPlaybackSpeed(speed)
        _nowPlaying.value = _nowPlaying.value?.copy(speed = speed)
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                controller?.let(::updateProgress)
                delay(500)
            }
        }
    }

    private fun updateProgress(player: MediaController) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        _nowPlaying.value = _nowPlaying.value?.copy(positionMs = player.currentPosition.coerceAtLeast(0L), durationMs = duration)
    }

    private suspend fun insertIntoQueue(episode: EpisodeEntity, requestedIndex: Int) {
        val player = controller()
        restoredQueueApplied = true
        ensurePlayerQueue(player)
        val current = _queue.value.episodes
        if (current.any { it.id == episode.id }) return
        val index = requestedIndex.coerceIn(0, current.size)
        player.addMediaItem(index, mediaItem(episode))
        val updated = current.toMutableList().also { it.add(index, episode) }
        _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId)
        playbackRepository.replaceQueue(updated.map { it.id })
    }

    private fun ensurePlayerQueue(player: MediaController) {
        val episodes = _queue.value.episodes
        val playerIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (playerIds == episodes.map { it.id }) return
        if (episodes.isEmpty()) {
            player.clearMediaItems()
            return
        }
        val startIndex = episodes.indexOfFirst { it.id == _queue.value.currentEpisodeId }.takeIf { it >= 0 } ?: 0
        player.setMediaItems(episodes.map(::mediaItem), startIndex, 0L)
    }

    private fun mediaItem(episode: EpisodeEntity) = MediaItem.Builder()
        .setMediaId(episode.id)
        .setUri(episode.audioUrl)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).setArtist(episode.description).setArtworkUri(episode.artworkUrl?.let(android.net.Uri::parse)).build())
        .build()
}
