package app.xpod

import android.app.Application
import app.xpod.refresh.FeedRefreshScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class XpodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FeedRefreshScheduler.schedule(this)
    }
}
