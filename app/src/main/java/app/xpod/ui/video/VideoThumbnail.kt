package app.xpod.ui.video

import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import app.xpod.data.LocalVideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VideoThumbnail(
    video: LocalVideoEntity,
    modifier: Modifier,
    contentDescription: String?,
) {
  val context = LocalContext.current
  val cacheKey = videoThumbnailCacheKey(video)
  val thumbnail by
      produceState<Bitmap?>(initialValue = videoThumbnailCache.get(cacheKey), cacheKey) {
        if (value != null) return@produceState
        value =
            runCatching {
                  withContext(Dispatchers.IO) {
                    context.contentResolver.loadThumbnail(
                        android.net.Uri.parse(video.documentUri),
                        VIDEO_THUMBNAIL_SIZE,
                        null,
                    )
                  }
                }
                .getOrNull()
                ?.also { videoThumbnailCache.put(cacheKey, it) }
      }
  Box(
      modifier
          .clip(MaterialTheme.shapes.small)
          .background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
  ) {
    if (thumbnail != null) {
      androidx.compose.foundation.Image(
          bitmap = thumbnail!!.asImageBitmap(),
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
      )
    } else {
      Icon(
          Icons.Filled.VideoLibrary,
          contentDescription = contentDescription,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

private fun videoThumbnailCacheKey(video: LocalVideoEntity): String =
    "${video.documentUri}|${video.modifiedEpochMs}|${video.fileSizeBytes}"

private val videoThumbnailCache =
    object : LruCache<String, Bitmap>(4 * 1024) {
      override fun sizeOf(key: String, value: Bitmap): Int =
          (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

private val VIDEO_THUMBNAIL_SIZE = Size(256, 144)
