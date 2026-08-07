package app.xpod.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowcaseDataTest {
  private val data =
      showcaseData(
          ShowcaseMedia(
              spaceArtwork = "android.resource://debug/drawable/space",
              cityArtwork = "android.resource://debug/drawable/city",
              technologyArtwork = "android.resource://debug/drawable/technology",
              natureArtwork = "android.resource://debug/drawable/nature",
              audio = "android.resource://debug/raw/audio",
          )
      )

  @Test
  fun showcaseCatalogHasStableUniqueRelationships() {
    assertEquals(4, data.podcasts.size)
    assertEquals(12, data.episodes.size)
    assertEquals(3, data.articleFeeds.size)
    assertEquals(6, data.articles.size)
    assertEquals(8, data.tracks.size)

    assertEquals(data.podcasts.size, data.podcasts.map { it.id }.distinct().size)
    assertEquals(data.episodes.size, data.episodes.map { it.id }.distinct().size)
    assertEquals(data.articles.size, data.articles.map { it.id }.distinct().size)
    assertTrue(data.episodes.all { episode -> data.podcasts.any { it.id == episode.podcastId } })
    assertTrue(data.articles.all { article -> data.articleFeeds.any { it.id == article.feedId } })
  }

  @Test
  fun nowPlayingItemStartsThePodcastQueue() {
    assertEquals(data.nowPlayingEpisodeId, data.queueEpisodeIds.first())
    assertTrue(data.queueEpisodeIds.all { id -> data.episodes.any { it.id == id } })
    assertTrue(data.tracks.all { it.id.startsWith("local:") })
  }
}
