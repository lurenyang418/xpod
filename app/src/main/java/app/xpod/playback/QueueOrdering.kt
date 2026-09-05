package app.xpod.playback

import androidx.media3.common.Player
import app.xpod.data.PlaybackItem
import app.xpod.data.PlaybackMediaType

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

internal fun shouldClearCompletedQueue(
    playbackState: Int,
    currentMediaItemIndex: Int,
    mediaItemCount: Int,
): Boolean =
    playbackState == Player.STATE_ENDED &&
        mediaItemCount > 0 &&
        currentMediaItemIndex == mediaItemCount - 1

internal fun Player.playbackStatus(): PlaybackStatus =
    playbackStatus(playbackState, playWhenReady, isPlaying, playerError != null)

internal data class RestoredQueue(
    val items: List<PlaybackItem>,
    val currentMediaId: String?,
    val mediaType: PlaybackMediaType?,
    val needsPersist: Boolean,
)

/**
 * Assembles the persisted queue back into the active queue model.
 *
 * - Keeps persisted order but moves the current item to the front for podcasts.
 * - Falls back to the single saved current item when nothing persisted resolves.
 * - Reports [RestoredQueue.needsPersist] when the assembled order differs from what is stored.
 */
internal fun assembleRestoredQueue(
    persistedMediaIds: List<String>,
    resolvedById: Map<String, PlaybackItem>,
    storedMediaType: PlaybackMediaType,
    currentMediaId: String?,
): RestoredQueue {
  val restoredFromPersistence = buildList {
    persistedMediaIds.forEach { id -> resolvedById[id]?.let { add(it) } }
  }
  val restoredItems =
      if (restoredFromPersistence.isNotEmpty()) restoredFromPersistence
      else currentMediaId?.let { resolvedById[it] }?.let(::listOf).orEmpty()
  val currentIndex = restoredItems.indexOfFirst { it.id == currentMediaId }
  val items =
      if (storedMediaType == PlaybackMediaType.Podcast) restoredItems.moveItemToFront(currentIndex)
      else restoredItems
  return RestoredQueue(
      items = items,
      currentMediaId = currentMediaId?.takeIf { id -> items.any { it.id == id } },
      mediaType = storedMediaType.takeIf { items.isNotEmpty() },
      needsPersist = items.map { it.id } != persistedMediaIds,
  )
}
