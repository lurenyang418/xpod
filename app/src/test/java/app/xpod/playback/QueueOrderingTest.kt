package app.xpod.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOrderingTest {
  @Test
  fun selectedItemMovesToFrontWithoutChangingRemainingOrder() {
    assertEquals(
        listOf("third", "first", "second", "fourth"),
        listOf("first", "second", "third", "fourth").moveItemToFront(2),
    )
  }

  @Test
  fun currentItemStaysAtFront() {
    val episodes = listOf("first", "second", "third")
    val reordered = episodes.moveItemToFront(0)
    assertEquals(
        listOf("first", "second", "third"),
        reordered,
    )
    assertSame(episodes, reordered)
  }

  @Test
  fun lastItemMovesToFront() {
    val episodes = listOf("first", "second", "third")
    val reordered = episodes.moveItemToFront(episodes.lastIndex)
    assertEquals(listOf("third", "first", "second"), reordered)
    assertNotSame(episodes, reordered)
  }

  @Test
  fun invalidItemIndexKeepsOriginalList() {
    val episodes = listOf("first", "second", "third")
    assertSame(episodes, episodes.moveItemToFront(-1))
    assertSame(episodes, episodes.moveItemToFront(episodes.size))
  }

  @Test
  fun automaticAdvanceConsumesCompletedItems() {
    assertEquals(
        listOf("third", "fourth"),
        listOf("first", "second", "third", "fourth").remainingFrom(2),
    )
  }

  @Test
  fun remainingFromFirstItemKeepsAllItems() {
    val episodes = listOf("first", "second", "third")
    assertEquals(episodes, episodes.remainingFrom(0))
  }

  @Test
  fun invalidRemainingIndexKeepsOriginalList() {
    val episodes = listOf("first", "second", "third")
    assertSame(episodes, episodes.remainingFrom(-1))
    assertSame(episodes, episodes.remainingFrom(episodes.size))
  }

  @Test
  fun transitionActionConsumesOnlyAutomaticOrAdjacentForwardItems() {
    assertEquals(
        QueueTransitionAction.ConsumeEarlier,
        queueTransitionAction(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, 2),
    )
    assertEquals(
        QueueTransitionAction.ConsumeEarlier,
        queueTransitionAction(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, 1),
    )
    assertEquals(
        QueueTransitionAction.PromoteCurrent,
        queueTransitionAction(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, 2),
    )
    assertEquals(
        QueueTransitionAction.Keep,
        queueTransitionAction(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, 1),
    )
    assertEquals(
        QueueTransitionAction.Keep,
        queueTransitionAction(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, 0),
    )
  }

  @Test
  fun distantSeekPromotesTargetWithoutRemovingSkippedItems() {
    assertEquals(
        listOf("fifth", "second", "third", "fourth"),
        listOf("second", "third", "fourth", "fifth")
            .applyTransition(QueueTransitionAction.PromoteCurrent, 3),
    )
  }

  @Test
  fun adjacentSeekConsumesPreviousCurrentItem() {
    assertEquals(
        listOf("third", "fourth"),
        listOf("second", "third", "fourth")
            .applyTransition(QueueTransitionAction.ConsumeEarlier, 1),
    )
  }

  @Test
  fun playbackStatusDistinguishesPlayingBufferingPausedEndedAndError() {
    assertEquals(
        PlaybackStatus.Playing,
        playbackStatus(
            Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            hasError = false,
        ),
    )
    assertEquals(
        PlaybackStatus.Buffering,
        playbackStatus(
            Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false,
            hasError = false,
        ),
    )
    assertEquals(
        PlaybackStatus.Paused,
        playbackStatus(
            Player.STATE_READY,
            playWhenReady = false,
            isPlaying = false,
            hasError = false,
        ),
    )
    assertEquals(
        PlaybackStatus.Ended,
        playbackStatus(
            Player.STATE_ENDED,
            playWhenReady = true,
            isPlaying = false,
            hasError = false,
        ),
    )
    assertEquals(
        PlaybackStatus.Error,
        playbackStatus(Player.STATE_IDLE, playWhenReady = true, isPlaying = false, hasError = true),
    )
    assertTrue(PlaybackStatus.Buffering.showsPauseAction)
    assertFalse(PlaybackStatus.Ended.showsPauseAction)
  }

  @Test
  fun completedFinalItemClearsQueue() {
    assertTrue(
        shouldClearCompletedQueue(
            playbackState = Player.STATE_ENDED,
            currentMediaItemIndex = 2,
            mediaItemCount = 3,
        )
    )
  }

  @Test
  fun queueIsKeptUntilFinalItemCompletes() {
    assertFalse(
        shouldClearCompletedQueue(
            playbackState = Player.STATE_READY,
            currentMediaItemIndex = 2,
            mediaItemCount = 3,
        )
    )
    assertFalse(
        shouldClearCompletedQueue(
            playbackState = Player.STATE_ENDED,
            currentMediaItemIndex = 1,
            mediaItemCount = 3,
        )
    )
    assertFalse(
        shouldClearCompletedQueue(
            playbackState = Player.STATE_ENDED,
            currentMediaItemIndex = 0,
            mediaItemCount = 0,
        )
    )
  }
}
