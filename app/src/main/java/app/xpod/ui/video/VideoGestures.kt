package app.xpod.ui.video

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
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
