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
import app.xpod.ui.player.buildPictureInPictureParams
import app.xpod.ui.player.mediaTimeLabel
import app.xpod.ui.player.speedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun GestureFeedbackCard(text: String, modifier: Modifier = Modifier) {
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

internal enum class VideoDoubleTapZone {
  Left,
  Center,
  Right,
}

internal enum class VideoGestureKind {
  Brightness,
  Volume,
}

internal data class VideoGestureFeedback(
    val kind: VideoGestureKind,
    val levelPercent: Int,
)

internal data class VideoTapFeedback(
    val textResId: Int,
    val token: Long,
)

internal class VideoGestureTouchListener(
    private val context: Context,
    private val window: android.view.Window?,
    private val audioManager: AudioManager?,
    private val player: ExoPlayer,
    private val onFeedback: (VideoGestureFeedback?) -> Unit,
    private val onSpeedBoostChanged: (Boolean) -> Unit,
    private val onDoubleTap: (VideoDoubleTapZone) -> Unit,
) : View.OnTouchListener {
  private val handler = Handler(Looper.getMainLooper())
  private var lastViewWidth = 1
  private var doubleTapHandled = false
  private val doubleTapDetector =
      android.view.GestureDetector(
          context,
          object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
              doubleTapHandled = true
              onDoubleTap(
                  when {
                    event.x < lastViewWidth * 0.33f -> VideoDoubleTapZone.Left
                    event.x > lastViewWidth * 0.67f -> VideoDoubleTapZone.Right
                    else -> VideoDoubleTapZone.Center
                  }
              )
              return true
            }
          },
      )
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
    lastViewWidth = view.width.coerceAtLeast(1)
    if (event.actionMasked == MotionEvent.ACTION_DOWN) doubleTapHandled = false
    doubleTapDetector.onTouchEvent(event)
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
        val handled = verticalGesture || speedBoosted || doubleTapHandled
        if (speedBoosted) {
          player.setPlaybackSpeed(previousSpeed)
          speedBoosted = false
          onSpeedBoostChanged(false)
        }
        tracking = false
        verticalGesture = false
        gestureKind = null
        doubleTapHandled = false
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
private const val FOLDER_BACK_SWIPE_THRESHOLD_PX = 96f
