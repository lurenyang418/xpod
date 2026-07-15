package app.xpod.refresh

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.xpod.data.PodcastRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class FeedRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            FeedRefreshWorkerEntryPoint::class.java,
        )
        return if (entryPoint.podcasts().refreshAll().shouldRetry) Result.retry() else Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedRefreshWorkerEntryPoint {
    fun podcasts(): PodcastRepository
}

object FeedRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "feed_refresh"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
