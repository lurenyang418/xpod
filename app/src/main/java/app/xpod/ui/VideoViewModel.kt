package app.xpod.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.xpod.R
import app.xpod.data.LOCAL_VIDEO_ALL_FILES_SOURCE
import app.xpod.data.LOCAL_VIDEO_MEDIA_SOURCE
import app.xpod.data.LocalVideoEntity
import app.xpod.data.LocalVideoRepository
import app.xpod.data.appendRelativePath
import app.xpod.data.isGlobalVideoSource
import app.xpod.playback.PlaybackController
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VideoFolder(
    val path: String,
    val name: String,
    val videoCount: Int,
)

data class VideoPlayerState(
    val status: VideoPlaybackStatus = VideoPlaybackStatus.Paused,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val errorMessage: String? = null,
) {
  val isPlaying: Boolean
    get() = status == VideoPlaybackStatus.Playing || status == VideoPlaybackStatus.Buffering
}

enum class VideoPlaybackStatus {
  Playing,
  Paused,
  Buffering,
  Ended,
  Error,
}

data class VideoUiState(
    val videos: List<LocalVideoEntity> = emptyList(),
    val visibleVideos: List<LocalVideoEntity> = emptyList(),
    val visibleFolders: List<VideoFolder> = emptyList(),
    val playbackVideos: List<LocalVideoEntity> = emptyList(),
    val currentFolderPath: String = "",
    val selectedTreeUri: String? = null,
    val isGlobalSource: Boolean = false,
    val hasVideoPermission: Boolean = false,
    val query: String = "",
    val isScanning: Boolean = false,
    val playerVideoId: String? = null,
    val playbackQueue: List<LocalVideoEntity> = emptyList(),
    val player: VideoPlayerState = VideoPlayerState(),
)

internal data class VideoFolderContents(
    val currentFolderPath: String,
    val folders: List<VideoFolder>,
    val directVideos: List<LocalVideoEntity>,
    val playbackVideos: List<LocalVideoEntity>,
)

@HiltViewModel
class VideoViewModel
@Inject
constructor(
    private val localVideos: LocalVideoRepository,
    private val audioPlayback: PlaybackController,
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val folderPath = MutableStateFlow("")
  private val scanning = MutableStateFlow(false)
  private val playerVideoId = MutableStateFlow<String?>(null)
  private val _playerState = MutableStateFlow(VideoPlayerState())
  private val videoPermission = MutableStateFlow(localVideos.hasVideoPermission())
  private var scanJob: Job? = null
  private var progressJob: Job? = null
  private var progressWriteJob: Job? = null
  private val progressWrites = Channel<VideoProgressWrite>(Channel.UNLIMITED)
  private val playerQueueIds = MutableStateFlow<List<String>>(emptyList())
  private val playerMutationMutex = Mutex()
  private var automaticScanStarted = false
  private var automaticPermissionRequested = false
  private val _status = MutableStateFlow<UiStatus?>(null)
  val status: StateFlow<UiStatus?> = _status.asStateFlow()

  val player: ExoPlayer =
      ExoPlayer.Builder(context).build().apply {
        configureSeekIncrements()
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        addListener(
            object : Player.Listener {
              override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlayerState()
                if (playbackState == Player.STATE_ENDED) persistPosition()
              }

              override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlayerState()
                if (!isPlaying) persistPosition()
              }

              override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousId = playerVideoId.value
                val nextId = mediaItem?.mediaId
                if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        previousId != null &&
                        previousId != nextId
                ) {
                  val completedPosition =
                      state.value.videos
                          .firstOrNull { it.id == previousId }
                          ?.durationMs
                          ?.takeIf { it > 0L }
                          ?: _playerState.value.positionMs
                  enqueuePositionWrite(previousId, completedPosition)
                }
                playerVideoId.value = nextId
                syncPlayerState()
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && nextId != null) {
                  state.value.videos
                      .firstOrNull { it.id == nextId }
                      ?.let { nextVideo ->
                        val resumePosition =
                            videoResumePosition(nextVideo.lastPositionMs, nextVideo.durationMs)
                        if (resumePosition > 0L) player.seekTo(resumePosition)
                      }
                  syncPlayerState()
                }
              }

              override fun onPositionDiscontinuity(
                  oldPosition: Player.PositionInfo,
                  newPosition: Player.PositionInfo,
                  reason: Int,
              ) {
                syncPlayerState()
                persistPosition()
              }

              override fun onPlaybackParametersChanged(
                  playbackParameters: androidx.media3.common.PlaybackParameters
              ) {
                syncPlayerState()
              }

              override fun onPlayerError(error: PlaybackException) {
                _playerState.value =
                    _playerState.value.copy(
                        status = VideoPlaybackStatus.Error,
                        errorMessage = error.message,
                    )
                persistPosition()
              }
            }
        )
      }

  @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
  private fun ExoPlayer.configureSeekIncrements() {
    setSeekBackIncrementMs(10_000L)
    setSeekForwardIncrementMs(30_000L)
  }

  val state: StateFlow<VideoUiState> =
      combine(
              localVideos.videos,
              localVideos.treeUri,
              videoPermission,
              query,
              scanning,
              folderPath,
              playerVideoId,
              _playerState,
              playerQueueIds,
          ) { values ->
            @Suppress("UNCHECKED_CAST") val videos = values[0] as List<LocalVideoEntity>
            val treeUri = values[1] as String?
            val hasVideoPermission = values[2] as Boolean
            val requestedQuery = values[3] as String
            val isScanning = values[4] as Boolean
            val requestedFolderPath = values[5] as String
            val selectedVideoId = values[6] as String?
            val playerState = values[7] as VideoPlayerState
            @Suppress("UNCHECKED_CAST") val selectedQueueIds = values[8] as List<String>
            val normalizedQuery = requestedQuery.trim()
            val contents = videoFolderContents(videos, requestedFolderPath)
            val videosById = videos.associateBy(LocalVideoEntity::id)
            val searchVideos =
                if (normalizedQuery.isBlank()) {
                  emptyList()
                } else {
                  videos.filter { video ->
                    video.title.contains(normalizedQuery, ignoreCase = true) ||
                        video.relativePath.contains(normalizedQuery, ignoreCase = true)
                  }
                }
            VideoUiState(
                videos = videos,
                visibleVideos =
                    if (normalizedQuery.isBlank()) contents.directVideos else searchVideos,
                visibleFolders = if (normalizedQuery.isBlank()) contents.folders else emptyList(),
                playbackVideos =
                    if (normalizedQuery.isBlank()) contents.playbackVideos else searchVideos,
                currentFolderPath = contents.currentFolderPath,
                selectedTreeUri = treeUri,
                isGlobalSource = isGlobalVideoSource(treeUri),
                hasVideoPermission = hasVideoPermission,
                query = requestedQuery,
                isScanning = isScanning,
                playerVideoId = selectedVideoId,
                playbackQueue = selectedQueueIds.mapNotNull(videosById::get),
                player = playerState,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoUiState())

  init {
    progressWriteJob =
        viewModelScope.launch {
          for (write in progressWrites) {
            try {
              runCatchingCancellable {
                localVideos.updateProgress(write.id, write.positionMs, write.epochMs)
              }
            } finally {
              write.completion?.complete(Unit)
            }
          }
        }
    progressJob = viewModelScope.launch {
      while (isActive) {
        if (playerVideoId.value != null && player.isPlaying) {
          syncPlayerState()
          persistPosition()
        }
        delay(PROGRESS_POLL_INTERVAL_MS)
      }
    }
  }

  fun dismissStatus() {
    _status.value = null
  }

  fun onVideoScreenVisible() {
    videoPermission.value = localVideos.hasVideoPermission()
    if (!videoPermission.value || scanJob?.isActive == true || automaticScanStarted) return
    scanJob = viewModelScope.launch {
      val source = localVideos.sourceValue()
      val hasIndexedVideos = localVideos.hasIndexedVideos()
      if (shouldStartAutomaticVideoScan(source, hasIndexedVideos)) {
        automaticScanStarted = true
        scan {
          if (isGlobalVideoSource(source)) localVideos.refresh()
          else localVideos.enableAutoScan()
        }
      } else {
        scanJob = null
      }
    }
  }

  fun selectVideoFolder(uri: android.net.Uri) {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { scan { localVideos.selectTree(uri) } }
  }

  fun startAutomaticScan() {
    if (scanJob?.isActive == true || !videoPermission.value) return
    automaticScanStarted = true
    scanJob = viewModelScope.launch { scan { localVideos.enableAutoScan() } }
  }

  fun onVideoPermissionResult(granted: Boolean) {
    videoPermission.value = granted && localVideos.hasVideoPermission()
    if (granted) startAutomaticScan()
  }

  fun shouldRequestAutomaticVideoPermission(): Boolean {
    if (automaticPermissionRequested) return false
    automaticPermissionRequested = true
    return true
  }

  fun refresh() {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { scan { localVideos.refresh() } }
  }

  fun cancelScan() {
    if (scanJob?.isActive != true) return
    scanJob?.cancel()
    _status.value = UiStatus(context.getString(R.string.video_scan_cancelled))
  }

  fun setQuery(value: String) {
    query.value = value
  }

  fun openFolder(path: String) {
    folderPath.value = normalizeVideoPath(path)
    query.value = ""
  }

  fun openVideo(videoId: String, playlist: List<LocalVideoEntity> = emptyList()) {
    val currentState = state.value
    val video =
        currentState.videos.firstOrNull { it.id == videoId }
            ?: run {
              _status.value =
                  UiStatus(context.getString(R.string.could_not_open_video), StatusSeverity.Error)
              return
            }
    val queue = buildVideoPlaybackQueue(currentState.videos, playlist, video.id)
    val targetIndex = queue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
    viewModelScope.launch {
      playerMutationMutex.withLock {
        val audioWasPlaying = audioPlayback.nowPlaying.value?.isPlaying == true
        val result =
            runCatchingCancellable {
              runCatchingCancellable { audioPlayback.pause() }
                  .onFailure { Log.w("XPOD", "Unable to pause audio before video playback", it) }
              persistPositionNow()
              playerQueueIds.value = queue.map(LocalVideoEntity::id)
              player.setMediaItems(
                  queue.map { item ->
                    MediaItem.Builder().setMediaId(item.id).setUri(item.documentUri).build()
                  },
                  targetIndex,
                  videoResumePosition(video.lastPositionMs, video.durationMs),
              )
              playerVideoId.value = video.id
              _playerState.value = VideoPlayerState(status = VideoPlaybackStatus.Buffering)
              player.prepare()
              player.play()
              syncPlayerState()
              persistPosition()
            }
        if (result.isSuccess) return@withLock
        if (audioWasPlaying) {
          runCatchingCancellable {
                if (audioPlayback.nowPlaying.value?.isPlaying != true) audioPlayback.toggle()
              }
              .onFailure { Log.w("XPOD", "Unable to resume audio after video open failed", it) }
        }
        _status.value =
            UiStatus(context.getString(R.string.could_not_open_video), StatusSeverity.Error)
        playerVideoId.value = null
        playerQueueIds.value = emptyList()
        player.stop()
        player.clearMediaItems()
      }
    }
  }

  fun closeVideo() {
    viewModelScope.launch {
      playerMutationMutex.withLock {
        persistPositionNow()
        // Clear the id before stopping. Media3 emits pause/state callbacks from stop(), and
        // those callbacks must not persist the reset position (0) over the saved position.
        playerVideoId.value = null
        playerQueueIds.value = emptyList()
        player.stop()
        player.clearMediaItems()
        _playerState.value = VideoPlayerState()
      }
    }
  }

  fun togglePlayback() {
    when {
      player.playbackState == Player.STATE_ENDED -> {
        player.seekTo(0L)
        player.play()
      }
      player.playerError != null -> {
        player.prepare()
        player.play()
      }
      player.isPlaying -> player.pause()
      else -> player.play()
    }
    syncPlayerState()
  }

  fun seekTo(positionMs: Long) {
    player.seekTo(positionMs.coerceAtLeast(0L))
    syncPlayerState()
    persistPosition()
  }

  fun seekBy(deltaMs: Long) {
    seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
  }

  fun setSpeed(speed: Float) {
    val value = speed.coerceIn(0.5f, 2f)
    player.setPlaybackSpeed(value)
    syncPlayerState()
  }

  private suspend fun scan(block: suspend () -> Int) {
    scanning.value = true
    try {
      runCatchingCancellable { block() }
          .onSuccess { count ->
            _status.value =
                UiStatus(
                    context.resources.getQuantityString(
                        R.plurals.videos_scan_complete,
                        count,
                        count,
                    )
                )
          }
          .onFailure {
            automaticScanStarted = false
            _status.value =
                UiStatus(context.getString(R.string.videos_scan_failed), StatusSeverity.Error)
          }
    } finally {
      scanning.value = false
      scanJob = null
    }
  }

  private fun syncPlayerState() {
    val status =
        when {
          player.playerError != null -> VideoPlaybackStatus.Error
          player.playbackState == Player.STATE_BUFFERING -> VideoPlaybackStatus.Buffering
          player.playbackState == Player.STATE_ENDED -> VideoPlaybackStatus.Ended
          player.isPlaying -> VideoPlaybackStatus.Playing
          else -> VideoPlaybackStatus.Paused
        }
    _playerState.value =
        _playerState.value.copy(
            status = status,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: _playerState.value.durationMs,
            speed = player.playbackParameters.speed,
            errorMessage =
                if (status == VideoPlaybackStatus.Error) _playerState.value.errorMessage else null,
        )
  }

  private fun persistPosition() {
    currentProgressWrite()?.let { progressWrites.trySend(it) }
  }

  private suspend fun persistPositionNow() {
    val write = currentProgressWrite() ?: return
    val completion = CompletableDeferred<Unit>()
    progressWrites.send(write.copy(completion = completion))
    completion.await()
  }

  private fun currentProgressWrite(): VideoProgressWrite? {
    val id = playerVideoId.value ?: return null
    if (!shouldPersistVideoPosition(id, player.currentMediaItem?.mediaId)) return null
    return VideoProgressWrite(
        id = id,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        epochMs = clock.millis(),
    )
  }

  private fun enqueuePositionWrite(id: String, positionMs: Long) {
    progressWrites.trySend(
        VideoProgressWrite(
            id = id,
            positionMs = positionMs.coerceAtLeast(0L),
            epochMs = clock.millis(),
        )
    )
  }

  override fun onCleared() {
    progressJob?.cancel()
    progressWrites.close()
    progressWriteJob?.cancel()
    player.release()
    super.onCleared()
  }
}

private data class VideoProgressWrite(
    val id: String,
    val positionMs: Long,
    val epochMs: Long,
    val completion: CompletableDeferred<Unit>? = null,
)

internal fun videoFolderContents(
    videos: List<LocalVideoEntity>,
    requestedFolderPath: String,
): VideoFolderContents {
  val normalizedVideos = videos.map { it to normalizeVideoPath(it.relativePath) }
  val requestedPath = normalizeVideoPath(requestedFolderPath)
  val requestedPrefix = if (requestedPath.isBlank()) "" else "$requestedPath/"
  val currentPath =
      if (
          requestedPath.isBlank() ||
              normalizedVideos.any { (_, path) ->
                path == requestedPath || path.startsWith(requestedPrefix)
              }
      ) {
        requestedPath
      } else {
        ""
      }
  val prefix = if (currentPath.isBlank()) "" else "$currentPath/"
  val directVideos = normalizedVideos.filter { (_, path) -> path == currentPath }.map { it.first }
  val playbackVideos =
      normalizedVideos
          .filter { (_, path) ->
            currentPath.isBlank() || path == currentPath || path.startsWith(prefix)
          }
          .map { it.first }
  val childPaths = linkedSetOf<String>()
  normalizedVideos.forEach { (_, path) ->
    if (path == currentPath) return@forEach
    if (currentPath.isNotBlank() && !path.startsWith(prefix)) return@forEach
    val remainder = if (currentPath.isBlank()) path else path.removePrefix(prefix)
    if (remainder.isBlank()) return@forEach
    childPaths += appendRelativePath(currentPath, remainder.substringBefore('/'))
  }
  val folders =
      childPaths
          .sortedBy { it.substringAfterLast('/').lowercase(Locale.ROOT) }
          .map { path ->
            val folderPrefix = "$path/"
            VideoFolder(
                path = path,
                name = path.substringAfterLast('/'),
                videoCount =
                    normalizedVideos.count { (_, videoPath) ->
                      videoPath == path || videoPath.startsWith(folderPrefix)
                    },
            )
          }
  return VideoFolderContents(currentPath, folders, directVideos, playbackVideos)
}

internal fun normalizeVideoPath(path: String): String =
    path.trim('/').split('/').filter(String::isNotBlank).joinToString("/")

internal fun shouldStartAutomaticVideoScan(source: String?, hasIndexedVideos: Boolean): Boolean =
    source == null ||
        source == LOCAL_VIDEO_ALL_FILES_SOURCE ||
        (source == LOCAL_VIDEO_MEDIA_SOURCE && !hasIndexedVideos)

private const val PROGRESS_POLL_INTERVAL_MS = 2_000L
private const val RESUME_EPSILON_MS = 5_000L

internal fun videoResumePosition(lastPositionMs: Long, durationMs: Long): Long =
    lastPositionMs.takeIf {
      it > 0L && (durationMs <= 0L || it < durationMs - RESUME_EPSILON_MS)
    } ?: 0L

internal fun shouldPersistVideoPosition(expectedVideoId: String, currentMediaId: String?): Boolean =
    currentMediaId == expectedVideoId

internal fun buildVideoPlaybackQueue(
    videos: List<LocalVideoEntity>,
    requestedPlaylist: List<LocalVideoEntity>,
    startVideoId: String,
): List<LocalVideoEntity> {
  val videosById = videos.associateBy(LocalVideoEntity::id)
  return (requestedPlaylist.mapNotNull { videosById[it.id] } + videosById[startVideoId])
      .filterNotNull()
      .distinctBy(LocalVideoEntity::id)
}
