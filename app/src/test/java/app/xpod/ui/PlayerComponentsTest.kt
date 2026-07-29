package app.xpod.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerComponentsTest {
  @Test
  fun onlyPositiveDurationsAreSeekable() {
    assertEquals(123L, knownDuration(123L))
    assertNull(knownDuration(0L))
    assertNull(knownDuration(Long.MIN_VALUE))
  }

  @Test
  fun mediaTimesUseMinutesAndZeroPaddedSeconds() {
    assertEquals("0:00", mediaTimeLabel(-1L))
    assertEquals("1:05", mediaTimeLabel(65_000L))
  }
}
