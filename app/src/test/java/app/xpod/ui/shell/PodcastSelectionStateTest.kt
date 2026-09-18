package app.xpod.ui.shell

import app.xpod.data.EpisodeEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastSelectionStateTest {
  @Test
  fun `selection emits loading before the episode data`() = runTest {
    val selectedPodcastId = MutableStateFlow<String?>("podcast")
    val episodeUpdates = MutableSharedFlow<List<EpisodeEntity>>()
    val states = mutableListOf<PodcastSelectionUiState>()
    val collectJob =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          podcastSelectionFlow(selectedPodcastId) { episodeUpdates }.take(2).toList(states)
        }

    assertEquals(
        listOf(
            PodcastSelectionUiState(
                selectedPodcastId = "podcast",
                isLoading = true,
            )
        ),
        states,
    )

    val episode = episode()
    episodeUpdates.emit(listOf(episode))
    collectJob.join()

    assertEquals(
        listOf(
            PodcastSelectionUiState(
                selectedPodcastId = "podcast",
                isLoading = true,
            ),
            PodcastSelectionUiState(
                selectedPodcastId = "podcast",
                episodes = listOf(episode),
                isLoading = false,
            ),
        ),
        states,
    )
  }

  @Test
  fun `no selection emits an idle state without loading`() = runTest {
    val selectedPodcastId = MutableStateFlow<String?>(null)
    val requestedPodcastIds = mutableListOf<String>()
    val states =
        podcastSelectionFlow(selectedPodcastId) { id ->
              requestedPodcastIds += id
              MutableSharedFlow<List<EpisodeEntity>>()
            }
            .take(1)
            .toList()

    assertEquals(listOf(PodcastSelectionUiState()), states)
    assertTrue(requestedPodcastIds.isEmpty())
  }

  @Test
  fun `switching podcasts cancels the old episode stream`() = runTest {
    val selectedPodcastId = MutableStateFlow<String?>("a")
    val aUpdates = MutableSharedFlow<List<EpisodeEntity>>()
    val bUpdates = MutableSharedFlow<List<EpisodeEntity>>()
    val states = mutableListOf<PodcastSelectionUiState>()
    val collectJob =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          podcastSelectionFlow(selectedPodcastId) { id ->
                if (id == "a") aUpdates else bUpdates
              }
              .collect { states += it }
        }

    val aEpisode = episode("a-episode", "a")
    val bEpisode = episode("b-episode", "b")
    assertEquals(
        listOf(PodcastSelectionUiState(selectedPodcastId = "a", isLoading = true)),
        states,
    )

    aUpdates.emit(listOf(aEpisode))
    runCurrent()
    selectedPodcastId.value = "b"
    runCurrent()
    aUpdates.emit(listOf(episode("late-a-episode", "a")))
    runCurrent()
    bUpdates.emit(listOf(bEpisode))
    runCurrent()
    collectJob.cancel()

    assertEquals(
        listOf(
            PodcastSelectionUiState(selectedPodcastId = "a", isLoading = true),
            PodcastSelectionUiState(selectedPodcastId = "a", episodes = listOf(aEpisode)),
            PodcastSelectionUiState(selectedPodcastId = "b", isLoading = true),
            PodcastSelectionUiState(selectedPodcastId = "b", episodes = listOf(bEpisode)),
        ),
        states,
    )
  }

  private fun episode(id: String = "episode", podcastId: String = "podcast") =
      EpisodeEntity(
          id = id,
          podcastId = podcastId,
          stableKey = id,
          title = "Episode",
          description = "Description",
          audioUrl = "https://example.com/episode.mp3",
          publishedEpochMs = 1L,
          durationMs = null,
          artworkUrl = null,
      )
}
