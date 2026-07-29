package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRepositoryTest {
  @Test
  fun playbackTimestampsAdvanceWhenClockDoesNot() {
    assertEquals(123L, nextPlaybackTimestamp(nowEpochMs = 123L, previousEpochMs = null))
    assertEquals(124L, nextPlaybackTimestamp(nowEpochMs = 123L, previousEpochMs = 123L))
    assertEquals(200L, nextPlaybackTimestamp(nowEpochMs = 200L, previousEpochMs = 123L))
    assertEquals(
        Long.MAX_VALUE,
        nextPlaybackTimestamp(nowEpochMs = 123L, previousEpochMs = Long.MAX_VALUE),
    )
  }
}
