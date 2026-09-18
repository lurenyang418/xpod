package app.xpod.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerView.ControllerVisibilityListener
import app.xpod.R
import app.xpod.data.LocalVideoEntity
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

  val folderPlaybackVideos = state.playbackVideos
  var libraryActionsExpanded by remember { mutableStateOf(false) }
  var searchOpen by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
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
              folderPlaybackVideos.size,
              folderPlaybackVideos.size,
          ),
          Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
      )
      Button(
          onClick = { folderPlaybackVideos.firstOrNull()?.let(play) },
          enabled = folderPlaybackVideos.isNotEmpty(),
      ) {
        Icon(Icons.Filled.PlayArrow, null)
        Text(stringResource(R.string.play_all), Modifier.padding(start = 6.dp))
      }
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
          VideoFolderRow(folder, enabled = true) { openFolder(folder.path) }
        }
        items(state.visibleVideos, key = { "video:${it.id}" }) { video ->
          VideoRow(video, enabled = true, onPlay = { play(video) })
        }
      }
    }
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

@Composable
private fun VideoFolderRow(folder: VideoFolder, enabled: Boolean, onOpen: () -> Unit) {
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
    }
  }
}

@Composable
private fun VideoRow(video: LocalVideoEntity, enabled: Boolean, onPlay: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          Modifier.size(76.dp, 48.dp).background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            Icons.Filled.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
      IconButton(onClick = onPlay, enabled = enabled) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_video))
      }
    }
  }
}

private fun videoDetails(video: LocalVideoEntity, emptyLabel: String): String {
  val duration = video.durationMs.takeIf { it > 0L }?.let(::mediaTimeLabel)
  val resolution =
      if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else null
  return listOfNotNull(duration, resolution).joinToString(" · ").ifBlank { emptyLabel }
}

internal fun isVideoUnplayed(video: LocalVideoEntity): Boolean = video.lastOpenedEpochMs <= 0L

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
internal fun VideoPlayerScreen(
    video: LocalVideoEntity?,
    player: ExoPlayer,
    playerState: VideoPlayerState,
    playlist: List<LocalVideoEntity>,
    playlistTitle: String,
    onClose: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSelectVideo: (String) -> Unit,
) {
  val speedOptions = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
  var controlsVisible by remember { mutableStateOf(true) }
  var gestureFeedback by remember { mutableStateOf<VideoGestureFeedback?>(null) }
  var speedBoosted by remember { mutableStateOf(false) }
  var showRemainingTime by remember { mutableStateOf(false) }
  var showPlaylist by remember { mutableStateOf(false) }
  val activity = LocalActivity.current
  val context = LocalContext.current
  val view = LocalView.current
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val isInPictureInPictureMode = activity?.isInPictureInPictureMode == true
  val touchListener =
      remember(activity, player) {
        VideoGestureTouchListener(
            window = activity?.window,
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
            player = player,
            onFeedback = { gestureFeedback = it },
            onSpeedBoostChanged = { speedBoosted = it },
        )
      }
  DisposableEffect(activity, view) {
    val previousOrientation = activity?.requestedOrientation
    val window = activity?.window
    val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
    val previousSystemBarsBehavior = insetsController?.systemBarsBehavior
    val previousScreenBrightness = window?.attributes?.screenBrightness
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    window?.let {
      WindowCompat.setDecorFitsSystemWindows(it, false)
      insetsController?.hide(WindowInsetsCompat.Type.systemBars())
      insetsController?.systemBarsBehavior =
          androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    onDispose {
      window?.let {
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        previousSystemBarsBehavior?.let { behavior ->
          insetsController.systemBarsBehavior = behavior
        }
        previousScreenBrightness?.let { brightness ->
          it.attributes = it.attributes.apply { screenBrightness = brightness }
        }
        WindowCompat.setDecorFitsSystemWindows(it, true)
      }
      previousOrientation?.let { activity.requestedOrientation = it }
    }
  }
  DisposableEffect(touchListener) { onDispose(touchListener::dispose) }
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
        factory = {
          PlayerView(it).apply {
            useController = true
            controllerAutoShow = true
            controllerShowTimeoutMs = 3_000
            setControllerVisibilityListener(
                ControllerVisibilityListener { visibility ->
                  controlsVisible = visibility == View.VISIBLE
                }
            )
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setOnTouchListener(touchListener)
            findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
            val durationView =
                findViewById<TextView>(androidx.media3.ui.R.id.exo_duration).apply {
                  isClickable = true
                  setOnClickListener {
                    showRemainingTime = !showRemainingTime
                    updateVideoDurationLabel(this, player, showRemainingTime)
                  }
                }
            findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                ?.setProgressUpdateListener { positionMs, _ ->
                  if (showRemainingTime) {
                    updateVideoDurationLabel(
                        durationView,
                        player,
                        showRemainingTime,
                        positionMs,
                    )
                  }
                }
            setBackgroundColor(android.graphics.Color.BLACK)
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            this.player = player
          }
        },
        update = {
          it.player = player
          if (isInPictureInPictureMode) it.hideController()
        },
        modifier = Modifier.fillMaxSize(),
    )
    if (controlsVisible && !isInPictureInPictureMode) {
      Surface(
          color = Color.Black.copy(alpha = 0.46f),
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .statusBarsPadding()
                  .padding(horizontal = 8.dp, vertical = 8.dp)
                  .align(Alignment.TopCenter),
      ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.close_video), tint = Color.White)
          }
          Text(
              video?.title ?: stringResource(R.string.local_video),
              Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = Color.White,
              style = MaterialTheme.typography.titleMedium,
          )
          IconButton(
              onClick = { showPlaylist = true },
              enabled = playlist.isNotEmpty(),
          ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = stringResource(R.string.video_playlist),
                tint = Color.White,
            )
          }
          if (activity != null) {
            IconButton(
                onClick = {
                  activity.enterPictureInPictureMode(
                      buildPictureInPictureParams(
                          autoEnter = false,
                          width = video?.width ?: 16,
                          height = video?.height ?: 9,
                      )
                  )
                }
            ) {
              Icon(
                  Icons.Filled.PictureInPictureAlt,
                  contentDescription = stringResource(R.string.enter_picture_in_picture),
                  tint = Color.White,
              )
            }
          }
          TextButton(
              onClick = {
                activity?.requestedOrientation =
                    if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
              },
          ) {
            Text(
                stringResource(if (isLandscape) R.string.lock_portrait else R.string.lock_landscape),
                color = Color.White,
            )
          }
          TextButton(
              onClick = {
                val currentIndex = speedOptions.indexOfFirst { it == playerState.speed }
                onSetSpeed(speedOptions[(currentIndex + 1).mod(speedOptions.size)])
              },
          ) {
            Text(speedLabel(playerState.speed), color = Color.White)
          }
        }
      }
    }
    if (showPlaylist && !isInPictureInPictureMode) {
      VideoPlaylistSheet(
          playlist = playlist,
          currentVideoId = video?.id,
          folderPath = playlistTitle,
          onDismiss = { showPlaylist = false },
          onSelectVideo = {
            showPlaylist = false
            onSelectVideo(it)
          },
      )
    }
    if (playerState.status == VideoPlaybackStatus.Error) {
      Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
      ) {
        Text(
            stringResource(R.string.video_playback_failed),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
      }
    }
    if (!isInPictureInPictureMode && speedBoosted) {
      GestureFeedbackCard(
          text = stringResource(R.string.speed_boost_feedback),
          modifier = Modifier.align(Alignment.Center),
      )
    } else if (!isInPictureInPictureMode) {
      gestureFeedback?.let { feedback ->
        GestureFeedbackCard(
            text =
                stringResource(
                    when (feedback.kind) {
                      VideoGestureKind.Brightness -> R.string.brightness_level
                      VideoGestureKind.Volume -> R.string.volume_level
                    },
                    feedback.levelPercent,
                ),
            modifier = Modifier.align(Alignment.Center),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlaylistSheet(
    playlist: List<LocalVideoEntity>,
    currentVideoId: String?,
    folderPath: String,
    onDismiss: () -> Unit,
    onSelectVideo: (String) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.video_playlist), style = MaterialTheme.typography.titleLarge)
      Text(
          folderPath.ifBlank { stringResource(R.string.all_local_videos) },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          pluralStringResource(R.plurals.local_video_count, playlist.size, playlist.size),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      LazyColumn(
          Modifier.heightIn(max = 440.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(playlist, key = { it.id }) { item ->
          val active = item.id == currentVideoId
          Surface(
              color =
                  if (active) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surface,
              shape = MaterialTheme.shapes.medium,
              modifier =
                  Modifier.fillMaxWidth().clickable(enabled = !active) { onSelectVideo(item.id) },
          ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                  if (active) Icons.Filled.PlayArrow else Icons.Filled.VideoLibrary,
                  contentDescription = null,
                  tint =
                      if (active) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(28.dp),
              )
              Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (active) {
                  Text(
                      stringResource(R.string.now_playing),
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                  )
                } else {
                  Text(
                      videoDetails(item, stringResource(R.string.video_file_details)),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun updateVideoDurationLabel(
    durationView: TextView,
    player: ExoPlayer,
    showRemainingTime: Boolean,
    positionMs: Long = player.currentPosition,
) {
  val durationMs = player.duration
  if (durationMs <= 0L) return
  val displayedMs =
      if (showRemainingTime) (durationMs - positionMs).coerceAtLeast(0L) else durationMs
  durationView.text =
      if (showRemainingTime) "−${mediaTimeLabel(displayedMs)}" else mediaTimeLabel(displayedMs)
}

@Composable
private fun GestureFeedbackCard(text: String, modifier: Modifier = Modifier) {
  Surface(
      color = Color.Black.copy(alpha = 0.72f),
      shape = MaterialTheme.shapes.medium,
      modifier = modifier,
  ) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
  }
}

private enum class VideoGestureKind {
  Brightness,
  Volume,
}

private data class VideoGestureFeedback(
    val kind: VideoGestureKind,
    val levelPercent: Int,
)

private class VideoGestureTouchListener(
    private val window: android.view.Window?,
    private val audioManager: AudioManager?,
    private val player: ExoPlayer,
    private val onFeedback: (VideoGestureFeedback?) -> Unit,
    private val onSpeedBoostChanged: (Boolean) -> Unit,
) : View.OnTouchListener {
  private val handler = Handler(Looper.getMainLooper())
  private val speedBoostRunnable = Runnable {
    if (tracking && !verticalGesture && player.isPlaying) {
      previousSpeed = player.playbackParameters.speed
      player.setPlaybackSpeed(2f)
      speedBoosted = true
      onSpeedBoostChanged(true)
    }
  }
  private var tracking = false
  private var verticalGesture = false
  private var speedBoosted = false
  private var downX = 0f
  private var downY = 0f
  private var brightnessStart = 0.5f
  private var volumeStart = 0
  private var maxVolume = 0
  private var previousSpeed = 1f
  private var touchSlop = 8f
  private var gestureKind: VideoGestureKind? = null

  override fun onTouch(view: View, event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        handler.removeCallbacks(speedBoostRunnable)
        tracking = true
        verticalGesture = false
        speedBoosted = false
        gestureKind =
            when {
              event.x < view.width * 0.35f -> VideoGestureKind.Brightness
              event.x > view.width * 0.65f -> VideoGestureKind.Volume
              else -> null
            }
        downX = event.x
        downY = event.y
        touchSlop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        brightnessStart = window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        volumeStart = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        handler.postDelayed(speedBoostRunnable, LONG_PRESS_TIMEOUT_MS)
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        if (!tracking) return false
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        if (!verticalGesture && abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX)) {
          handler.removeCallbacks(speedBoostRunnable)
          gestureKind?.let {
            verticalGesture = true
            when (it) {
              VideoGestureKind.Brightness -> updateBrightness(view.height, deltaY)
              VideoGestureKind.Volume -> updateVolume(view.height, deltaY)
            }
          }
        } else if (verticalGesture) {
          when (gestureKind) {
            VideoGestureKind.Brightness -> updateBrightness(view.height, deltaY)
            VideoGestureKind.Volume -> updateVolume(view.height, deltaY)
            null -> Unit
          }
        }
        return verticalGesture || speedBoosted
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        handler.removeCallbacks(speedBoostRunnable)
        val handled = verticalGesture || speedBoosted
        if (speedBoosted) {
          player.setPlaybackSpeed(previousSpeed)
          speedBoosted = false
          onSpeedBoostChanged(false)
        }
        tracking = false
        verticalGesture = false
        gestureKind = null
        onFeedback(null)
        return handled
      }
    }
    return false
  }

  fun dispose() {
    handler.removeCallbacks(speedBoostRunnable)
    if (speedBoosted) {
      player.setPlaybackSpeed(previousSpeed)
      speedBoosted = false
      onSpeedBoostChanged(false)
    }
    tracking = false
    onFeedback(null)
  }

  private fun updateBrightness(height: Int, deltaY: Float) {
    val value = (brightnessStart - deltaY / height.coerceAtLeast(1)).coerceIn(0.01f, 1f)
    window?.let { targetWindow ->
      targetWindow.attributes = targetWindow.attributes.apply { screenBrightness = value }
    }
    onFeedback(VideoGestureFeedback(VideoGestureKind.Brightness, (value * 100).roundToInt()))
  }

  private fun updateVolume(height: Int, deltaY: Float) {
    if (audioManager == null || maxVolume <= 0) return
    val value =
        (volumeStart + (-deltaY / height.coerceAtLeast(1) * maxVolume))
            .roundToInt()
            .coerceIn(0, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
    onFeedback(VideoGestureFeedback(VideoGestureKind.Volume, value * 100 / maxVolume))
  }
}

private const val LONG_PRESS_TIMEOUT_MS = 500L
