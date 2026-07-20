package app.xpod.data

import android.content.Context
import android.os.StatFs
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.util.UnstableApi
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
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val Context.settingsStore by preferencesDataStore("settings")

enum class ThemeMode {
  System,
  Light,
  Dark,
}

enum class AppTab {
  Podcasts,
  Reader,
  Library,
  Memos,
  Settings,
}

internal val defaultTabOrder =
    listOf(AppTab.Podcasts, AppTab.Reader, AppTab.Library, AppTab.Memos, AppTab.Settings)

internal fun parseTabOrder(value: String?): List<AppTab> {
  val saved =
      value
          ?.split(',')
          ?.mapNotNull { name -> AppTab.entries.firstOrNull { it.name == name } }
          ?.distinct()
          .orEmpty()
  return saved + defaultTabOrder.filterNot(saved::contains)
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
          val parsed = parser.parse(ByteArrayInputStream(bytes))
          save(feedUrl, parsed)
        }
      }

  internal suspend fun save(feedUrl: String, parsed: ParsedFeed) {
    val podcastId = FeedId.from(feedUrl)
    database.withTransaction {
      val isExistingSubscription = database.podcasts().find(podcastId) != null
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

  suspend fun remove(podcastId: String) =
      withContext(Dispatchers.IO) {
        database.episodes().allForPodcast(podcastId).forEach { downloads.remove(it.id) }
        database.podcasts().delete(podcastId)
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
  ) = OpmlCodec.write(output, database.podcasts().all(), articleFeeds)

  private companion object {
    const val MAX_CONCURRENT_REFRESHES = 4
    const val SQLITE_BATCH_SIZE = 500
  }
}

data class FeedRefreshResult(val shouldRetry: Boolean)

@Singleton
class PlaybackRepository
@Inject
constructor(private val database: XpodDatabase, private val clock: Clock) {
  suspend fun save(episodeId: String?, positionMs: Long, speed: Float) {
    database
        .playback()
        .save(
            PlaybackStateEntity(
                episodeId = episodeId,
                positionMs = positionMs,
                speed = speed,
                updatedAtEpochMs = clock.millis(),
            )
        )
  }

  suspend fun state(): PlaybackStateEntity? = database.playback().current()

  suspend fun markEpisodePlayed(episodeId: String) =
      database.episodes().markEpisodePlayed(episodeId, clock.millis())

  suspend fun replaceQueue(episodeIds: List<String>) = database.withTransaction {
    database.playback().clearQueue()
    database
        .playback()
        .insertQueue(episodeIds.distinct().mapIndexed { index, id -> QueueItemEntity(id, index) })
  }

  suspend fun queue(): List<String> = database.playback().queue().map { it.episodeId }
}

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
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class DownloadRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {
  private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
  val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()
  private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val manager: DownloadManager =
      DownloadComponent.manager(context).also { manager ->
        manager.addListener(
            object : DownloadManager.Listener {
              override fun onDownloadChanged(
                  downloadManager: DownloadManager,
                  download: Download,
                  finalException: Exception?,
              ) = refreshStates(downloadManager)

              override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) =
                  refreshStates(downloadManager)
            }
        )
        refreshStates(manager)
      }

  fun enqueue(episode: EpisodeEntity): Result<Unit> = runCatching { enqueueOrThrow(episode) }

  fun remove(episodeId: String) {
    DownloadService.sendRemoveDownload(context, XpodDownloadService::class.java, episodeId, false)
  }

  fun retry(episode: EpisodeEntity): Result<Unit> = runCatching {
    remove(episode.id)
    enqueueOrThrow(episode)
  }

  fun setWifiOnly(enabled: Boolean) {
    DownloadPreferences.setWifiOnly(context, enabled)
    manager.requirements =
        Requirements(if (enabled) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
    refreshStates(manager)
  }

  private fun refreshStates(manager: DownloadManager) {
    val waitingForNetwork = manager.notMetRequirements != 0
    syncScope.launch {
      val downloads =
          runCatchingCancellable {
                manager.downloadIndex.getDownloads().use { cursor ->
                  buildList { while (cursor.moveToNext()) add(cursor.download) }
                }
              }
              .getOrElse {
                return@launch
              }
      _states.value =
          downloads
              .mapNotNull { download ->
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
    }
  }

  private fun enqueueOrThrow(episode: EpisodeEntity) {
    val available = StatFs(DownloadComponent.downloadDirectory(context).path).availableBytes
    require(available >= 500L * 1024 * 1024) { "At least 500 MiB of free storage is required" }
    val request = DownloadRequest.Builder(episode.id, episode.audioUrl.toUri()).build()
    DownloadService.sendAddDownload(context, XpodDownloadService::class.java, request, false)
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
