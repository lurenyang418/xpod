package app.xpod.playback

import app.xpod.data.PlaybackItem
import app.xpod.data.PlaybackMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRestoreTest {
  @Test
  fun podcastRestoreMovesCurrentItemToFrontAndPersistsTheNewOrder() {
    val byId = mapOf("a" to item("a"), "b" to item("b"), "c" to item("c"))
    val restored =
        assembleRestoredQueue(listOf("a", "b", "c"), byId, PlaybackMediaType.Podcast, "b")

    assertEquals(listOf("b", "a", "c"), restored.items.map { it.id })
    assertEquals("b", restored.currentMediaId)
    assertEquals(PlaybackMediaType.Podcast, restored.mediaType)
    assertTrue(restored.needsPersist)
  }

  @Test
  fun podcastRestoreWithCurrentAlreadyFirstDoesNotPersist() {
    val byId = mapOf("a" to item("a"), "b" to item("b"))
    val restored = assembleRestoredQueue(listOf("a", "b"), byId, PlaybackMediaType.Podcast, "a")

    assertEquals(listOf("a", "b"), restored.items.map { it.id })
    assertEquals("a", restored.currentMediaId)
    assertFalse(restored.needsPersist)
  }

  @Test
  fun musicRestoreKeepsPersistedOrder() {
    val byId =
        mapOf("a" to item("a", PlaybackMediaType.Music), "b" to item("b", PlaybackMediaType.Music))
    val restored = assembleRestoredQueue(listOf("a", "b"), byId, PlaybackMediaType.Music, "b")

    assertEquals(listOf("a", "b"), restored.items.map { it.id })
    assertEquals("b", restored.currentMediaId)
    assertEquals(PlaybackMediaType.Music, restored.mediaType)
    assertFalse(restored.needsPersist)
  }

  @Test
  fun fallsBackToCurrentItemWhenPersistedQueueDoesNotResolve() {
    val current = item("z")
    val restored =
        assembleRestoredQueue(
            listOf("x", "y"),
            mapOf("z" to current),
            PlaybackMediaType.Podcast,
            "z",
        )

    assertEquals(listOf("z"), restored.items.map { it.id })
    assertEquals("z", restored.currentMediaId)
    assertTrue(restored.needsPersist)
  }

  @Test
  fun emptyPersistenceWithoutCurrentYieldsEmptyRestoredQueue() {
    val restored = assembleRestoredQueue(emptyList(), emptyMap(), PlaybackMediaType.Podcast, null)

    assertTrue(restored.items.isEmpty())
    assertNull(restored.currentMediaId)
    assertNull(restored.mediaType)
    assertFalse(restored.needsPersist)
  }

  @Test
  fun emptyPersistenceWithResolvableCurrentYieldsSingleItemQueue() {
    val current = item("z")
    val restored =
        assembleRestoredQueue(emptyList(), mapOf("z" to current), PlaybackMediaType.Music, "z")

    assertEquals(listOf("z"), restored.items.map { it.id })
    assertEquals("z", restored.currentMediaId)
    assertEquals(PlaybackMediaType.Music, restored.mediaType)
    assertTrue(restored.needsPersist)
  }

  @Test
  fun unresolvedCurrentKeepsRestOfThePodcastQueue() {
    val byId = mapOf("a" to item("a"), "b" to item("b"))
    val restored = assembleRestoredQueue(listOf("a", "b"), byId, PlaybackMediaType.Podcast, "gone")

    assertEquals(listOf("a", "b"), restored.items.map { it.id })
    assertNull(restored.currentMediaId)
    assertFalse(restored.needsPersist)
  }

  private fun item(
      id: String,
      mediaType: PlaybackMediaType = PlaybackMediaType.Podcast,
  ): PlaybackItem =
      PlaybackItem(
          id = id,
          mediaType = mediaType,
          title = id,
          subtitle = "",
          uri = "https://example.com/$id",
      )
}
