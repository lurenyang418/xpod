package app.xpod.ui.books

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookReaderScreenTest {
  @Test
  fun uniformEdgeSamplesAreSafeForCropping() {
    val white = 0xFFFFFFFF.toInt()
    val samples = List(8) { white }

    assertTrue(isPdfBackgroundReliable(samples, white))
  }

  @Test
  fun mixedEdgeSamplesDisableHeuristicCropping() {
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    val samples = listOf(white, white, white, black, white, white, white, white)

    assertFalse(isPdfBackgroundReliable(samples, white))
  }
}
