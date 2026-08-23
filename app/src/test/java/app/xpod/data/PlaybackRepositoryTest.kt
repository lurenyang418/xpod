package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

  @Test
  fun stalePlaybackSnapshotsAreIgnored() {
    assertTrue(
        shouldPersistPlaybackSnapshot(previousUpdatedAtEpochMs = null, capturedAtEpochMs = 1L)
    )
    assertTrue(shouldPersistPlaybackSnapshot(previousUpdatedAtEpochMs = 1L, capturedAtEpochMs = 2L))
    assertFalse(
        shouldPersistPlaybackSnapshot(previousUpdatedAtEpochMs = 2L, capturedAtEpochMs = 1L)
    )
    assertFalse(
        shouldPersistPlaybackSnapshot(previousUpdatedAtEpochMs = 2L, capturedAtEpochMs = 2L)
    )
  }
}
