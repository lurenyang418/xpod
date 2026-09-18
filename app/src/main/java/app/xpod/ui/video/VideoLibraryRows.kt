package app.xpod.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.LocalVideoEntity

@Composable
internal fun VideoFolderRow(
    folder: VideoFolder,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
  var menuExpanded by remember(folder.path) { mutableStateOf(false) }
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen),
  ) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Filled.Folder,
          contentDescription = stringResource(R.string.open_video_folder),
          modifier = Modifier.size(44.dp),
          tint = MaterialTheme.colorScheme.primary,
      )
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.local_video_count, folder.videoCount, folder.videoCount),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Box {
        IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.video_folder_actions))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
          VideoMenuIconButton(
              icon = Icons.Filled.Delete,
              contentDescription = stringResource(R.string.delete_video_folder),
              onClick = {
                menuExpanded = false
                onDelete()
              },
          )
        }
      }
    }
  }
}

@Composable
internal fun VideoRow(
    video: LocalVideoEntity,
    enabled: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
) {
  var menuExpanded by remember(video.id) { mutableStateOf(false) }
  Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(76.dp, 48.dp)) {
        VideoThumbnail(
            video = video,
            modifier = Modifier.fillMaxSize(),
            contentDescription = video.title,
        )
        if (isVideoUnplayed(video)) {
          Surface(
              color = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
          ) {
            Text(
                stringResource(R.string.video_unplayed),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
          }
        }
      }
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            videoDetails(video, stringResource(R.string.video_file_details)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (video.lastPositionMs > 0L && video.durationMs > 0L) {
          Text(
              stringResource(
                  R.string.video_progress,
                  (video.lastPositionMs * 100 / video.durationMs).toInt().coerceIn(0, 99),
              ),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Box {
        IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.video_actions))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VideoMenuIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.rename_video),
                onClick = {
                  menuExpanded = false
                  onRename()
                },
            )
            VideoMenuIconButton(
                icon = Icons.Filled.Info,
                contentDescription = stringResource(R.string.video_properties),
                onClick = {
                  menuExpanded = false
                  onProperties()
                },
            )
            VideoMenuIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete_video),
                onClick = {
                  menuExpanded = false
                  onDelete()
                },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VideoMenuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
  Box(
      Modifier.size(40.dp).clickable(onClick = onClick).semantics {
        this.contentDescription = contentDescription
        role = Role.Button
      },
      contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
  }
}
