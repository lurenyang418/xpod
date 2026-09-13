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

  @Test
  fun speedLabelsRenderExactValuesWithoutTrailingZeros() {
    assertEquals("1x", speedLabel(1f))
    assertEquals("2x", speedLabel(2f))
    assertEquals("0.75x", speedLabel(0.75f))
    assertEquals("1.25x", speedLabel(1.25f))
    assertEquals("1.5x", speedLabel(1.5f))
    assertEquals("1.75x", speedLabel(1.75f))
  }
}
