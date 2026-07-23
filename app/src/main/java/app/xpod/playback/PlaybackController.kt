package app.xpod.playback

import android.content.Context
import android.util.Log
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
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PlaybackStatus {
  Playing,
  Paused,
  Buffering,
  Ended,
  Error;

  val showsPauseAction: Boolean
    get() = this == Playing || this == Buffering
}

data class NowPlaying(
    val episode: EpisodeEntity,
    val status: PlaybackStatus,
    val speed: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
  val isPlaying: Boolean
    get() = status.showsPauseAction
}

data class PlaybackQueue(
    val episodes: List<EpisodeEntity> = emptyList(),
    val currentEpisodeId: String? = null,
)

@Singleton
class PlaybackController
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
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
  private val playbackMutationMutex = Mutex()
  private var restoredQueueApplied = false
  private val queueRestoreCompleted = CompletableDeferred<Unit>()

  init {
    scope.launch(Dispatchers.IO) {
      try {
        runCatchingCancellable {
              val persistedEpisodeIds = playbackRepository.queue()
              val restoredEpisodes = persistedEpisodeIds.mapNotNull { podcasts.episode(it) }
              val currentEpisodeId = playbackRepository.state()?.episodeId
              val currentIndex = restoredEpisodes.indexOfFirst { it.id == currentEpisodeId }
              val episodes = restoredEpisodes.moveItemToFront(currentIndex)
              withContext(Dispatchers.Main.immediate) {
                playbackMutationMutex.withLock {
                  if (!restoredQueueApplied) {
                    _queue.value =
                        PlaybackQueue(
                            episodes,
                            currentEpisodeId?.takeIf { id -> episodes.any { it.id == id } },
                        )
                    if (episodes.map(EpisodeEntity::id) != persistedEpisodeIds) {
                      playbackRepository.replaceQueue(episodes.map(EpisodeEntity::id))
                    }
                  }
                }
              }
            }
            .onFailure { Log.w("XPOD", "Unable to restore the playback queue", it) }
      } finally {
        queueRestoreCompleted.complete(Unit)
      }
    }
  }

  private suspend fun controller(): MediaController =
      controller
          ?: suspendCancellableCoroutine { continuation ->
            val token = SessionToken(context, PlaybackService.component(context))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                  runCatching { future.get() }
                      .onSuccess { created ->
                        controller = created
                        created.addListener(
                            object : Player.Listener {
                              override fun onEvents(player: Player, events: Player.Events) {
                                _nowPlaying.value =
                                    _nowPlaying.value?.copy(status = player.playbackStatus())
                                if (
                                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                                        shouldClearCompletedQueue(
                                            player.playbackState,
                                            player.currentMediaItemIndex,
                                            player.mediaItemCount,
                                        )
                                ) {
                                  scope.launch {
                                    playbackMutationMutex.withLock {
                                      clearCompletedQueue(created)
                                    }
                                  }
                                }
                              }

                              override fun onPlaybackParametersChanged(
                                  playbackParameters: androidx.media3.common.PlaybackParameters
                              ) {
                                _nowPlaying.value =
                                    _nowPlaying.value?.copy(speed = playbackParameters.speed)
                              }

                              override fun onMediaItemTransition(
                                  mediaItem: MediaItem?,
                                  reason: Int,
                              ) {
                                scope.launch {
                                  playbackMutationMutex.withLock {
                                    handleMediaItemTransition(created, mediaItem, reason)
                                  }
                                }
                              }
                            }
                        )
                        if (continuation.isActive) continuation.resume(created)
                      }
                      .onFailure { if (continuation.isActive) continuation.cancel(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
          }

  suspend fun play(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    startQueuePlayback(listOf(episode))
  }

  suspend fun playQueueItem(episodeId: String) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val episodes = _queue.value.episodes
    val index = episodes.indexOfFirst { it.id == episodeId }
    if (index < 0) return@withLock
    startQueuePlayback(episodes.moveItemToFront(index))
  }

  suspend fun playNext(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    val index =
        if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex.coerceAtLeast(0) + 1
    insertIntoQueue(player, episode, index)
  }

  suspend fun addToQueue(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    insertIntoQueue(player, episode, _queue.value.episodes.size)
  }

  suspend fun removeFromQueue(episodeId: String) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    val episodes = _queue.value.episodes
    val index = episodes.indexOfFirst { it.id == episodeId }
    if (index < 0) return@withLock
    val removingCurrent =
        episodeId == _queue.value.currentEpisodeId || episodeId == _nowPlaying.value?.episode?.id
    val updated = episodes.toMutableList().also { it.removeAt(index) }
    playbackRepository.replaceQueue(updated.map { it.id })
    try {
      player.removeMediaItem(index)
    } catch (error: Throwable) {
      rollbackPersistedQueue(episodes, error)
      throw error
    }
    val currentEpisodeId = player.currentMediaItem?.mediaId
    _queue.value = PlaybackQueue(updated, currentEpisodeId)
    if (removingCurrent) {
      _nowPlaying.value =
          updated.firstOrNull { it.id == currentEpisodeId }?.let { nowPlayingSnapshot(player, it) }
    }
  }

  suspend fun removeDeletedEpisodes(episodeIds: Set<String>) {
    if (episodeIds.isEmpty()) return
    queueRestoreCompleted.await()
    playbackMutationMutex.withLock {
      restoredQueueApplied = true
      val previous = _queue.value
      val updated = previous.episodes.filterNot { it.id in episodeIds }
      var activePlayer: MediaController? = null
      runCatchingCancellable {
            controller().also { player ->
              activePlayer = player
              val indexes =
                  (0 until player.mediaItemCount).filter { index ->
                    player.getMediaItemAt(index).mediaId in episodeIds
                  }
              indexes.asReversed().forEach(player::removeMediaItem)
            }
          }
          .onFailure { error ->
            Log.w("XPOD", "Unable to remove deleted episodes from the active player", error)
            activePlayer?.let { player ->
              runCatching { player.clearMediaItems() }
                  .onFailure { Log.w("XPOD", "Unable to reset the active player", it) }
            }
          }
      val currentEpisodeId =
          activePlayer?.currentMediaItem?.mediaId?.takeIf { id -> updated.any { it.id == id } }
              ?: previous.currentEpisodeId?.takeIf { it !in episodeIds && activePlayer == null }
      _queue.value = PlaybackQueue(updated, currentEpisodeId)
      if (_nowPlaying.value?.episode?.id in episodeIds || currentEpisodeId == null) {
        _nowPlaying.value = currentEpisodeId?.let { id ->
          val episode = updated.firstOrNull { it.id == id } ?: return@let null
          activePlayer?.let { nowPlayingSnapshot(it, episode) }
        }
      }
      if (updated.isEmpty()) progressJob?.cancel()
    }
  }

  suspend fun clearQueue() = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    clearQueue(controller())
  }

  suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackMutationMutex.withLock {
    val episodes = _queue.value.episodes
    if (fromIndex !in episodes.indices || toIndex !in episodes.indices || fromIndex == toIndex)
        return@withLock
    val currentIndex = episodes.indexOfFirst { it.id == _queue.value.currentEpisodeId }
    if (currentIndex >= 0 && (fromIndex == currentIndex || toIndex <= currentIndex)) return@withLock
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    val updated = episodes.toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) }
    playbackRepository.replaceQueue(updated.map { it.id })
    try {
      player.moveMediaItem(fromIndex, toIndex)
    } catch (error: Throwable) {
      rollbackPersistedQueue(episodes, error)
      throw error
    }
    _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId)
  }

  suspend fun toggle() = playbackMutationMutex.withLock {
    controller().let { player ->
      when {
        player.playbackState == Player.STATE_ENDED -> {
          player.seekToDefaultPosition()
          player.prepare()
          player.play()
        }
        player.playerError != null -> {
          player.prepare()
          player.play()
        }
        player.playWhenReady -> player.pause()
        else -> player.play()
      }
      _nowPlaying.value = _nowPlaying.value?.copy(status = player.playbackStatus())
    }
  }

  suspend fun seekTo(positionMs: Long) = playbackMutationMutex.withLock {
    controller().let { player ->
      player.seekTo(positionMs)
      updateProgress(player)
    }
  }

  suspend fun seekBy(deltaMs: Long) = playbackMutationMutex.withLock {
    controller().let { player ->
      player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
      updateProgress(player)
    }
  }

  suspend fun setSpeed(speed: Float) = playbackMutationMutex.withLock {
    settings.setDefaultSpeed(speed)
    controller().setPlaybackSpeed(speed)
    _nowPlaying.value = _nowPlaying.value?.copy(speed = speed)
  }

  private suspend fun startQueuePlayback(episodes: List<EpisodeEntity>) {
    val player = controller()
    val speed = settings.defaultSpeed.first()
    val previous = _queue.value.episodes
    playbackRepository.replaceQueue(episodes.map { it.id })
    try {
      player.setMediaItems(episodes.map(::mediaItem), 0, 0L)
      player.setPlaybackSpeed(speed)
      player.prepare()
      player.play()
    } catch (error: Throwable) {
      rollbackPersistedQueue(previous, error)
      throw error
    }
    val episode = episodes.first()
    _nowPlaying.value = NowPlaying(episode, status = player.playbackStatus(), speed = speed)
    _queue.value = PlaybackQueue(episodes, episode.id)
    startProgressUpdates()
  }

  private suspend fun handleMediaItemTransition(
      player: MediaController,
      mediaItem: MediaItem?,
      reason: Int,
  ) {
    if (mediaItem == null) {
      _nowPlaying.value = null
      _queue.value = _queue.value.copy(currentEpisodeId = null)
      return
    }
    val queue = _queue.value
    val episodeIndex = queue.episodes.indexOfFirst { it.id == mediaItem.mediaId }
    val episode = queue.episodes.getOrNull(episodeIndex) ?: return
    val requestedAction = queueTransitionAction(reason, episodeIndex)
    val playerIndex = player.currentMediaItemIndex
    val appliedAction = applyPlayerTransitionAction(player, requestedAction, playerIndex)
    val episodes = queue.episodes.applyTransition(appliedAction, episodeIndex)
    _nowPlaying.value = nowPlayingSnapshot(player, episode)
    _queue.value = PlaybackQueue(episodes, episode.id)
    if (appliedAction != QueueTransitionAction.Keep) persistQueueSafely(episodes)
  }

  private suspend fun clearCompletedQueue(player: MediaController) {
    if (
        !shouldClearCompletedQueue(
            player.playbackState,
            player.currentMediaItemIndex,
            player.mediaItemCount,
        )
    ) {
      return
    }
    restoredQueueApplied = true
    runCatchingCancellable { clearQueue(player) }
        .onFailure { Log.w("XPOD", "Unable to clear completed playback queue", it) }
  }

  private suspend fun clearQueue(player: MediaController) {
    val previous = _queue.value.episodes
    playbackRepository.replaceQueue(emptyList())
    try {
      player.clearMediaItems()
    } catch (error: Throwable) {
      rollbackPersistedQueue(previous, error)
      throw error
    }
    progressJob?.cancel()
    _queue.value = PlaybackQueue()
    _nowPlaying.value = null
  }

  private fun applyPlayerTransitionAction(
      player: MediaController,
      action: QueueTransitionAction,
      playerIndex: Int,
  ): QueueTransitionAction {
    if (action == QueueTransitionAction.Keep || playerIndex <= 0) return action
    if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
      Log.w("XPOD", "Unable to reorder playback queue: command unavailable")
      return QueueTransitionAction.Keep
    }
    return runCatching {
          when (action) {
            QueueTransitionAction.Keep -> Unit
            QueueTransitionAction.ConsumeEarlier -> player.removeMediaItems(0, playerIndex)
            QueueTransitionAction.PromoteCurrent -> player.moveMediaItem(playerIndex, 0)
          }
          action
        }
        .getOrElse {
          Log.w("XPOD", "Unable to reorder playback queue", it)
          QueueTransitionAction.Keep
        }
  }

  private fun nowPlayingSnapshot(
      player: MediaController,
      episode: EpisodeEntity,
  ): NowPlaying =
      NowPlaying(
          episode = episode,
          status = player.playbackStatus(),
          speed = player.playbackParameters.speed,
          positionMs = player.currentPosition.coerceAtLeast(0L),
          durationMs = player.duration.takeIf { it > 0L } ?: 0L,
      )

  private suspend fun persistQueueSafely(episodes: List<EpisodeEntity>) {
    runCatchingCancellable { playbackRepository.replaceQueue(episodes.map { it.id }) }
        .onFailure { Log.w("XPOD", "Unable to persist playback queue", it) }
  }

  private suspend fun rollbackPersistedQueue(
      episodes: List<EpisodeEntity>,
      originalError: Throwable,
  ) {
    runCatchingCancellable { playbackRepository.replaceQueue(episodes.map { it.id }) }
        .onFailure {
          originalError.addSuppressed(it)
          Log.w("XPOD", "Unable to roll back playback queue", it)
        }
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
    _nowPlaying.value =
        _nowPlaying.value?.copy(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
  }

  private suspend fun insertIntoQueue(
      player: MediaController,
      episode: EpisodeEntity,
      requestedIndex: Int,
  ) {
    val current = _queue.value.episodes
    if (current.any { it.id == episode.id }) return
    val index = requestedIndex.coerceIn(0, current.size)
    val updated = current.toMutableList().also { it.add(index, episode) }
    playbackRepository.replaceQueue(updated.map { it.id })
    try {
      player.addMediaItem(index, mediaItem(episode))
    } catch (error: Throwable) {
      rollbackPersistedQueue(current, error)
      throw error
    }
    _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId)
  }

  private fun ensurePlayerQueue(player: MediaController) {
    val episodes = _queue.value.episodes
    val playerIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
    if (playerIds == episodes.map { it.id }) return
    if (episodes.isEmpty()) {
      player.clearMediaItems()
      return
    }
    val startIndex =
        episodes.indexOfFirst { it.id == _queue.value.currentEpisodeId }.takeIf { it >= 0 } ?: 0
    player.setMediaItems(episodes.map(::mediaItem), startIndex, 0L)
  }

  private fun mediaItem(episode: EpisodeEntity) =
      MediaItem.Builder()
          .setMediaId(episode.id)
          .setUri(episode.audioUrl)
          .setMediaMetadata(
              MediaMetadata.Builder()
                  .setTitle(episode.title)
                  .setArtist(episode.description)
                  .setArtworkUri(episode.artworkUrl?.let(android.net.Uri::parse))
                  .build()
          )
          .build()
}
