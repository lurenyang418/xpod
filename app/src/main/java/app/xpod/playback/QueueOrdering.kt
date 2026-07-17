package app.xpod.playback

import androidx.media3.common.Player

internal fun <T> List<T>.moveItemToFront(index: Int): List<T> {
  if (index !in indices || index == 0) return this
  return buildList(size) {
    add(this@moveItemToFront[index])
    this@moveItemToFront.forEachIndexed { itemIndex, item ->
      if (itemIndex != index) add(item)
    }
  }
}

internal fun <T> List<T>.remainingFrom(index: Int): List<T> =
    if (index in indices) drop(index) else this

internal enum class QueueTransitionAction {
  Keep,
  ConsumeEarlier,
  PromoteCurrent,
}

internal fun queueTransitionAction(
    transitionReason: Int,
    episodeIndex: Int,
): QueueTransitionAction =
    when {
      episodeIndex <= 0 -> QueueTransitionAction.Keep
      transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ->
          QueueTransitionAction.ConsumeEarlier
      transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && episodeIndex == 1 ->
          QueueTransitionAction.ConsumeEarlier
      transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
          QueueTransitionAction.PromoteCurrent
      else -> QueueTransitionAction.Keep
    }

internal fun <T> List<T>.applyTransition(
    action: QueueTransitionAction,
    episodeIndex: Int,
): List<T> =
    when (action) {
      QueueTransitionAction.Keep -> this
      QueueTransitionAction.ConsumeEarlier -> remainingFrom(episodeIndex)
      QueueTransitionAction.PromoteCurrent -> moveItemToFront(episodeIndex)
    }

internal fun playbackStatus(
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    hasError: Boolean,
): PlaybackStatus =
    when {
      hasError -> PlaybackStatus.Error
      playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
      isPlaying -> PlaybackStatus.Playing
      playWhenReady -> PlaybackStatus.Buffering
      else -> PlaybackStatus.Paused
    }

internal fun Player.playbackStatus(): PlaybackStatus =
    playbackStatus(playbackState, playWhenReady, isPlaying, playerError != null)
