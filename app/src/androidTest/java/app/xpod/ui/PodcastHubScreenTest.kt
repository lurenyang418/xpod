package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.R
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.ui.navigation.PodcastSubView
import app.xpod.ui.podcasts.PodcastHubActions
import app.xpod.ui.podcasts.PodcastHubScreen
import app.xpod.ui.shell.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastHubScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun subscriptionsAndLibraryShareThePodcastsDestination() {
    compose.setContent {
      MaterialTheme {
        var subView by remember { mutableStateOf(PodcastSubView.Subscriptions) }
        PodcastHubScreen(
            state = MainUiState(),
            wide = false,
            selectedPodcastId = null,
            episodes = emptyList(),
            subView = subView,
            onSubViewSelected = { subView = it },
            nowPlaying = null,
            downloadStates = emptyMap(),
            bulkActionBusy = false,
            actions = noOpActions(),
        )
      }
    }

    compose.onNodeWithTag("podcast_subtab_Subscriptions").assertIsDisplayed()
    compose.onNodeWithTag("podcast_subtab_Library").assertIsDisplayed()
    compose.onNodeWithTag("podcast_subtab_Library").performClick()
    compose.onNodeWithTag("library_filter_Downloaded").assertIsDisplayed()
  }

  @Test
  fun subscriptionCardUsesOpenPodcastAction() {
    var openedPodcastId: String? = null
    val podcast =
        PodcastEntity(
            id = "podcast",
            feedUrl = "https://example.com/feed.xml",
            title = "Example Podcast",
            author = "Author",
            description = "Description",
            artworkUrl = null,
        )

    compose.setContent {
      MaterialTheme {
        PodcastHubScreen(
            state = MainUiState(podcasts = listOf(podcast)),
            wide = false,
            selectedPodcastId = null,
            episodes = emptyList(),
            subView = PodcastSubView.Subscriptions,
            onSubViewSelected = {},
            nowPlaying = null,
            downloadStates = emptyMap(),
            bulkActionBusy = false,
            actions = noOpActions(openPodcast = { openedPodcastId = it }),
        )
      }
    }

    compose.onNodeWithText("Example Podcast").performClick()
    compose.runOnIdle { assertEquals("podcast", openedPodcastId) }
  }

  @Test
  fun wideSubscriptionsRenderPodcastAndEpisodeColumns() {
    val podcast = podcast()
    val episode = episode()

    compose.setContent {
      MaterialTheme {
        PodcastHubScreen(
            state = MainUiState(podcasts = listOf(podcast)),
            wide = true,
            selectedPodcastId = podcast.id,
            episodes = listOf(episode),
            subView = PodcastSubView.Subscriptions,
            onSubViewSelected = {},
            nowPlaying = null,
            downloadStates = emptyMap(),
            bulkActionBusy = false,
            actions = noOpActions(),
        )
      }
    }

    compose.onNodeWithText(podcast.title).assertIsDisplayed()
    compose.onNodeWithText(episode.title).assertIsDisplayed()
  }

  @Test
  fun libraryEpisodeUsesOpenEpisodeAction() {
    var openedEpisodeId: String? = null
    val episode = episode("library-episode")

    compose.setContent {
      MaterialTheme {
        PodcastHubScreen(
            state = MainUiState(libraryEpisodes = listOf(episode)),
            wide = false,
            selectedPodcastId = null,
            episodes = emptyList(),
            subView = PodcastSubView.Library,
            onSubViewSelected = {},
            nowPlaying = null,
            downloadStates = mapOf(episode.id to DownloadState(progress = 1f, isCompleted = true)),
            bulkActionBusy = false,
            actions = noOpActions(openEpisode = { openedEpisodeId = it.id }),
        )
      }
    }

    compose.onNodeWithText(episode.title).performClick()
    compose.runOnIdle { assertEquals(episode.id, openedEpisodeId) }
  }

  @Test
  fun loadingEpisodesShowsAnAccessibleProgressIndicator() {
    val podcast = podcast()
    val loadingLabel =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.loading_episodes)
    compose.mainClock.autoAdvance = false

    compose.setContent {
      MaterialTheme {
        PodcastHubScreen(
            state = MainUiState(podcasts = listOf(podcast)),
            wide = false,
            selectedPodcastId = podcast.id,
            episodes = emptyList(),
            subView = PodcastSubView.Subscriptions,
            onSubViewSelected = {},
            nowPlaying = null,
            downloadStates = emptyMap(),
            bulkActionBusy = false,
            actions = noOpActions(),
            episodesLoading = true,
        )
      }
    }

    compose.mainClock.advanceTimeBy(16L)
    compose.onNodeWithContentDescription(loadingLabel).assertIsDisplayed()
  }

  private fun noOpActions(
      openPodcast: (String) -> Unit = {},
      openEpisode: (EpisodeEntity) -> Unit = {},
  ) =
      PodcastHubActions(
          openPodcast = openPodcast,
          refresh = {},
          refreshAll = {},
          play = {},
          download = {},
          requestRemoveFailedDownload = {},
          favorite = {},
          played = { _, _ -> },
          openEpisode = openEpisode,
          togglePlayback = {},
          addToQueue = {},
          showQueue = {},
          delete = {},
          requestMarkAllPlayed = {},
          openSettings = {},
      )

  private fun podcast() =
      PodcastEntity(
          id = "podcast",
          feedUrl = "https://example.com/feed.xml",
          title = "Example Podcast",
          author = "Author",
          description = "Description",
          artworkUrl = null,
      )

  private fun episode(id: String = "episode") =
      EpisodeEntity(
          id = id,
          podcastId = "podcast",
          stableKey = id,
          title = "Example Episode $id",
          description = "Description",
          audioUrl = "https://example.com/audio.mp3",
          publishedEpochMs = 1L,
          durationMs = 1_000L,
          artworkUrl = null,
      )
}
