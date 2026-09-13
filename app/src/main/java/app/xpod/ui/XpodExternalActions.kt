package app.xpod.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.net.toUri
import app.xpod.R
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoVisibility

internal fun copyMemoMarkdown(context: Context, memo: CloudMemo) {
  val clip = ClipData.newPlainText(context.getString(R.string.cloud_memo_markdown), memo.content)
  if (memo.visibility == CloudMemoVisibility.Private) {
    clip.description.extras =
        PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
  }
  context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

internal fun shareText(context: Context, text: String, chooserTitle: String): Boolean =
    try {
      val intent =
          Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
          }
      context.startActivity(Intent.createChooser(intent, chooserTitle))
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: SecurityException) {
      false
    }

internal fun openExternalUrl(context: Context, url: String): Boolean =
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: SecurityException) {
      false
    }

internal const val XPOD_RELEASES_URL = "https://github.com/lurenyang418/xpod/releases"
