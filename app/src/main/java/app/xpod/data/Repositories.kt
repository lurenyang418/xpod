package app.xpod.data

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.room.withTransaction
import app.xpod.download.DownloadComponent
import app.xpod.download.DownloadPreferences
import app.xpod.download.XpodDownloadService
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private val Context.settingsStore by preferencesDataStore("settings")

enum class ThemeMode {
  System,
  Light,
  Dark,
}

enum class ReadingTheme {
  FollowApp,
  Light,
  Sepia,
  Dark,
}

data class ReadingPreferences(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.55f,
    val theme: ReadingTheme = ReadingTheme.FollowApp,
)

internal fun parseReadingTheme(value: String?): ReadingTheme =
    ReadingTheme.entries.firstOrNull { it.name == value } ?: ReadingTheme.FollowApp

enum class MusicRepeatMode {
  Off,
  All,
  One;

  fun next(): MusicRepeatMode = entries[(ordinal + 1) % entries.size]
}

data class MusicPlaybackSettings(
    val shuffleEnabled: Boolean = false,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.Off,
)

internal fun parseMusicRepeatMode(value: String?): MusicRepeatMode =
    MusicRepeatMode.entries.firstOrNull { it.name == value } ?: MusicRepeatMode.Off

enum class AppTab {
  Podcasts,
  Reader,
  Music,
  Memos,
  Books,
  Settings,
}

internal val defaultTabOrder =
    listOf(
        AppTab.Podcasts,
        AppTab.Reader,
        AppTab.Music,
        AppTab.Memos,
        AppTab.Books,
        AppTab.Settings,
    )

internal fun parseTabOrder(value: String?): List<AppTab> {
  val saved =
      value
          ?.split(',')
          ?.mapNotNull { name -> AppTab.entries.firstOrNull { it.name == name } }
          ?.distinct()
          .orEmpty()
  return defaultTabOrder.filterNot(saved::contains).fold(saved.toMutableList()) { order, tab ->
    val settingsIndex = order.indexOf(AppTab.Settings)
    order.add(if (settingsIndex >= 0) settingsIndex else order.size, tab)
    order
  }
}

internal fun moveTab(order: List<AppTab>, tab: AppTab, offset: Int): List<AppTab> {
  val normalized = parseTabOrder(order.joinToString(",", transform = AppTab::name))
  val from = normalized.indexOf(tab)
  val to = (from + offset).coerceIn(normalized.indices)
  if (from < 0 || from == to) return normalized
  return normalized.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun parseDisabledTabs(value: String?): Set<AppTab> =
    value
        ?.split(',')
        ?.mapNotNull { name -> AppTab.entries.firstOrNull { it.name == name } }
        ?.filterNot { it == AppTab.Settings }
        ?.toSet()
        .orEmpty()

data class PodcastPlayedChange(
    val states: List<EpisodeBulkState>,
    val markedPlayedCount: Int,
)

@Singleton
class PodcastRepository
@Inject
constructor(
    private val database: XpodDatabase,
    private val feedFetcher: FeedFetcher,
    private val parser: FeedParser,
    private val clock: Clock,
    private val downloads: DownloadRepository,
) {
  fun podcasts(): Flow<List<PodcastEntity>> = database.podcasts().observeAll()

  fun episodes(podcastId: String): Flow<List<EpisodeEntity>> =
      database.episodes().observeForPodcast(podcastId)

  fun allEpisodes(): Flow<List<EpisodeEntity>> = database.episodes().observeAll()

  suspend fun episode(id: String): EpisodeEntity? = database.episodes().find(id)

  suspend fun addOrRefresh(feedUrl: String): Result<Unit> =
      withContext(Dispatchers.IO) {
        runCatchingCancellable {
          val bytes = feedFetcher.fetch(feedUrl, FeedRequestType.Podcast)
          val parsed = parser.parse(bytes)
          // Refresh paths must not insert: a subscription the user removed while this
          // fetch was in flight would otherwise be resurrected by the upsert below.
          save(feedUrl, parsed, allowInsert = false)
        }
      }

  internal suspend fun save(feedUrl: String, parsed: ParsedFeed, allowInsert: Boolean = true) {
    val podcastId = FeedId.from(feedUrl)
    database.withTransaction {
      val isExistingSubscription = database.podcasts().find(podcastId) != null
      if (!isExistingSubscription && !allowInsert) return@withTransaction
      val existingEpisodes =
          database.episodes().allForPodcast(podcastId).associateBy { it.stableKey }
      database
          .podcasts()
          .upsert(
              PodcastEntity(
                  podcastId,
                  feedUrl,
                  parsed.title,
                  parsed.author,
                  parsed.description,
                  parsed.artworkUrl,
                  clock.millis(),
                  null,
              ),
          )
      database
          .episodes()
          .upsertAll(
              parsed.episodes.map { episode ->
                val existing = existingEpisodes[episode.stableKey]
                EpisodeEntity(
                    id = FeedId.from("$podcastId:${episode.stableKey}"),
                    podcastId = podcastId,
                    stableKey = episode.stableKey,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    publishedEpochMs = episode.publishedEpochMs,
                    durationMs = episode.durationMs ?: existing?.durationMs,
                    artworkUrl = episode.artworkUrl,
                    isPlayed = existing?.isPlayed ?: false,
                    isFavorite = existing?.isFavorite ?: false,
                    isNew = existing?.isNew ?: isExistingSubscription,
                    lastPlayedEpochMs = existing?.lastPlayedEpochMs ?: 0,
                )
              }
          )
    }
  }

  internal suspend fun removeEmptySubscription(feedUrl: String) {
    val podcastId = FeedId.from(feedUrl)
    if (database.episodes().allForPodcast(podcastId).isEmpty()) {
      database.podcasts().delete(podcastId)
    }
  }

  suspend fun remove(podcastId: String): Set<String> =
      withContext(Dispatchers.IO) {
        // Read the episode list inside the transaction so it matches what is deleted.
        val episodeIds = database.withTransaction {
          val ids = database.episodes().allForPodcast(podcastId).map(EpisodeEntity::id).toSet()
          database.playback().removeQueueEpisodesForPodcast(podcastId)
          database.playback().clearStateForPodcast(podcastId)
          database.podcasts().delete(podcastId)
          ids
        }
        // The unsubscribe is committed; download cleanup is best effort, only for episodes
        // that actually have a download entry, and one failure must not abort the rest.
        val downloadedIds = downloads.states.value.keys
        episodeIds
            .filter { it in downloadedIds }
            .forEach { episodeId ->
              runCatching { downloads.remove(episodeId) }
                  .onFailure { Log.w("XPOD", "Unable to remove download for $episodeId", it) }
            }
        episodeIds
      }

  suspend fun refreshAll(): FeedRefreshResult = coroutineScope {
    val feeds = withContext(Dispatchers.IO) { database.podcasts().all() }
    val concurrency = Semaphore(MAX_CONCURRENT_REFRESHES)
    val failures =
        feeds
            .map { podcast ->
              async { concurrency.withPermit { addOrRefresh(podcast.feedUrl).exceptionOrNull() } }
            }
            .awaitAll()
            .filterNotNull()
    FeedRefreshResult(
        refreshedCount = feeds.size - failures.size,
        failureCount = failures.size,
        shouldRetry = failures.any(::shouldRetryFeedRefresh),
    )
  }

  suspend fun toggleFavorite(episodeId: String) = database.episodes().toggleFavorite(episodeId)

  suspend fun setPlayed(episodeId: String, played: Boolean) =
      database.episodes().setPlayed(episodeId, played)

  suspend fun markAllPlayed(podcastId: String): PodcastPlayedChange = database.withTransaction {
    val states = database.episodes().bulkStatesForPodcast(podcastId)
    database.episodes().markAllPlayed(podcastId)
    PodcastPlayedChange(
        states = states,
        markedPlayedCount = states.count { !it.isPlayed },
    )
  }

  suspend fun restorePlayedChange(change: PodcastPlayedChange) = database.withTransaction {
    change.states
        .groupBy { it.isPlayed to it.isNew }
        .forEach { (status, states) ->
          states.chunked(SQLITE_BATCH_SIZE).forEach { batch ->
            database
                .episodes()
                .restoreBulkStates(
                    ids = batch.map(EpisodeBulkState::id),
                    played = status.first,
                    isNew = status.second,
                )
          }
        }
  }

  suspend fun markPodcastSeen(podcastId: String) = database.episodes().markPodcastSeen(podcastId)

  suspend fun recordPlayback(episodeId: String) =
      database.episodes().recordPlayback(episodeId, clock.millis())

  suspend fun exportOpml(
      output: OutputStream,
      articleFeeds: List<ArticleFeedEntity> = emptyList(),
  ) =
      withContext(Dispatchers.IO) {
        OpmlCodec.write(output, database.podcasts().all(), articleFeeds)
      }

  private companion object {
    const val MAX_CONCURRENT_REFRESHES = 4
    const val SQLITE_BATCH_SIZE = 500
  }
}

data class FeedRefreshResult(
    val refreshedCount: Int,
    val failureCount: Int,
    val shouldRetry: Boolean,
)

@Singleton
class PlaybackRepository
@Inject
constructor(private val database: XpodDatabase, private val clock: Clock) {
  suspend fun save(
      mediaId: String?,
      mediaType: PlaybackMediaType,
      positionMs: Long,
      speed: Float,
      capturedAtEpochMs: Long = clock.millis(),
  ) = database.withTransaction {
    val previous = database.playback().state(mediaType.name)
    if (!shouldPersistPlaybackSnapshot(previous?.updatedAtEpochMs, capturedAtEpochMs)) {
      return@withTransaction
    }
    val availableMediaId = mediaId?.takeIf { id ->
      when (mediaType) {
        PlaybackMediaType.Podcast -> database.episodes().find(id) != null
        PlaybackMediaType.Music -> database.localTracks().find(id) != null
      }
    }
    val updatedAtEpochMs =
        nextPlaybackTimestamp(
            nowEpochMs = capturedAtEpochMs,
            previousEpochMs = database.playback().latestUpdatedAt(),
        )
    database
        .playback()
        .save(
            PlaybackStateEntity(
                key = mediaType.name,
                mediaId = availableMediaId,
                mediaType = mediaType.name,
                positionMs = positionMs.takeIf { availableMediaId != null } ?: 0L,
                speed = speed.takeIf { mediaType == PlaybackMediaType.Podcast } ?: 1f,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        )
  }

  suspend fun state(): PlaybackStateEntity? = database.playback().current()

  suspend fun state(mediaType: PlaybackMediaType): PlaybackStateEntity? =
      database.playback().state(mediaType.name)

  suspend fun markEpisodePlayed(episodeId: String) =
      database.episodes().markEpisodePlayed(episodeId, clock.millis())

  suspend fun replaceQueue(mediaType: PlaybackMediaType, mediaIds: List<String>) =
      database.withTransaction {
        database.playback().clearQueue(mediaType.name)
        database
            .playback()
            .insertQueue(
                mediaIds.distinct().mapIndexed { index, id ->
                  QueueItemEntity(id, mediaType.name, index)
                }
            )
      }

  suspend fun queue(mediaType: PlaybackMediaType): List<PlaybackReference> =
      database.playback().queue(mediaType.name).map {
        PlaybackReference(it.mediaId, PlaybackMediaType.fromStored(it.mediaType))
      }
}

internal fun nextPlaybackTimestamp(nowEpochMs: Long, previousEpochMs: Long?): Long =
    when {
      previousEpochMs == null -> nowEpochMs
      previousEpochMs == Long.MAX_VALUE -> Long.MAX_VALUE
      else -> maxOf(nowEpochMs, previousEpochMs + 1L)
    }

internal fun shouldPersistPlaybackSnapshot(
    previousUpdatedAtEpochMs: Long?,
    capturedAtEpochMs: Long,
): Boolean = previousUpdatedAtEpochMs == null || capturedAtEpochMs > previousUpdatedAtEpochMs

@Singleton
class SettingsRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {
  private val dynamicColor = booleanPreferencesKey("dynamic_color")
  private val speed = floatPreferencesKey("default_speed")
  private val themeMode = stringPreferencesKey("theme_mode")
  private val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
  private val tabOrderKey = stringPreferencesKey("tab_order")
  private val disabledTabsKey = stringPreferencesKey("disabled_tabs")
  private val localMusicTreeUriKey = stringPreferencesKey("local_music_tree_uri")
  private val localBooksTreeUriKey = stringPreferencesKey("local_books_tree_uri")
  private val musicShuffleEnabledKey = booleanPreferencesKey("music_shuffle_enabled")
  private val musicRepeatModeKey = stringPreferencesKey("music_repeat_mode")
  val useDynamicColor: Flow<Boolean> = context.settingsStore.data.map { it[dynamicColor] ?: true }
  val defaultSpeed: Flow<Float> = context.settingsStore.data.map { it[speed] ?: 1f }
  val appTheme: Flow<ThemeMode> =
      context.settingsStore.data.map { preferences ->
        runCatching { ThemeMode.valueOf(preferences[themeMode] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
      }
  val useWifiOnlyDownloads: Flow<Boolean> =
      context.settingsStore.data.map { it[wifiOnlyDownloads] ?: true }
  val tabOrder: Flow<List<AppTab>> =
      context.settingsStore.data.map { preferences -> parseTabOrder(preferences[tabOrderKey]) }
  val enabledTabs: Flow<Set<AppTab>> =
      context.settingsStore.data.map { preferences ->
        defaultTabOrder.toSet() - parseDisabledTabs(preferences[disabledTabsKey])
      }
  val localMusicTreeUri: Flow<String?> =
      context.settingsStore.data.map { preferences -> preferences[localMusicTreeUriKey] }
  val localBooksTreeUri: Flow<String?> =
      context.settingsStore.data.map { preferences -> preferences[localBooksTreeUriKey] }
  val musicPlaybackSettings: Flow<MusicPlaybackSettings> =
      context.settingsStore.data.map { preferences ->
        MusicPlaybackSettings(
            shuffleEnabled = preferences[musicShuffleEnabledKey] ?: false,
            repeatMode = parseMusicRepeatMode(preferences[musicRepeatModeKey]),
        )
      }

  suspend fun setDynamicColor(enabled: Boolean) {
    context.settingsStore.edit { it[dynamicColor] = enabled }
  }

  suspend fun setDefaultSpeed(value: Float) {
    context.settingsStore.edit { it[speed] = value }
  }

  suspend fun setAppTheme(value: ThemeMode) {
    context.settingsStore.edit { it[themeMode] = value.name }
  }

  suspend fun setWifiOnlyDownloads(enabled: Boolean) {
    context.settingsStore.edit { it[wifiOnlyDownloads] = enabled }
  }

  suspend fun setLocalMusicTreeUri(value: String?) {
    context.settingsStore.edit { preferences ->
      if (value == null) preferences.remove(localMusicTreeUriKey)
      else preferences[localMusicTreeUriKey] = value
    }
  }

  suspend fun localMusicTreeUriValue(): String? = localMusicTreeUri.first()

  suspend fun localBooksTreeUriValue(): String? = localBooksTreeUri.first()

  suspend fun setLocalBooksTreeUri(value: String?) {
    context.settingsStore.edit { preferences ->
      if (value == null) preferences.remove(localBooksTreeUriKey)
      else preferences[localBooksTreeUriKey] = value
    }
  }

  suspend fun musicPlaybackSettingsValue(): MusicPlaybackSettings = musicPlaybackSettings.first()

  suspend fun toggleMusicShuffle(): Boolean {
    var updated = false
    context.settingsStore.edit { preferences ->
      updated = !(preferences[musicShuffleEnabledKey] ?: false)
      preferences[musicShuffleEnabledKey] = updated
    }
    return updated
  }

  suspend fun cycleMusicRepeatMode(): MusicRepeatMode {
    var updated = MusicRepeatMode.Off
    context.settingsStore.edit { preferences ->
      updated = parseMusicRepeatMode(preferences[musicRepeatModeKey]).next()
      preferences[musicRepeatModeKey] = updated.name
    }
    return updated
  }

  suspend fun moveTab(tab: AppTab, offset: Int) {
    context.settingsStore.edit { preferences ->
      preferences[tabOrderKey] =
          moveTab(parseTabOrder(preferences[tabOrderKey]), tab, offset)
              .joinToString(",", transform = AppTab::name)
    }
  }

  suspend fun setTabEnabled(tab: AppTab, enabled: Boolean) {
    if (tab == AppTab.Settings) return
    context.settingsStore.edit { preferences ->
      val disabled = parseDisabledTabs(preferences[disabledTabsKey]).toMutableSet()
      if (enabled) disabled.remove(tab) else disabled.add(tab)
      preferences[disabledTabsKey] =
          defaultTabOrder.filter(disabled::contains).joinToString(",", transform = AppTab::name)
    }
  }
}

@Singleton
class ReadingPreferencesRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {
  private val fontSizeSpKey = floatPreferencesKey("reading_font_size_sp")
  private val lineHeightMultiplierKey = floatPreferencesKey("reading_line_height_multiplier")
  private val themeKey = stringPreferencesKey("reading_theme")

  val preferences: Flow<ReadingPreferences> =
      context.settingsStore.data.map { values ->
        ReadingPreferences(
            fontSizeSp = (values[fontSizeSpKey] ?: 18f).coerceIn(12f, 32f),
            lineHeightMultiplier = (values[lineHeightMultiplierKey] ?: 1.55f).coerceIn(1.1f, 2.4f),
            theme = parseReadingTheme(values[themeKey]),
        )
      }

  suspend fun setFontSizeSp(value: Float) {
    context.settingsStore.edit { it[fontSizeSpKey] = value.coerceIn(12f, 32f) }
  }

  suspend fun setLineHeightMultiplier(value: Float) {
    context.settingsStore.edit {
      it[lineHeightMultiplierKey] = value.coerceIn(1.1f, 2.4f)
    }
  }

  suspend fun setTheme(value: ReadingTheme) {
    context.settingsStore.edit { it[themeKey] = value.name }
  }
}

@Singleton
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class DownloadRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
  private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
  val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()
  private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  // All state refreshes run on a single worker so a slower, stale snapshot can never
  // overwrite a newer one (which could leave a finished download shown as in-progress).
  private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
  // Only touched from refreshScope, which runs one coroutine at a time.
  private var progressPollJob: Job? = null

  // Building the DownloadManager scans the download directory and opens SQLite; keep that
  // off the main thread that constructs this repository.
  private val manager: Deferred<DownloadManager> = syncScope.async {
    // Strip the shared OkHttp HTTP cache for episode downloads: a full episode GET is
    // cacheable and would evict the small feed cache while SimpleCache already stores
    // the audio. The connection pool and dispatcher stay shared.
    DownloadComponent.configure(
        OkHttpDataSource.Factory(okHttpClient.newBuilder().cache(null).build())
    )
    DownloadComponent.manager(context).also { manager ->
      manager.addListener(
          object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = refreshStates(downloadManager)

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) = refreshStates(downloadManager)

            // Losing or regaining an allowed network must re-render queued items as
            // waiting-for-network (and back) instead of leaving a stale "Queued".
            override fun onRequirementsStateChanged(
                downloadManager: DownloadManager,
                requirements: Requirements,
                notMetRequirements: Int,
            ) = refreshStates(downloadManager)

            override fun onDownloadsPausedChanged(
                downloadManager: DownloadManager,
                downloadsPaused: Boolean,
            ) = refreshStates(downloadManager)
          }
      )
      refreshStates(manager)
    }
  }

  suspend fun enqueue(episode: EpisodeEntity): Result<Unit> = runCatchingCancellable {
    enqueueOrThrow(episode)
  }

  fun remove(episodeId: String) {
    DownloadService.sendRemoveDownload(context, XpodDownloadService::class.java, episodeId, false)
  }

  suspend fun retry(episode: EpisodeEntity): Result<Unit> = runCatchingCancellable {
    remove(episode.id)
    enqueueOrThrow(episode)
  }

  fun setWifiOnly(enabled: Boolean) {
    DownloadPreferences.setWifiOnly(context, enabled)
    syncScope.launch {
      val manager = manager.await()
      manager.requirements =
          Requirements(if (enabled) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
      refreshStates(manager)
    }
  }

  private fun refreshStates(manager: DownloadManager) {
    refreshScope.launch { refreshStatesNow(manager) }
  }

  private fun refreshStatesNow(manager: DownloadManager) {
    val waitingForNetwork = manager.notMetRequirements != 0
    val downloads =
        runCatching {
              manager.downloadIndex.getDownloads().use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.download) }
              }
            }
            .getOrElse {
              return
            }
    // The download index is only written on state transitions; the manager's current
    // downloads carry live progress, so prefer those snapshots where available.
    val liveDownloads = manager.currentDownloads.associateBy { it.request.id }
    _states.value =
        downloads
            .mapNotNull { indexed ->
              val download = liveDownloads[indexed.request.id] ?: indexed
              when (download.state) {
                Download.STATE_QUEUED,
                Download.STATE_STOPPED ->
                    DownloadState(
                        progress = download.percentDownloaded.takeIf { it >= 0f }?.div(100f),
                        bytesDownloaded = download.bytesDownloaded,
                        phase =
                            if (waitingForNetwork) DownloadPhase.WaitingForNetwork
                            else DownloadPhase.Queued,
                    )
                Download.STATE_DOWNLOADING,
                Download.STATE_RESTARTING ->
                    DownloadState(
                        progress = download.percentDownloaded.takeIf { it >= 0f }?.div(100f),
                        bytesDownloaded = download.bytesDownloaded,
                        phase = DownloadPhase.Downloading,
                    )
                Download.STATE_COMPLETED ->
                    DownloadState(
                        progress = 1f,
                        bytesDownloaded = download.bytesDownloaded,
                        isCompleted = true,
                    )
                Download.STATE_FAILED ->
                    DownloadState(
                        progress = download.percentDownloaded.takeIf { it >= 0f }?.div(100f),
                        bytesDownloaded = download.bytesDownloaded,
                        phase = DownloadPhase.Failed,
                    )
                else -> null
              }?.let { download.request.id to it }
            }
            .toMap()
    updateProgressPolling(manager)
  }

  // DownloadManager.Listener has no progress callback, so percentages would freeze between
  // state transitions; poll while a download is actively transferring and stop when none is.
  private fun updateProgressPolling(manager: DownloadManager) {
    val transferring = manager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }
    if (!transferring) {
      progressPollJob?.cancel()
      progressPollJob = null
    } else if (progressPollJob?.isActive != true) {
      progressPollJob = refreshScope.launch {
        while (true) {
          delay(1_000)
          refreshStatesNow(manager)
        }
      }
    }
  }

  private suspend fun enqueueOrThrow(episode: EpisodeEntity) {
    withContext(Dispatchers.IO) {
      val available = StatFs(DownloadComponent.downloadDirectory(context).path).availableBytes
      require(available >= 500L * 1024 * 1024) { "At least 500 MiB of free storage is required" }
      val request = DownloadRequest.Builder(episode.id, episode.audioUrl.toUri()).build()
      // Enqueues are user-initiated while the app is in the foreground, so a foreground
      // service start is both allowed and required (background starts throw on Android 12+).
      DownloadService.sendAddDownload(context, XpodDownloadService::class.java, request, true)
    }
  }
}

enum class DownloadPhase {
  WaitingForNetwork,
  Queued,
  Downloading,
  Failed,
}

data class DownloadState(
    val progress: Float?,
    val bytesDownloaded: Long = 0L,
    val isCompleted: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.Downloading,
)
