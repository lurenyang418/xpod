package app.xpod.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDurationTest {
  @Test
  fun playerDurationTakesPriorityWhenKnown() {
    assertEquals(120_000L, playbackDuration(120_000L, 90_000L))
  }

  @Test
  fun itemDurationIsUsedWhilePlayerDurationIsUnknown() {
    assertEquals(90_000L, playbackDuration(0L, 90_000L))
    assertEquals(90_000L, playbackDuration(Long.MIN_VALUE, 90_000L))
  }

  @Test
  fun durationRemainsUnknownWhenNeitherSourceIsValid() {
    assertEquals(0L, playbackDuration(0L, null))
    assertEquals(0L, playbackDuration(0L, 0L))
  }
}
