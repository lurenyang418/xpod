package app.xpod.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import app.xpod.MainActivity
import app.xpod.data.PlaybackRepository
import app.xpod.data.XpodDatabase
import app.xpod.download.DownloadComponent
import app.xpod.util.runCatchingCancellable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService() {
  @Inject lateinit var playbackRepository: PlaybackRepository
  @Inject lateinit var database: XpodDatabase
  private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var session: MediaLibrarySession? = null
  private var persistenceJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    val cacheDataSource =
        CacheDataSource.Factory()
            .setCache(DownloadComponent.cache(this))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    val player =
        ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSource))
            .build()
            .apply {
              setAudioAttributes(
                  AudioAttributes.Builder()
                      .setUsage(C.USAGE_MEDIA)
                      .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                      .build(),
                  true,
              )
              addListener(
                  object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                      if (
                          events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                              events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                              events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                              events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                      )
                          save(player)
                    }
                  }
              )
            }
    val sessionActivity =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    session =
        MediaLibrarySession.Builder(
                this,
                player,
                object : MediaLibrarySession.Callback {
                  override fun onGetLibraryRoot(
                      session: MediaLibrarySession,
                      browser: MediaSession.ControllerInfo,
                      params: LibraryParams?,
                  ): ListenableFuture<LibraryResult<MediaItem>> =
                      Futures.immediateFuture(
                          LibraryResult.ofItem(browsableItem("root", "XPOD"), params)
                      )

                  override fun onGetChildren(
                      session: MediaLibrarySession,
                      browser: MediaSession.ControllerInfo,
                      parentId: String,
                      page: Int,
                      pageSize: Int,
                      params: LibraryParams?,
                  ): ListenableFuture<
                      LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>
                  > {
                    val items =
                        if (parentId == "root")
                            listOf(
                                browsableItem("subscriptions", "Subscriptions"),
                                browsableItem("downloads", "Downloads"),
                            )
                        else emptyList()
                    return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                  }

                  override fun onPlaybackResumption(
                      session: MediaSession,
                      controller: MediaSession.ControllerInfo,
                      isForPlayback: Boolean,
                  ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    ioScope.launch {
                      runCatchingCancellable {
                            val state = playbackRepository.state()
                            val id = state?.episodeId ?: error("No previous episode")
                            val episodes =
                                playbackRepository.queue().mapNotNull {
                                  database.episodes().find(it)
                                }
                            val queue = episodes.ifEmpty {
                              listOf(
                                  database.episodes().find(id)
                                      ?: error("Previous episode is unavailable")
                              )
                            }
                            val currentIndex = queue.indexOfFirst { it.id == id }.coerceAtLeast(0)
                            MediaSession.MediaItemsWithStartPosition(
                                queue.map { mediaItem(it.id, it.title, it.audioUrl) },
                                currentIndex,
                                state.positionMs,
                            ) to state.speed
                          }
                          .onSuccess { (items, speed) ->
                            playerScope.launch {
                              session.player.setPlaybackSpeed(speed)
                              future.set(items)
                            }
                          }
                          .onFailure(future::setException)
                    }
                    return future
                  }
                },
            )
            .setSessionActivity(sessionActivity)
            .build()
    persistenceJob = playerScope.launch {
      while (true) {
        delay(2_000)
        if (player.currentMediaItem != null) save(player)
      }
    }
  }

  private fun save(player: Player) {
    val snapshot = snapshot(player)
    ioScope.launch { persistSafely(snapshot) }
  }

  private fun snapshot(player: Player) =
      PlaybackSnapshot(
          episodeId = player.currentMediaItem?.mediaId,
          positionMs = player.currentPosition,
          durationMs = player.duration,
          speed = player.playbackParameters.speed,
      )

  private suspend fun persist(snapshot: PlaybackSnapshot) {
    playbackRepository.save(snapshot.episodeId, snapshot.positionMs, snapshot.speed)
    if (
        snapshot.episodeId != null &&
            snapshot.durationMs > 0 &&
            snapshot.positionMs.toDouble() / snapshot.durationMs >= 0.9
    ) {
      playbackRepository.markEpisodePlayed(snapshot.episodeId)
    }
  }

  private suspend fun persistSafely(snapshot: PlaybackSnapshot) {
    runCatchingCancellable { persist(snapshot) }
        .onFailure { Log.w("XPOD", "Unable to persist playback state", it) }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
      session

  override fun onTaskRemoved(rootIntent: Intent?) {
    session?.let { save(it.player) }
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    session?.let {
      save(it.player)
      it.player.release()
      it.release()
    }
    persistenceJob?.cancel()
    playerScope.cancel()
    session = null
    super.onDestroy()
  }

  companion object {
    fun component(context: android.content.Context) =
        ComponentName(context, PlaybackService::class.java)

    private fun browsableItem(id: String, title: String) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).build())
            .build()

    private fun mediaItem(id: String, title: String, url: String) =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsPlayable(true).build())
            .build()
  }

  private data class PlaybackSnapshot(
      val episodeId: String?,
      val positionMs: Long,
      val durationMs: Long,
      val speed: Float,
  )
}
