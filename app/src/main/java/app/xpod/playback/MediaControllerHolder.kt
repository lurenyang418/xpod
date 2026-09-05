package app.xpod.playback

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.xpod.data.MusicPlaybackSettings
import app.xpod.data.PlaybackMediaType
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reactions the queue coordinator performs in response to Media3 / MediaController events. */
internal interface PlayerEventSink {
  fun onPlayerEvents(player: MediaController, events: Player.Events)

  fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters)

  fun onMediaItemTransition(player: MediaController, mediaItem: MediaItem?, reason: Int)

  fun onIsPlayingChanged(isPlaying: Boolean)

  fun onControllerDisconnected()
}

/**
 * Owns the [MediaController] connection lifecycle: lazy build, event listener registration and
 * teardown on disconnect. Media3 events are forwarded to [sink] so queue logic stays
 * connection-free.
 */
internal class MediaControllerHolder(
    private val context: Context,
    private val readInitialPlaybackState: () -> Pair<PlaybackMediaType?, MusicPlaybackSettings>,
    private val sink: PlayerEventSink,
) {
  private var controller: MediaController? = null
  private var activePlayerListener: Player.Listener? = null

  val connected: MediaController?
    get() = controller

  suspend fun controller(): MediaController =
      controller
          ?: suspendCancellableCoroutine { continuation ->
            val token = SessionToken(context, PlaybackService.component(context))
            val future =
                MediaController.Builder(context, token)
                    .setListener(
                        object : MediaController.Listener {
                          override fun onDisconnected(mediaController: MediaController) {
                            if (controller !== mediaController) return
                            activePlayerListener?.let { mediaController.removeListener(it) }
                            activePlayerListener = null
                            controller = null
                            runCatching { mediaController.release() }
                            sink.onControllerDisconnected()
                          }
                        }
                    )
                    .buildAsync()
            future.addListener(
                {
                  runCatching { future.get() }
                      .onSuccess { created ->
                        if (!continuation.isActive) {
                          created.release()
                          return@onSuccess
                        }
                        controller = created
                        val (mediaType, musicSettings) = readInitialPlaybackState()
                        applyPlaybackSettings(created, mediaType, musicSettings)
                        val playerListener =
                            object : Player.Listener {
                              override fun onEvents(player: Player, events: Player.Events) {
                                sink.onPlayerEvents(created, events)
                              }

                              override fun onPlaybackParametersChanged(
                                  playbackParameters: androidx.media3.common.PlaybackParameters
                              ) {
                                sink.onPlaybackParametersChanged(playbackParameters)
                              }

                              override fun onMediaItemTransition(
                                  mediaItem: MediaItem?,
                                  reason: Int,
                              ) {
                                sink.onMediaItemTransition(created, mediaItem, reason)
                              }

                              override fun onIsPlayingChanged(isPlaying: Boolean) {
                                sink.onIsPlayingChanged(isPlaying)
                              }
                            }
                        created.addListener(playerListener)
                        activePlayerListener = playerListener
                        continuation.resume(created)
                      }
                      .onFailure { if (continuation.isActive) continuation.cancel(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
          }
}
