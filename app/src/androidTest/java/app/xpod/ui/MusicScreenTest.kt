@file:Suppress("DEPRECATION")

package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.xpod.data.LocalTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun playAllStartsTheFirstVisibleTrack() {
    val hidden = track("hidden", "Hidden")
    val visible = track("visible", "Visible")
    var playedId: String? = null

    compose.setContent {
      MaterialTheme {
        MusicScreen(
            state =
                MusicUiState(
                    tracks = listOf(hidden, visible),
                    visibleTracks = listOf(visible),
                    selectedTreeUri = "content://provider/tree/music",
                ),
            nowPlaying = null,
            chooseFolder = {},
            refresh = {},
            cancelScan = {},
            setQuery = {},
            play = { playedId = it.id },
            togglePlayback = {},
            playNext = {},
            addToQueue = {},
        )
      }
    }

    compose.onNodeWithTag("local_music_play_all").performClick()

    compose.runOnIdle { assertEquals("visible", playedId) }
  }

  @Test
  fun scanCanBeCancelledFromTheMusicScreen() {
    var cancelled = false
    compose.setContent {
      MaterialTheme {
        MusicScreen(
            state =
                MusicUiState(
                    selectedTreeUri = "content://provider/tree/music",
                    isScanning = true,
                ),
            nowPlaying = null,
            chooseFolder = {},
            refresh = {},
            cancelScan = { cancelled = true },
            setQuery = {},
            play = {},
            togglePlayback = {},
            playNext = {},
            addToQueue = {},
        )
      }
    }

    compose.onNodeWithTag("local_music_cancel_scan").performClick()

    compose.runOnIdle { assertEquals(true, cancelled) }
  }

  private fun track(id: String, title: String) =
      LocalTrackEntity(
          id = id,
          documentUri = "content://provider/document/$id",
          treeUri = "content://provider/tree/music",
          title = title,
          artist = "Artist",
          album = "Album",
          durationMs = 1_000L,
          modifiedEpochMs = 2L,
      )
}
