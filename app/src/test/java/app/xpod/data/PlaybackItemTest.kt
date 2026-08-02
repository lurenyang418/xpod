package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackItemTest {
  @Test
  fun podcastPlaybackItemKeepsFeedDuration() {
    val episode =
        EpisodeEntity(
            id = "episode",
            podcastId = "podcast",
            stableKey = "stable",
            title = "Episode",
            description = "Description",
            audioUrl = "https://example.com/episode.mp3",
            publishedEpochMs = 0L,
            durationMs = 90_000L,
            artworkUrl = null,
        )

    assertEquals(90_000L, episode.asPlaybackItem().durationMs)
  }

  @Test
  fun localPlaybackItemKeepsExtractedDuration() {
    val track =
        LocalTrackEntity(
            id = "local:track",
            documentUri = "content://music/track",
            treeUri = "content://music",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 120_000L,
            modifiedEpochMs = 0L,
        )

    assertEquals(120_000L, track.asPlaybackItem().durationMs)
  }
}
