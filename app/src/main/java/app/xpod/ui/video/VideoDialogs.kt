package app.xpod.ui.video

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import app.xpod.R
import app.xpod.data.LocalVideoEntity
import app.xpod.ui.player.mediaTimeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

internal sealed interface VideoDeleteTarget {
  data class Video(val video: LocalVideoEntity) : VideoDeleteTarget

  data class Folder(val folder: VideoFolder) : VideoDeleteTarget
}

@Composable
internal fun VideoRenameDialog(
    video: LocalVideoEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var title by rememberSaveable(video.id) { mutableStateOf(video.title) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.rename_video_title)) },
      text = {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.rename_video)) },
            singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
            onClick = { onConfirm(title) },
            enabled = title.trim().isNotBlank(),
        ) {
          Text(stringResource(R.string.save))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
  )
}

@Composable
internal fun VideoPropertiesDialog(video: LocalVideoEntity, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.video_properties_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          VideoPropertyRow(R.string.video_path, video.relativePath.ifBlank { video.title })
          VideoPropertyRow(R.string.video_size, videoFileSizeLabel(video.fileSizeBytes))
          VideoPropertyRow(
              R.string.video_resolution,
              if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else "—",
          )
          VideoPropertyRow(
              R.string.video_duration,
              video.durationMs.takeIf { it > 0L }?.let(::mediaTimeLabel) ?: "—",
          )
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
  )
}

@Composable
private fun VideoPropertyRow(labelResId: Int, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(labelResId), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
internal fun VideoDeleteDialog(
    target: VideoDeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  val title =
      when (target) {
        is VideoDeleteTarget.Video -> stringResource(R.string.delete_video_title)
        is VideoDeleteTarget.Folder -> stringResource(R.string.delete_video_folder_title)
      }
  val message =
      when (target) {
        is VideoDeleteTarget.Video -> stringResource(R.string.delete_video_message, target.video.title)
        is VideoDeleteTarget.Folder ->
            stringResource(R.string.delete_video_folder_message, target.folder.videoCount)
      }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
  )
}

private fun videoFileSizeLabel(bytes: Long): String {
  if (bytes <= 0L) return "—"
  return when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
  }
}
