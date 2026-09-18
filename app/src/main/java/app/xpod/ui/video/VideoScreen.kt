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

@Composable
internal fun VideoScreen(
    state: VideoUiState,
    chooseFolder: () -> Unit,
    startAutomaticScan: () -> Unit,
    refresh: () -> Unit,
    cancelScan: () -> Unit,
    setQuery: (String) -> Unit,
    openFolder: (String) -> Unit,
    play: (LocalVideoEntity) -> Unit,
    renameVideo: (LocalVideoEntity, String) -> Unit,
    deleteVideo: (LocalVideoEntity) -> Unit,
    deleteFolder: (String) -> Unit,
) {
  if (state.selectedTreeUri == null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(Icons.Filled.VideoLibrary, null, Modifier.size(56.dp))
      Text(
          stringResource(R.string.no_local_video_folder),
          Modifier.padding(top = 16.dp),
          style = MaterialTheme.typography.titleLarge,
      )
      Text(
          stringResource(R.string.local_video_folder_summary),
          Modifier.padding(top = 8.dp, bottom = 20.dp),
          style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = startAutomaticScan, enabled = !state.isScanning) {
        Text(
            stringResource(
                if (state.hasVideoPermission) R.string.scan_all_videos
                else R.string.grant_video_permission
            )
        )
      }
      TextButton(onClick = chooseFolder, enabled = !state.isScanning) {
        Text(stringResource(R.string.choose_video_folder))
      }
      if (state.isScanning) {
        CircularProgressIndicator(Modifier.padding(top = 20.dp))
        TextButton(onClick = cancelScan, modifier = Modifier.padding(top = 4.dp)) {
          Text(stringResource(R.string.cancel_video_scan))
        }
      }
    }
    return
  }

  var libraryActionsExpanded by remember { mutableStateOf(false) }
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<LocalVideoEntity?>(null) }
  var propertiesTarget by remember { mutableStateOf<LocalVideoEntity?>(null) }
  var deleteTarget by remember { mutableStateOf<VideoDeleteTarget?>(null) }
  Column(
      Modifier.fillMaxSize()
          .padding(horizontal = 12.dp)
          .videoFolderBackSwipe(state.currentFolderPath, state.query, openFolder),
  ) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (searchOpen) {
        OutlinedTextField(
            value = state.query,
            onValueChange = setQuery,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = {
              Text(
                  stringResource(R.string.search_local_videos),
                  style = MaterialTheme.typography.bodyMedium,
              )
            },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
              if (state.query.isNotBlank()) {
                IconButton(onClick = { setQuery("") }) {
                  Icon(Icons.Filled.Close, stringResource(R.string.clear_search))
                }
              }
            },
            singleLine = true,
        )
        IconButton(
            onClick = {
              searchOpen = false
              setQuery("")
            }
        ) {
          Icon(Icons.Filled.Close, stringResource(R.string.close_search))
        }
      } else {
        Text(
            stringResource(R.string.local_video),
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.isScanning) {
          CircularProgressIndicator(Modifier.size(24.dp))
          IconButton(onClick = cancelScan) {
            Icon(Icons.Filled.Close, stringResource(R.string.cancel_video_scan))
          }
        } else {
          IconButton(onClick = refresh) {
            Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_videos))
          }
        }
        IconButton(onClick = { searchOpen = true }, enabled = !state.isScanning) {
          Icon(Icons.Filled.Search, stringResource(R.string.search_local_videos_action))
        }
        if (!state.isGlobalSource) {
          IconButton(onClick = startAutomaticScan, enabled = !state.isScanning) {
            Icon(Icons.Filled.VideoLibrary, stringResource(R.string.scan_all_videos))
          }
        }
        Box {
          IconButton(
              onClick = { libraryActionsExpanded = true },
              enabled = !state.isScanning,
          ) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.local_video_actions))
          }
          DropdownMenu(
              expanded = libraryActionsExpanded,
              onDismissRequest = { libraryActionsExpanded = false },
          ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.choose_video_folder)) },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                onClick = {
                  libraryActionsExpanded = false
                  chooseFolder()
                },
            )
          }
        }
      }
    }
    if (state.isGlobalSource && !state.hasVideoPermission) {
      Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              stringResource(R.string.video_permission_required),
              Modifier.weight(1f),
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
          )
          TextButton(onClick = startAutomaticScan) {
            Text(stringResource(R.string.grant_video_permission))
          }
        }
      }
    }
    if (state.query.isBlank()) VideoBreadcrumbs(state.currentFolderPath, openFolder)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          pluralStringResource(
              R.plurals.local_video_count,
              state.playbackVideos.size,
              state.playbackVideos.size,
          ),
          Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    if (state.visibleFolders.isEmpty() && state.visibleVideos.isEmpty()) {
      Text(
          stringResource(
              if (state.query.isBlank()) R.string.no_local_videos else R.string.no_video_matches
          ),
          Modifier.padding(20.dp),
          style = MaterialTheme.typography.bodyLarge,
      )
    } else {
      LazyColumn(
          Modifier.fillMaxWidth().weight(1f),
          contentPadding = PaddingValues(bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(state.visibleFolders, key = { "folder:${it.path}" }) { folder ->
          VideoFolderRow(
              folder = folder,
              enabled = true,
              onOpen = { openFolder(folder.path) },
              onDelete = { deleteTarget = VideoDeleteTarget.Folder(folder) },
          )
        }
        items(state.visibleVideos, key = { "video:${it.id}" }) { video ->
          VideoRow(
              video = video,
              enabled = true,
              onPlay = { play(video) },
              onRename = { renameTarget = video },
              onProperties = { propertiesTarget = video },
              onDelete = { deleteTarget = VideoDeleteTarget.Video(video) },
          )
        }
      }
    }
  }
  renameTarget?.let { video ->
    VideoRenameDialog(
        video = video,
        onDismiss = { renameTarget = null },
        onConfirm = { title ->
          renameTarget = null
          renameVideo(video, title)
        },
    )
  }
  propertiesTarget?.let { video ->
    VideoPropertiesDialog(video = video, onDismiss = { propertiesTarget = null })
  }
  deleteTarget?.let { target ->
    VideoDeleteDialog(
        target = target,
        onDismiss = { deleteTarget = null },
        onConfirm = {
          deleteTarget = null
          when (target) {
            is VideoDeleteTarget.Video -> deleteVideo(target.video)
            is VideoDeleteTarget.Folder -> deleteFolder(target.folder.path)
          }
        },
    )
  }
}
@Composable
private fun VideoBreadcrumbs(currentPath: String, openFolder: (String) -> Unit) {
  if (currentPath.isBlank()) {
    Text(
        stringResource(R.string.all_local_videos),
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  val paths = buildList {
    add("")
    val segments = currentPath.split('/')
    segments.indices.forEach { add(segments.take(it + 1).joinToString("/")) }
  }
  Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    paths.forEachIndexed { index, path ->
      if (index > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(
          onClick = { openFolder(path) },
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
      ) {
        Text(
            if (path.isBlank()) stringResource(R.string.all_local_videos)
            else path.substringAfterLast('/'),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
private fun Modifier.videoFolderBackSwipe(
    currentFolderPath: String,
    query: String,
    openFolder: (String) -> Unit,
): Modifier =
    pointerInput(currentFolderPath, query) {
      if (currentFolderPath.isBlank() || query.isNotBlank()) return@pointerInput
      var totalDrag = 0f
      detectHorizontalDragGestures(
          onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
          onDragEnd = {
            if (totalDrag >= FOLDER_BACK_SWIPE_THRESHOLD_PX) {
              openFolder(currentFolderPath.substringBeforeLast('/', missingDelimiterValue = ""))
            }
          },
      )
    }

internal fun videoDetails(video: LocalVideoEntity, emptyLabel: String): String {
  val duration = video.durationMs.takeIf { it > 0L }?.let(::mediaTimeLabel)
  val resolution =
      if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else null
  return listOfNotNull(duration, resolution).joinToString(" · ").ifBlank { emptyLabel }
}

internal fun isVideoUnplayed(video: LocalVideoEntity): Boolean = video.lastOpenedEpochMs <= 0L

private const val FOLDER_BACK_SWIPE_THRESHOLD_PX = 96f
