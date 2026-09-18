@file:Suppress("DEPRECATION")

package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.xpod.data.DownloadPhase
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.ui.podcasts.LibraryScreen
import app.xpod.ui.shell.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun failedDownloadOffersRetryAndRemoveActions() {
    var retryCount = 0
    var removedId: String? = null
    val episode = episode()

    compose.setContent {
      MaterialTheme {
        LibraryScreen(
            state = MainUiState(libraryEpisodes = listOf(episode)),
            play = {},
            favorite = {},
            download = { retryCount++ },
            requestRemoveFailedDownload = { removedId = it.id },
            played = { _, _ -> },
            nowPlaying = null,
            downloadStates =
                mapOf(
                    episode.id to DownloadState(progress = null, phase = DownloadPhase.Failed),
                ),
            openEpisode = {},
            togglePlayback = {},
            addToQueue = {},
        )
      }
    }

    compose.onNodeWithTag("library_filter_DownloadTasks").performClick()
    compose.onNodeWithTag("download_actions_${episode.id}").performClick()
    compose.onNodeWithTag("retry_download_${episode.id}").performClick()
    compose.waitForIdle()
    compose.onAllNodesWithTag("retry_download_${episode.id}").assertCountEquals(0)
    compose.runOnIdle { assertEquals(1, retryCount) }

    compose.onNodeWithTag("download_actions_${episode.id}").performClick()
    compose.onNodeWithTag("remove_download_${episode.id}").performClick()
    compose.waitForIdle()
    compose.onAllNodesWithTag("remove_download_${episode.id}").assertCountEquals(0)
    compose.runOnIdle { assertEquals(episode.id, removedId) }
  }

  @Test
  fun downloadActionsMenuOnlyAppearsForFailedDownloads() {
    val episodes =
        listOf(
            episode("failed"),
            episode("queued"),
            episode("downloading"),
            episode("waiting"),
        )
    val states =
        mapOf(
            "failed" to DownloadState(progress = null, phase = DownloadPhase.Failed),
            "queued" to DownloadState(progress = null, phase = DownloadPhase.Queued),
            "downloading" to DownloadState(progress = 0.5f, phase = DownloadPhase.Downloading),
            "waiting" to DownloadState(progress = null, phase = DownloadPhase.WaitingForNetwork),
        )

    compose.setContent {
      MaterialTheme {
        LibraryScreen(
            state = MainUiState(libraryEpisodes = episodes),
            play = {},
            favorite = {},
            download = {},
            requestRemoveFailedDownload = {},
            played = { _, _ -> },
            nowPlaying = null,
            downloadStates = states,
            openEpisode = {},
            togglePlayback = {},
            addToQueue = {},
        )
      }
    }

    compose.onNodeWithTag("library_filter_DownloadTasks").performClick()
    compose.onNodeWithTag("episode_card_failed").performScrollTo()
    compose.onNodeWithTag("download_actions_failed").assertIsDisplayed()
    compose.onNodeWithTag("episode_card_queued").performScrollTo()
    compose.onAllNodesWithTag("download_actions_queued").assertCountEquals(0)
    compose.onNodeWithTag("episode_card_downloading").performScrollTo()
    compose.onAllNodesWithTag("download_actions_downloading").assertCountEquals(0)
    compose.onNodeWithTag("episode_card_waiting").performScrollTo()
    compose.onAllNodesWithTag("download_actions_waiting").assertCountEquals(0)
  }

  @Test
  fun completedDownloadHasNoFailedActionsMenu() {
    val episode = episode("completed")

    compose.setContent {
      MaterialTheme {
        LibraryScreen(
            state = MainUiState(libraryEpisodes = listOf(episode)),
            play = {},
            favorite = {},
            download = {},
            requestRemoveFailedDownload = {},
            played = { _, _ -> },
            nowPlaying = null,
            downloadStates = mapOf(episode.id to DownloadState(progress = 1f, isCompleted = true)),
            openEpisode = {},
            togglePlayback = {},
            addToQueue = {},
        )
      }
    }

    compose.onNodeWithTag("episode_card_completed").assertIsDisplayed()
    compose.onAllNodesWithTag("download_actions_completed").assertCountEquals(0)
  }

  @Test
  fun allLibraryFiltersCanBeSelected() {
    compose.setContent {
      MaterialTheme {
        LibraryScreen(
            state = MainUiState(libraryEpisodes = listOf(episode("all"))),
            play = {},
            favorite = {},
            download = {},
            requestRemoveFailedDownload = {},
            played = { _, _ -> },
            nowPlaying = null,
            downloadStates = emptyMap(),
            openEpisode = {},
            togglePlayback = {},
            addToQueue = {},
        )
      }
    }

    val filters =
        listOf(
            "Downloaded",
            "DownloadTasks",
            "ContinueListening",
            "Unplayed",
            "Recent",
            "Favorites",
            "All",
        )
    compose.onNodeWithTag("library_filter_Downloaded").assertIsSelected()
    filters.drop(1).forEachIndexed { index, filter ->
      if (index >= 3) {
        compose.onNodeWithTag("library_filters").performTouchInput { swipeLeft() }
      }
      compose.onNodeWithTag("library_filter_$filter").performClick().assertIsSelected()
    }
  }

  private fun episode(id: String = "failed-episode") =
      EpisodeEntity(
          id = id,
          podcastId = "podcast",
          stableKey = "stable-$id",
          title = "Episode $id",
          description = "Description",
          audioUrl = "https://example.com/audio.mp3",
          publishedEpochMs = 1L,
          durationMs = 1_000L,
          artworkUrl = null,
      )
}
