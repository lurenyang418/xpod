package app.xpod.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import app.xpod.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object DownloadComponent {
  private var cache: SimpleCache? = null
  private var manager: DownloadManager? = null
  private var upstream: HttpDataSource.Factory? = null
  private var databaseProvider: StandaloneDatabaseProvider? = null

  @Synchronized
  fun configure(upstreamFactory: HttpDataSource.Factory) {
    upstream = upstreamFactory
  }

  // Media3 expects a single database provider per app; two SQLiteOpenHelpers over the
  // same exoplayer_internal.db invite lock contention and index corruption.
  @Synchronized
  private fun databaseProvider(context: Context): StandaloneDatabaseProvider =
      databaseProvider
          ?: StandaloneDatabaseProvider(context.applicationContext).also { databaseProvider = it }

  @Synchronized
  fun cache(context: Context): SimpleCache =
      cache
          ?: SimpleCache(
                  downloadDirectory(context),
                  NoOpCacheEvictor(),
                  databaseProvider(context),
              )
              .also { cache = it }

  @Synchronized
  fun manager(context: Context): DownloadManager =
      manager
          ?: DownloadManager(
                  context,
                  databaseProvider(context),
                  cache(context),
                  upstream ?: DefaultHttpDataSource.Factory(),
                  Runnable::run,
              )
              .apply {
                requirements =
                    Requirements(
                        if (DownloadPreferences.useWifiOnly(context)) Requirements.NETWORK_UNMETERED
                        else Requirements.NETWORK
                    )
                maxParallelDownloads = 3
              }
              .also { manager = it }

  fun downloadDirectory(context: Context): File {
    val external = context.getExternalFilesDir("downloads")
    if (external != null && (external.exists() || external.mkdirs())) return external
    return context.getDir("downloads", Context.MODE_PRIVATE)
  }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class XpodDownloadService :
    DownloadService(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.app_name,
        0,
    ) {
  @Inject lateinit var okHttpClient: OkHttpClient

  override fun getDownloadManager(): DownloadManager {
    // Strip the shared OkHttp HTTP cache for episode downloads: a full episode GET is
    // cacheable and would evict the small feed cache while SimpleCache already stores the
    // audio. The connection pool and dispatcher stay shared.
    DownloadComponent.configure(
        OkHttpDataSource.Factory(okHttpClient.newBuilder().cache(null).build())
    )
    return DownloadComponent.manager(this)
  }

  // Restarts the service when download requirements (for example unmetered wifi) become
  // met again or after process death; the manifest RESTART intent filter relies on it.
  override fun getScheduler() = WorkManagerScheduler(this, "xpod-downloads")

  override fun getForegroundNotification(
      downloads: MutableList<Download>,
      notMetRequirements: Int,
  ): Notification =
      androidx.media3.exoplayer.offline
          .DownloadNotificationHelper(this, CHANNEL_ID)
          .buildProgressNotification(
              this,
              R.drawable.ic_stat_download,
              null,
              null,
              downloads,
              notMetRequirements,
          )

  private companion object {
    const val CHANNEL_ID = "downloads"
    const val FOREGROUND_NOTIFICATION_ID = 202
  }
}
