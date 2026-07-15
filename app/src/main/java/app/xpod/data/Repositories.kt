package app.xpod.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.util.concurrent.TimeUnit
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
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.settingsStore by preferencesDataStore("settings")

enum class ThemeMode {
  System,
  Light,
  Dark
}

@Singleton
class PodcastRepository
@Inject
constructor(
    private val database: XpodDatabase,
    private val client: OkHttpClient,
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
          require(feedUrl.startsWith("https://")) { "Only HTTPS feed URLs are supported" }
          val request =
              Request.Builder()
                  .url(feedUrl)
                  .header("User-Agent", "XPOD/1.0 (Android podcast client)")
                  .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                  .build()
          client
              .newCall(request)
              .apply { timeout().timeout(FEED_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
              .execute()
              .use { response ->
                if (!response.isSuccessful) throw FeedHttpException(response.code)
                val body = requireNotNull(response.body)
                require(body.contentLength() <= MAX_FEED_BYTES || body.contentLength() == -1L) {
                  "Feed exceeds the 10 MiB limit"
                }
                val bytes = body.byteStream().use { readBytesAtMost(it, MAX_FEED_BYTES) }
                val parsed = parser.parse(ByteArrayInputStream(bytes))
                val podcastId = parser.id(feedUrl)
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
                              null),
                      )
                  database
                      .episodes()
                      .upsertAll(
                          parsed.episodes.map { episode ->
                            val existing = existingEpisodes[episode.stableKey]
                            EpisodeEntity(
                                id = parser.id("$podcastId:${episode.stableKey}"),
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
                          })
                }
              }
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
        shouldRetry =
            failures.any { error ->
              when (error) {
                is FeedHttpException -> error.statusCode >= 500
                is IOException -> true
                else -> false
              }
            },
    )
  }

  suspend fun toggleFavorite(episodeId: String) = database.episodes().toggleFavorite(episodeId)

  suspend fun setPlayed(episodeId: String, played: Boolean) =
      database.episodes().setPlayed(episodeId, played)

  suspend fun markPodcastSeen(podcastId: String) = database.episodes().markPodcastSeen(podcastId)

  suspend fun recordPlayback(episodeId: String) =
      database.episodes().recordPlayback(episodeId, clock.millis())

  suspend fun importOpml(input: InputStream): Result<Int> =
      withContext(Dispatchers.IO) {
        runCatchingCancellable {
          val urls = OpmlCodec.read(input)
          var imported = 0
          urls.forEach { url -> if (addOrRefresh(url).isSuccess) imported++ }
          imported
        }
      }

  suspend fun exportOpml(output: OutputStream) = OpmlCodec.write(output, database.podcasts().all())

  private companion object {
    const val MAX_FEED_BYTES = 10 * 1024 * 1024
    const val MAX_CONCURRENT_REFRESHES = 4
    const val FEED_REQUEST_TIMEOUT_SECONDS = 25L
  }
}

class FeedHttpException(val statusCode: Int) : IOException("Feed request failed: HTTP $statusCode")

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
                updatedAtEpochMs = clock.millis()))
  }

  suspend fun state(): PlaybackStateEntity? = database.playback().current()

  suspend fun markEpisodePlayed(episodeId: String) =
      database.episodes().markEpisodePlayed(episodeId, clock.millis())

  suspend fun replaceQueue(episodeIds: List<String>) =
      database.withTransaction {
        database.playback().clearQueue()
        database
            .playback()
            .insertQueue(
                episodeIds.distinct().mapIndexed { index, id -> QueueItemEntity(id, index) })
      }

  suspend fun queue(): List<String> = database.playback().queue().map { it.episodeId }
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
  private val dynamicColor = booleanPreferencesKey("dynamic_color")
  private val speed = floatPreferencesKey("default_speed")
  private val themeMode = stringPreferencesKey("theme_mode")
  private val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
  val useDynamicColor: Flow<Boolean> = context.settingsStore.data.map { it[dynamicColor] ?: true }
  val defaultSpeed: Flow<Float> = context.settingsStore.data.map { it[speed] ?: 1f }
  val appTheme: Flow<ThemeMode> =
      context.settingsStore.data.map { preferences ->
        runCatching { ThemeMode.valueOf(preferences[themeMode] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
      }
  val useWifiOnlyDownloads: Flow<Boolean> =
      context.settingsStore.data.map { it[wifiOnlyDownloads] ?: true }

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
}

@Singleton
@UnstableApi
class DownloadRepository @Inject constructor(@ApplicationContext private val context: Context) {
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
                  finalException: Exception?
              ) = refreshStates(downloadManager)

              override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) =
                  refreshStates(downloadManager)
            })
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
                          isCompleted = true)
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
    val request = DownloadRequest.Builder(episode.id, Uri.parse(episode.audioUrl)).build()
    DownloadService.sendAddDownload(context, XpodDownloadService::class.java, request, false)
  }
}

enum class DownloadPhase {
  WaitingForNetwork,
  Queued,
  Downloading,
  Failed
}

data class DownloadState(
    val progress: Float?,
    val bytesDownloaded: Long = 0L,
    val isCompleted: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.Downloading,
)

internal fun readBytesAtMost(input: InputStream, maxBytes: Int): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var total = 0
  while (true) {
    val count = input.read(buffer)
    if (count < 0) break
    total += count
    require(total <= maxBytes) { "Input exceeds the configured size limit" }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}
