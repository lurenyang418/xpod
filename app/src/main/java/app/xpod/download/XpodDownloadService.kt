package app.xpod.download

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.xpod.R
import java.io.File

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object DownloadComponent {
  private var cache: SimpleCache? = null
  private var manager: DownloadManager? = null

  @Synchronized
  fun cache(context: Context): SimpleCache =
      cache
          ?: SimpleCache(
                  downloadDirectory(context),
                  NoOpCacheEvictor(),
                  StandaloneDatabaseProvider(context),
              )
              .also { cache = it }

  @Synchronized
  fun manager(context: Context): DownloadManager =
      manager
          ?: DownloadManager(
                  context,
                  StandaloneDatabaseProvider(context),
                  cache(context),
                  DefaultHttpDataSource.Factory(),
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
class XpodDownloadService :
    DownloadService(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.app_name,
        0,
    ) {
  override fun getDownloadManager(): DownloadManager = DownloadComponent.manager(this)

  override fun getScheduler() = null

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
