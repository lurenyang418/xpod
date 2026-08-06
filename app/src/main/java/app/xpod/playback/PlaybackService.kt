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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.xpod.MainActivity
import app.xpod.R
import app.xpod.data.MusicPlaybackSettings
import app.xpod.data.PlaybackItem
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PlaybackRepository
import app.xpod.data.PodcastEntity
import app.xpod.data.SettingsRepository
import app.xpod.data.XpodDatabase
import app.xpod.data.asPlaybackItem
import app.xpod.download.DownloadComponent
import app.xpod.util.runCatchingCancellable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
  @Inject lateinit var settings: SettingsRepository
  @Inject lateinit var database: XpodDatabase
  private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var session: MediaLibrarySession? = null
  private var periodicSaveJob: Job? = null
  private val persistence = ConflatedSerialExecutor(ioScope, ::persistSafely)
  private var acceptsPersistence = true
  private var lastMediaType = PlaybackMediaType.Podcast
  private var musicPlaybackSettings = MusicPlaybackSettings()
  private val musicPlaybackSettingsReady = CompletableDeferred<MusicPlaybackSettings>()

  override fun onCreate() {
    super.onCreate()
    val cachedHttpDataSource =
        CacheDataSource.Factory()
            .setCache(DownloadComponent.cache(this))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    val mediaDataSource = DefaultDataSource.Factory(this, cachedHttpDataSource)
    val player =
        ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(mediaDataSource))
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
                      if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        player.currentMediaItem?.mediaId?.let {
                          updatePlaybackBehavior(player, PlaybackMediaType.fromMediaId(it))
                        }
                      }
                      if (
                          events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                              events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                      ) {
                        val mediaType =
                            player.currentMediaItem?.mediaId?.let(PlaybackMediaType::fromMediaId)
                        if (mediaType == PlaybackMediaType.Music) {
                          musicPlaybackSettings =
                              MusicPlaybackSettings(
                                  shuffleEnabled = player.shuffleModeEnabled,
                                  repeatMode = player.repeatMode.toMusicRepeatMode(),
                              )
                        }
                      }
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
                          LibraryResult.ofItem(
                              browsableItem(ROOT_ID, getString(R.string.app_name)),
                              params,
                          )
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
                  > = loadLibraryChildren(parentId, page, pageSize, params)

                  override fun onAddMediaItems(
                      mediaSession: MediaSession,
                      controller: MediaSession.ControllerInfo,
                      mediaItems: List<MediaItem>,
                  ): ListenableFuture<List<MediaItem>> = resolveMediaItems(mediaItems)

                  override fun onPlaybackResumption(
                      session: MediaSession,
                      controller: MediaSession.ControllerInfo,
                      isForPlayback: Boolean,
                  ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    ioScope.launch {
                      runCatchingCancellable {
                            val state = playbackRepository.state()
                            val id = state?.mediaId ?: error("No previous item")
                            val mediaType = PlaybackMediaType.fromStored(state.mediaType)
                            val items =
                                playbackRepository.queue(mediaType).mapNotNull { reference ->
                                  when (reference.mediaType) {
                                    PlaybackMediaType.Podcast ->
                                        database
                                            .episodes()
                                            .find(reference.mediaId)
                                            ?.asPlaybackItem()
                                    PlaybackMediaType.Music ->
                                        database
                                            .localTracks()
                                            .find(reference.mediaId)
                                            ?.asPlaybackItem()
                                  }
                                }
                            val queue = items.ifEmpty {
                              listOf(
                                  when (mediaType) {
                                    PlaybackMediaType.Podcast ->
                                        database.episodes().find(id)?.asPlaybackItem()
                                    PlaybackMediaType.Music ->
                                        database.localTracks().find(id)?.asPlaybackItem()
                                  } ?: error("Previous item is unavailable")
                              )
                            }
                            val currentIndex = queue.indexOfFirst { it.id == id }.coerceAtLeast(0)
                            PlaybackResumption(
                                items =
                                    MediaSession.MediaItemsWithStartPosition(
                                        queue.map(::mediaItem),
                                        currentIndex,
                                        state.positionMs,
                                    ),
                                speed =
                                    state.speed
                                        .takeIf { mediaType == PlaybackMediaType.Podcast }
                                        .orDefault(),
                                mediaType = mediaType,
                                musicSettings = musicPlaybackSettingsReady.await(),
                            )
                          }
                          .onSuccess { resumption ->
                            playerScope.launch {
                              musicPlaybackSettings = resumption.musicSettings
                              updatePlaybackBehavior(
                                  session.player,
                                  resumption.mediaType,
                                  forcePlaybackSettings = true,
                              )
                              session.player.setPlaybackSpeed(resumption.speed)
                              future.set(resumption.items)
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
    ioScope.launch {
      val playbackSettings =
          runCatchingCancellable { settings.musicPlaybackSettingsValue() }
              .getOrElse {
                Log.w("XPOD", "Unable to restore music playback settings", it)
                MusicPlaybackSettings()
              }
      musicPlaybackSettingsReady.complete(playbackSettings)
      playerScope.launch {
        musicPlaybackSettings = playbackSettings
        player.currentMediaItem?.mediaId?.let(PlaybackMediaType::fromMediaId)?.let { mediaType ->
          updatePlaybackBehavior(player, mediaType, forcePlaybackSettings = true)
        }
      }
    }
    periodicSaveJob = playerScope.launch {
      while (true) {
        delay(2_000)
        if (player.currentMediaItem != null) save(player)
      }
    }
  }

  private fun save(player: Player) {
    if (!acceptsPersistence) return
    val snapshot = snapshot(player)
    if (!persistence.submit(snapshot)) {
      Log.w("XPOD", "Unable to queue playback state for persistence")
    }
  }

  private fun snapshot(player: Player) =
      PlaybackSnapshot(
          mediaId = player.currentMediaItem?.mediaId,
          mediaType =
              player.currentMediaItem?.mediaId?.let(PlaybackMediaType::fromMediaId)
                  ?: lastMediaType,
          positionMs = player.currentPosition,
          durationMs = player.duration,
          speed = player.playbackParameters.speed,
      )

  private suspend fun persist(snapshot: PlaybackSnapshot) {
    playbackRepository.save(
        snapshot.mediaId,
        snapshot.mediaType,
        snapshot.positionMs,
        snapshot.speed,
    )
    if (
        snapshot.mediaType == PlaybackMediaType.Podcast &&
            snapshot.mediaId != null &&
            snapshot.durationMs > 0 &&
            snapshot.positionMs.toDouble() / snapshot.durationMs >= 0.9
    ) {
      playbackRepository.markEpisodePlayed(snapshot.mediaId)
    }
  }

  private suspend fun persistSafely(snapshot: PlaybackSnapshot) {
    runCatchingCancellable { persist(snapshot) }
        .onFailure { Log.w("XPOD", "Unable to persist playback state", it) }
  }

  private fun updatePlaybackBehavior(
      player: Player,
      mediaType: PlaybackMediaType,
      forcePlaybackSettings: Boolean = false,
  ) {
    val mediaTypeChanged = lastMediaType != mediaType
    if (mediaTypeChanged) {
      player.setAudioAttributes(audioAttributes(mediaType), true)
      lastMediaType = mediaType
    }
    if (mediaTypeChanged || forcePlaybackSettings) {
      applyPlaybackSettings(player, mediaType, musicPlaybackSettings)
    }
  }

  private fun loadLibraryChildren(
      parentId: String,
      page: Int,
      pageSize: Int,
      params: LibraryParams?,
  ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
    val future =
        SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
    ioScope.launch {
      runCatchingCancellable {
            if (parentId.startsWith(PODCAST_ID_PREFIX)) {
              val offset = pageOffset(page, pageSize)
              if (offset == null) {
                emptyList()
              } else {
                database
                    .episodes()
                    .pageForPodcast(
                        parentId.removePrefix(PODCAST_ID_PREFIX),
                        limit = pageSize,
                        offset = offset,
                    )
                    .map { mediaItem(it.asPlaybackItem()) }
              }
            } else {
              val items =
                  when (parentId) {
                    ROOT_ID ->
                        listOf(
                            browsableItem(SUBSCRIPTIONS_ID, getString(R.string.subscriptions)),
                            browsableItem(DOWNLOADS_ID, getString(R.string.downloads)),
                            browsableItem(MUSIC_ID, getString(R.string.local_music)),
                        )
                    SUBSCRIPTIONS_ID -> database.podcasts().all().map(::podcastItem)
                    DOWNLOADS_ID -> downloadedEpisodes()
                    MUSIC_ID -> database.localTracks().all().map { mediaItem(it.asPlaybackItem()) }
                    else -> emptyList()
                  }
              pageItems(items, page, pageSize)
            }
          }
          .onSuccess { items -> future.set(LibraryResult.ofItemList(items, params)) }
          .onFailure { error ->
            Log.w("XPOD", "Unable to load media library children for $parentId", error)
            future.set(LibraryResult.ofError(SessionError.ERROR_IO, params))
          }
    }
    return future
  }

  private fun resolveMediaItems(
      requestedItems: List<MediaItem>
  ): ListenableFuture<List<MediaItem>> {
    val future = SettableFuture.create<List<MediaItem>>()
    ioScope.launch {
      runCatchingCancellable {
            requestedItems.mapNotNull { requested ->
              if (requested.localConfiguration != null) {
                requested
              } else {
                when (PlaybackMediaType.fromMediaId(requested.mediaId)) {
                  PlaybackMediaType.Podcast ->
                      database.episodes().find(requested.mediaId)?.asPlaybackItem()
                  PlaybackMediaType.Music ->
                      database.localTracks().find(requested.mediaId)?.asPlaybackItem()
                }?.let(::mediaItem)
              }
            }
          }
          .onSuccess(future::set)
          .onFailure(future::setException)
    }
    return future
  }

  private suspend fun downloadedEpisodes(): List<MediaItem> =
      DownloadComponent.manager(this)
          .downloadIndex
          .getDownloads(androidx.media3.exoplayer.offline.Download.STATE_COMPLETED)
          .use { cursor ->
            buildList {
              while (cursor.moveToNext()) {
                database.episodes().find(cursor.download.request.id)?.let { episode ->
                  add(mediaItem(episode.asPlaybackItem()))
                }
              }
            }
          }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
      session

  override fun onTaskRemoved(rootIntent: Intent?) {
    session?.let { save(it.player) }
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    val activeSession = session
    activeSession?.let { save(it.player) }
    acceptsPersistence = false
    persistence.close()
    persistence.invokeOnCompletion { ioScope.cancel() }
    activeSession?.player?.release()
    activeSession?.release()
    periodicSaveJob?.cancel()
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
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun podcastItem(podcast: PodcastEntity) =
        MediaItem.Builder()
            .setMediaId(PODCAST_ID_PREFIX + podcast.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(podcast.title)
                    .setArtist(podcast.author)
                    .setArtworkUri(podcast.artworkUrl?.let(android.net.Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

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
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun audioAttributes(mediaType: PlaybackMediaType) =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(
                if (mediaType == PlaybackMediaType.Music) C.AUDIO_CONTENT_TYPE_MUSIC
                else C.AUDIO_CONTENT_TYPE_SPEECH
            )
            .build()

    private const val ROOT_ID = "root"
    private const val SUBSCRIPTIONS_ID = "subscriptions"
    private const val DOWNLOADS_ID = "downloads"
    private const val MUSIC_ID = "music"
    private const val PODCAST_ID_PREFIX = "podcast:"
  }

  private data class PlaybackSnapshot(
      val mediaId: String?,
      val mediaType: PlaybackMediaType,
      val positionMs: Long,
      val durationMs: Long,
      val speed: Float,
  )

  private data class PlaybackResumption(
      val items: MediaSession.MediaItemsWithStartPosition,
      val speed: Float,
      val mediaType: PlaybackMediaType,
      val musicSettings: MusicPlaybackSettings,
  )
}

internal fun <T> pageItems(items: List<T>, page: Int, pageSize: Int): List<T> {
  val from = pageOffset(page, pageSize) ?: return emptyList()
  if (from >= items.size) return emptyList()
  return items.subList(from, minOf(from.toLong() + pageSize, items.size.toLong()).toInt())
}

internal fun pageOffset(page: Int, pageSize: Int): Int? {
  if (page < 0 || pageSize <= 0) return null
  return (page.toLong() * pageSize).takeIf { it <= Int.MAX_VALUE }?.toInt()
}

private fun Float?.orDefault(): Float = this ?: 1f
