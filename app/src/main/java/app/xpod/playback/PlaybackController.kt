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
import app.xpod.data.LocalMusicRepository
import app.xpod.data.LocalTrackEntity
import app.xpod.data.PlaybackItem
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PlaybackReference
import app.xpod.data.PlaybackRepository
import app.xpod.data.PodcastRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.asPlaybackItem
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
    val item: PlaybackItem,
    val status: PlaybackStatus,
    val speed: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
  val isPlaying: Boolean
    get() = status.showsPauseAction
}

data class PlaybackQueue(
    val items: List<PlaybackItem> = emptyList(),
    val currentMediaId: String? = null,
    val mediaType: PlaybackMediaType? = null,
)

@Singleton
class PlaybackController
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val podcasts: PodcastRepository,
    private val localMusic: LocalMusicRepository,
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
              val state = playbackRepository.state()
              val mediaType =
                  PlaybackMediaType.fromStored(state?.mediaType ?: PlaybackMediaType.Podcast.name)
              val persistedItems = playbackRepository.queue(mediaType)
              val currentMediaId = state?.mediaId
              val restoredItems =
                  persistedItems
                      .mapNotNull { resolve(it) }
                      .ifEmpty {
                        currentMediaId
                            ?.let { resolve(PlaybackReference(it, mediaType)) }
                            ?.let(::listOf)
                            .orEmpty()
                      }
              val currentIndex = restoredItems.indexOfFirst { it.id == currentMediaId }
              val items =
                  if (mediaType == PlaybackMediaType.Podcast)
                      restoredItems.moveItemToFront(currentIndex)
                  else restoredItems
              withContext(Dispatchers.Main.immediate) {
                playbackMutationMutex.withLock {
                  if (!restoredQueueApplied) {
                    _queue.value =
                        PlaybackQueue(
                            items,
                            currentMediaId?.takeIf { id -> items.any { it.id == id } },
                            mediaType.takeIf { items.isNotEmpty() },
                        )
                    if (items.map(PlaybackItem::id) != persistedItems.map { it.mediaId }) {
                      playbackRepository.replaceQueue(mediaType, items.map(PlaybackItem::id))
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
    scope.launch {
      queueRestoreCompleted.await()
      runCatchingCancellable {
            playbackMutationMutex.withLock { synchronizeActivePlayback(controller()) }
          }
          .onFailure { Log.w("XPOD", "Unable to synchronize active playback", it) }
    }
  }

  private suspend fun controller(): MediaController =
      controller
          ?: suspendCancellableCoroutine { continuation ->
            val token = SessionToken(context, PlaybackService.component(context))
            val future =
                MediaController.Builder(context, token)
                    .setListener(
                        object : MediaController.Listener {
                          override fun onDisconnected(mediaController: MediaController) {
                            if (controller === mediaController) {
                              controller = null
                              progressJob?.cancel()
                              progressJob = null
                              _nowPlaying.value =
                                  _nowPlaying.value?.copy(status = PlaybackStatus.Error)
                            }
                          }
                        }
                    )
                    .buildAsync()
            future.addListener(
                {
                  runCatching { future.get() }
                      .onSuccess { created ->
                        if (!continuation.isActive) {
                          created.release()
                          return@onSuccess
                        }
                        controller = created
                        created.addListener(
                            object : Player.Listener {
                              override fun onEvents(player: Player, events: Player.Events) {
                                _nowPlaying.value =
                                    _nowPlaying.value?.copy(status = player.playbackStatus())
                                if (
                                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                                        _queue.value.mediaType == PlaybackMediaType.Podcast &&
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
                        continuation.resume(created)
                      }
                      .onFailure { if (continuation.isActive) continuation.cancel(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
          }

  suspend fun play(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    startQueuePlayback(listOf(episode.asPlaybackItem()), 0)
  }

  suspend fun playMusic(tracks: List<LocalTrackEntity>, startTrackId: String) =
      playbackMutationMutex.withLock {
        restoredQueueApplied = true
        val items = tracks.map(LocalTrackEntity::asPlaybackItem)
        val startIndex = items.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        startQueuePlayback(items, startIndex)
      }

  suspend fun playQueueItem(mediaId: String) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val queue = _queue.value
    val index = queue.items.indexOfFirst { it.id == mediaId }
    if (index < 0) return@withLock
    if (queue.mediaType == PlaybackMediaType.Podcast) {
      startQueuePlayback(queue.items.moveItemToFront(index), 0)
    } else {
      startQueuePlayback(queue.items, index)
    }
  }

  suspend fun playNext(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    insertNext(episode.asPlaybackItem())
  }

  suspend fun playNext(track: LocalTrackEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    insertNext(track.asPlaybackItem())
  }

  private suspend fun insertNext(item: PlaybackItem) {
    if (_queue.value.mediaType != null && _queue.value.mediaType != item.mediaType) {
      addToInactiveQueue(item, next = true)
      return
    }
    val player = controller()
    ensurePlayerQueue(player)
    val index =
        if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex.coerceAtLeast(0) + 1
    insertIntoQueue(player, item, index)
  }

  suspend fun addToQueue(episode: EpisodeEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    addItemToQueue(episode.asPlaybackItem())
  }

  suspend fun addToQueue(track: LocalTrackEntity) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    addItemToQueue(track.asPlaybackItem())
  }

  private suspend fun addItemToQueue(item: PlaybackItem) {
    if (_queue.value.mediaType != null && _queue.value.mediaType != item.mediaType) {
      addToInactiveQueue(item, next = false)
      return
    }
    val player = controller()
    ensurePlayerQueue(player)
    insertIntoQueue(player, item, _queue.value.items.size)
  }

  suspend fun removeFromQueue(mediaId: String) = playbackMutationMutex.withLock {
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    val queue = _queue.value
    val items = queue.items
    val mediaType = queue.mediaType ?: return@withLock
    val index = items.indexOfFirst { it.id == mediaId }
    if (index < 0) return@withLock
    val removingCurrent = mediaId == queue.currentMediaId || mediaId == _nowPlaying.value?.item?.id
    val updated = items.toMutableList().also { it.removeAt(index) }
    playbackRepository.replaceQueue(mediaType, updated.map { it.id })
    try {
      player.removeMediaItem(index)
    } catch (error: Throwable) {
      rollbackPersistedQueue(mediaType, items, error)
      throw error
    }
    val currentMediaId = player.currentMediaItem?.mediaId
    _queue.value = PlaybackQueue(updated, currentMediaId, mediaType)
    if (removingCurrent) {
      _nowPlaying.value =
          updated.firstOrNull { it.id == currentMediaId }?.let { nowPlayingSnapshot(player, it) }
    }
  }

  suspend fun removeDeletedEpisodes(episodeIds: Set<String>) {
    if (episodeIds.isEmpty()) return
    queueRestoreCompleted.await()
    playbackMutationMutex.withLock {
      restoredQueueApplied = true
      val previous = _queue.value
      if (previous.mediaType != PlaybackMediaType.Podcast) {
        val stored = playbackRepository.queue(PlaybackMediaType.Podcast)
        playbackRepository.replaceQueue(
            PlaybackMediaType.Podcast,
            stored.map { it.mediaId }.filterNot { it in episodeIds },
        )
        return@withLock
      }
      val updated = previous.items.filterNot { it.id in episodeIds }
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
      val currentMediaId =
          activePlayer?.currentMediaItem?.mediaId?.takeIf { id -> updated.any { it.id == id } }
              ?: previous.currentMediaId?.takeIf { it !in episodeIds && activePlayer == null }
      _queue.value = PlaybackQueue(updated, currentMediaId, PlaybackMediaType.Podcast)
      if (_nowPlaying.value?.item?.id in episodeIds || currentMediaId == null) {
        _nowPlaying.value = currentMediaId?.let { id ->
          val item = updated.firstOrNull { it.id == id } ?: return@let null
          activePlayer?.let { nowPlayingSnapshot(it, item) }
        }
      }
      if (updated.isEmpty()) progressJob?.cancel()
    }
  }

  suspend fun removeMissingLocalTracks(availableTrackIds: Set<String>) {
    queueRestoreCompleted.await()
    playbackMutationMutex.withLock {
      val stored = playbackRepository.queue(PlaybackMediaType.Music)
      playbackRepository.replaceQueue(
          PlaybackMediaType.Music,
          stored.map { it.mediaId }.filter { it in availableTrackIds },
      )
      val previous = _queue.value
      if (previous.mediaType != PlaybackMediaType.Music) return@withLock
      val removedIds = previous.items.map(PlaybackItem::id).toSet() - availableTrackIds
      if (removedIds.isEmpty()) return@withLock
      restoredQueueApplied = true
      val player = controller()
      ensurePlayerQueue(player)
      val indexes =
          (0 until player.mediaItemCount).filter { index ->
            player.getMediaItemAt(index).mediaId in removedIds
          }
      indexes.asReversed().forEach(player::removeMediaItem)
      val updated = previous.items.filter { it.id in availableTrackIds }
      val currentMediaId =
          player.currentMediaItem?.mediaId?.takeIf { id -> updated.any { it.id == id } }
      _queue.value = PlaybackQueue(updated, currentMediaId, PlaybackMediaType.Music)
      if (_nowPlaying.value?.item?.id in removedIds) {
        _nowPlaying.value = currentMediaId?.let { id ->
          updated.firstOrNull { it.id == id }?.let { nowPlayingSnapshot(player, it) }
        }
      }
      if (updated.isEmpty()) {
        progressJob?.cancel()
        _nowPlaying.value = null
      }
    }
  }

  suspend fun clearQueue() {
    queueRestoreCompleted.await()
    playbackMutationMutex.withLock {
      restoredQueueApplied = true
      clearQueue(controller())
    }
  }

  suspend fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackMutationMutex.withLock {
    val queue = _queue.value
    val items = queue.items
    val mediaType = queue.mediaType ?: return@withLock
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex)
        return@withLock
    val currentIndex = items.indexOfFirst { it.id == queue.currentMediaId }
    if (
        mediaType == PlaybackMediaType.Podcast &&
            currentIndex >= 0 &&
            (fromIndex == currentIndex || toIndex <= currentIndex)
    )
        return@withLock
    restoredQueueApplied = true
    val player = controller()
    ensurePlayerQueue(player)
    val updated = items.toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) }
    playbackRepository.replaceQueue(mediaType, updated.map { it.id })
    try {
      player.moveMediaItem(fromIndex, toIndex)
    } catch (error: Throwable) {
      rollbackPersistedQueue(mediaType, items, error)
      throw error
    }
    _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId, mediaType)
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
    if (_queue.value.mediaType == PlaybackMediaType.Music) return@withLock
    settings.setDefaultSpeed(speed)
    controller().setPlaybackSpeed(speed)
    _nowPlaying.value = _nowPlaying.value?.copy(speed = speed)
  }

  suspend fun skipToNext() = playbackMutationMutex.withLock {
    controller().let { player ->
      if (player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
        player.play()
      }
    }
  }

  suspend fun skipToPrevious() = playbackMutationMutex.withLock {
    controller().let { player ->
      if (player.currentPosition > 3_000L || !player.hasPreviousMediaItem()) {
        player.seekTo(0L)
      } else {
        player.seekToPreviousMediaItem()
      }
      player.play()
    }
  }

  private suspend fun startQueuePlayback(items: List<PlaybackItem>, startIndex: Int) {
    if (items.isEmpty()) return
    val mediaType = items.first().mediaType
    require(items.all { it.mediaType == mediaType }) { "Playback queues cannot mix media types" }
    val player = controller()
    val speed = if (mediaType == PlaybackMediaType.Podcast) settings.defaultSpeed.first() else 1f
    val previous =
        _queue.value.takeIf { it.mediaType == mediaType }?.items
            ?: playbackRepository.queue(mediaType).mapNotNull { resolve(it) }
    playbackRepository.replaceQueue(mediaType, items.map { it.id })
    try {
      player.setMediaItems(items.map(::mediaItem), startIndex.coerceIn(items.indices), 0L)
      player.setPlaybackSpeed(speed)
      player.prepare()
      player.play()
    } catch (error: Throwable) {
      rollbackPersistedQueue(mediaType, previous, error)
      throw error
    }
    val item = items[startIndex.coerceIn(items.indices)]
    _nowPlaying.value = nowPlayingSnapshot(player, item)
    _queue.value = PlaybackQueue(items, item.id, mediaType)
    startProgressUpdates()
  }

  private fun synchronizeActivePlayback(player: MediaController) {
    val mediaId = player.currentMediaItem?.mediaId ?: return
    val queue = _queue.value
    val item = queue.items.firstOrNull { it.id == mediaId } ?: return
    _queue.value = queue.copy(currentMediaId = mediaId, mediaType = item.mediaType)
    _nowPlaying.value = nowPlayingSnapshot(player, item)
    startProgressUpdates()
  }

  private suspend fun handleMediaItemTransition(
      player: MediaController,
      mediaItem: MediaItem?,
      reason: Int,
  ) {
    if (mediaItem == null) {
      _nowPlaying.value = null
      _queue.value = _queue.value.copy(currentMediaId = null)
      return
    }
    val queue = _queue.value
    val itemIndex = queue.items.indexOfFirst { it.id == mediaItem.mediaId }
    val item = queue.items.getOrNull(itemIndex) ?: return
    val requestedAction =
        if (queue.mediaType == PlaybackMediaType.Music) QueueTransitionAction.Keep
        else queueTransitionAction(reason, itemIndex)
    val playerIndex = player.currentMediaItemIndex
    val appliedAction = applyPlayerTransitionAction(player, requestedAction, playerIndex)
    val items = queue.items.applyTransition(appliedAction, itemIndex)
    _nowPlaying.value = nowPlayingSnapshot(player, item)
    _queue.value = PlaybackQueue(items, item.id, item.mediaType)
    startProgressUpdates()
    if (appliedAction != QueueTransitionAction.Keep) persistQueueSafely(item.mediaType, items)
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
    val queue = _queue.value
    val mediaType = queue.mediaType ?: return
    val previous = queue.items
    playbackRepository.replaceQueue(mediaType, emptyList())
    try {
      player.clearMediaItems()
    } catch (error: Throwable) {
      rollbackPersistedQueue(mediaType, previous, error)
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
      item: PlaybackItem,
  ): NowPlaying =
      NowPlaying(
          item = item,
          status = player.playbackStatus(),
          speed = player.playbackParameters.speed,
          positionMs = player.currentPosition.coerceAtLeast(0L),
          durationMs = playbackDuration(player.duration, item.durationMs),
      )

  private suspend fun persistQueueSafely(
      mediaType: PlaybackMediaType,
      items: List<PlaybackItem>,
  ) {
    runCatchingCancellable {
          playbackRepository.replaceQueue(mediaType, items.map(PlaybackItem::id))
        }
        .onFailure { Log.w("XPOD", "Unable to persist playback queue", it) }
  }

  private suspend fun rollbackPersistedQueue(
      mediaType: PlaybackMediaType,
      items: List<PlaybackItem>,
      originalError: Throwable,
  ) {
    runCatchingCancellable {
          playbackRepository.replaceQueue(mediaType, items.map(PlaybackItem::id))
        }
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
    val current = _nowPlaying.value ?: return
    _nowPlaying.value =
        current.copy(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = playbackDuration(player.duration, current.item.durationMs),
        )
  }

  private suspend fun insertIntoQueue(
      player: MediaController,
      item: PlaybackItem,
      requestedIndex: Int,
  ) {
    val queue = _queue.value
    val mediaType = queue.mediaType ?: item.mediaType
    require(mediaType == item.mediaType) { "Playback queues cannot mix media types" }
    val current = queue.items
    if (current.any { it.id == item.id }) return
    val index = requestedIndex.coerceIn(0, current.size)
    val updated = current.toMutableList().also { it.add(index, item) }
    playbackRepository.replaceQueue(mediaType, updated.map { it.id })
    try {
      player.addMediaItem(index, mediaItem(item))
    } catch (error: Throwable) {
      rollbackPersistedQueue(mediaType, current, error)
      throw error
    }
    _queue.value = PlaybackQueue(updated, player.currentMediaItem?.mediaId, mediaType)
  }

  private fun ensurePlayerQueue(player: MediaController) {
    val items = _queue.value.items
    val playerIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
    if (playerIds == items.map { it.id }) return
    if (items.isEmpty()) {
      player.clearMediaItems()
      return
    }
    val startIndex =
        items.indexOfFirst { it.id == _queue.value.currentMediaId }.takeIf { it >= 0 } ?: 0
    player.setMediaItems(items.map(::mediaItem), startIndex, 0L)
  }

  private suspend fun addToInactiveQueue(item: PlaybackItem, next: Boolean) {
    val references = playbackRepository.queue(item.mediaType)
    val items = references.mapNotNull { resolve(it) }.filterNot { it.id == item.id }.toMutableList()
    val currentId = playbackRepository.state(item.mediaType)?.mediaId
    val index =
        if (next) {
          items.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.plus(1) ?: 0
        } else {
          items.size
        }
    items.add(index.coerceIn(0, items.size), item)
    playbackRepository.replaceQueue(item.mediaType, items.map(PlaybackItem::id))
  }

  private suspend fun resolve(reference: PlaybackReference): PlaybackItem? =
      when (reference.mediaType) {
        PlaybackMediaType.Podcast -> podcasts.episode(reference.mediaId)?.asPlaybackItem()
        PlaybackMediaType.Music -> localMusic.track(reference.mediaId)?.asPlaybackItem()
      }

  private fun mediaItem(item: PlaybackItem) =
      MediaItem.Builder()
          .setMediaId(item.id)
          .setUri(item.uri)
          .setMediaMetadata(
              MediaMetadata.Builder()
                  .setTitle(item.title)
                  .setArtist(item.subtitle)
                  .setArtworkUri(item.artworkUri?.let(android.net.Uri::parse))
                  .setDurationMs(item.durationMs)
                  .build()
          )
          .build()
}

internal fun playbackDuration(playerDurationMs: Long, itemDurationMs: Long?): Long =
    playerDurationMs.takeIf { it > 0L } ?: itemDurationMs?.takeIf { it > 0L } ?: 0L
