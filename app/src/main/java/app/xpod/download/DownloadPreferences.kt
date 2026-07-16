package app.xpod.download

import android.content.Context
import androidx.core.content.edit

object DownloadPreferences {
  private const val FILE_NAME = "download_preferences"
  private const val WIFI_ONLY_KEY = "wifi_only"

  fun useWifiOnly(context: Context): Boolean =
      context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean(WIFI_ONLY_KEY, true)

  fun setWifiOnly(context: Context, enabled: Boolean) {
    context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit {
      putBoolean(WIFI_ONLY_KEY, enabled)
    }
  }
}
