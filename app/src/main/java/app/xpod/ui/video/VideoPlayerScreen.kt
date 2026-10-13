package app.xpod.ui.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import app.xpod.ui.player.buildPictureInPictureParams
import app.xpod.ui.player.mediaTimeLabel
import app.xpod.ui.player.speedLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
internal fun VideoPlayerScreen(
    video: LocalVideoEntity?,
    player: ExoPlayer,
    playerState: VideoPlayerState,
    playlist: List<LocalVideoEntity>,
    onClose: () -> Unit,
    onTogglePlayback: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSelectVideo: (String) -> Unit,
    showClock: Boolean,
    showBattery: Boolean,
) {
  val speedOptions = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
  var controlsVisible by remember { mutableStateOf(true) }
  var gestureFeedback by remember { mutableStateOf<VideoGestureFeedback?>(null) }
  var speedBoosted by remember { mutableStateOf(false) }
  var showRemainingTime by remember { mutableStateOf(false) }
  var showPlaylist by remember { mutableStateOf(false) }
  var tapFeedback by remember { mutableStateOf<VideoTapFeedback?>(null) }
  var currentTime by remember { mutableStateOf(videoPlayerClockLabel()) }
  var batteryPercent by remember { mutableStateOf<Int?>(null) }
  val activity = LocalActivity.current
  val context = LocalContext.current
  val view = LocalView.current
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val isInPictureInPictureMode = activity?.isInPictureInPictureMode == true
  val playbackErrorText = playerState.errorMessage ?: stringResource(R.string.video_playback_failed)
  val playbackErrorTitle = stringResource(R.string.video_playback_failed)
  LaunchedEffect(showClock) {
    if (showClock) {
      while (true) {
        currentTime = videoPlayerClockLabel()
        delay(30_000L)
      }
    }
  }
  DisposableEffect(context, showBattery) {
    if (!showBattery) return@DisposableEffect onDispose {}
    val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra("level", -1)
            val scale = intent.getIntExtra("scale", -1)
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
          }
        }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    onDispose { context.unregisterReceiver(receiver) }
  }
  val touchListener =
      remember(activity, player) {
        VideoGestureTouchListener(
            context = context,
            window = activity?.window,
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
            player = player,
            onFeedback = { gestureFeedback = it },
            onSpeedBoostChanged = { speedBoosted = it },
            onDoubleTap = { zone ->
              val feedbackResId =
                  when (zone) {
                    VideoDoubleTapZone.Left -> {
                      onSeekBy(-10_000L)
                      R.string.video_seek_backward_feedback
                    }
                    VideoDoubleTapZone.Center -> {
                      val wasPlaying = player.isPlaying
                      onTogglePlayback()
                      if (wasPlaying) R.string.video_pause_feedback
                      else R.string.video_play_feedback
                    }
                    VideoDoubleTapZone.Right -> {
                      onSeekBy(10_000L)
                      R.string.video_seek_forward_feedback
                    }
                  }
              tapFeedback = VideoTapFeedback(feedbackResId, android.os.SystemClock.uptimeMillis())
            },
        )
      }
  LaunchedEffect(tapFeedback?.token) {
    if (tapFeedback != null) {
      delay(800L)
      tapFeedback = null
    }
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
          (LayoutInflater.from(it).inflate(R.layout.xpod_video_player, null, false) as PlayerView)
              .apply {
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
          if (playerState.durationMs > 0L) {
            Text(
                text =
                    stringResource(
                        R.string.video_progress_compact,
                        (playerState.positionMs * 100 / playerState.durationMs)
                            .toInt()
                            .coerceIn(0, 100),
                    ),
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium,
            )
          }
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
          IconButton(
              onClick = {
                activity?.requestedOrientation =
                    if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
              },
          ) {
            Icon(
                Icons.Filled.ScreenRotation,
                contentDescription =
                    stringResource(
                        if (isLandscape) R.string.lock_portrait else R.string.lock_landscape
                    ),
                tint = Color.White,
            )
          }
          IconButton(onClick = onRestart) {
            Icon(
                Icons.Filled.Replay,
                contentDescription = stringResource(R.string.restart_video),
                tint = Color.White,
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
          if (showClock || showBattery) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              if (showClock) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
              }
              if (showBattery) {
                batteryPercent?.let {
                  Text(
                      text = "$it%",
                      color = Color.White,
                      style = MaterialTheme.typography.labelLarge,
                      modifier = Modifier.padding(start = 12.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
    if (showPlaylist && !isInPictureInPictureMode) {
      VideoPlaylistDrawer(
          playlist = playlist,
          currentVideoId = video?.id,
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
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              playbackErrorText,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_video)) }
          TextButton(
              onClick = {
                val clip =
                    android.content.ClipData.newPlainText(
                        playbackErrorTitle,
                        playbackErrorText,
                    )
                context
                    .getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(clip)
              }
          ) {
            Text(stringResource(R.string.copy_error))
          }
        }
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
      tapFeedback?.let { feedback ->
        GestureFeedbackCard(
            text = stringResource(feedback.textResId),
            modifier = Modifier.align(Alignment.Center),
        )
      }
    }
  }
}

private fun videoPlayerClockLabel(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

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
