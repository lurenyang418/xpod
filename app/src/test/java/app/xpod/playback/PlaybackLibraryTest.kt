package app.xpod.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLibraryTest {
  @Test
  fun libraryPagesAreBoundedAndRejectInvalidRequests() {
    val items = listOf("a", "b", "c", "d", "e")

    assertEquals(listOf("a", "b"), pageItems(items, page = 0, pageSize = 2))
    assertEquals(listOf("c", "d"), pageItems(items, page = 1, pageSize = 2))
    assertEquals(listOf("e"), pageItems(items, page = 2, pageSize = 2))
    assertEquals(emptyList<String>(), pageItems(items, page = 3, pageSize = 2))
    assertEquals(emptyList<String>(), pageItems(items, page = -1, pageSize = 2))
    assertEquals(emptyList<String>(), pageItems(items, page = 0, pageSize = 0))
  }
}
