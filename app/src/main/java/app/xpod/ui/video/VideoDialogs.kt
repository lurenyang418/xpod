package app.xpod.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.LocalVideoEntity
import app.xpod.ui.player.mediaTimeLabel

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
        is VideoDeleteTarget.Video ->
            stringResource(R.string.delete_video_message, target.video.title)
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
