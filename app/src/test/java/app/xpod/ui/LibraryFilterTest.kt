package app.xpod.ui

import app.xpod.data.DownloadPhase
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
  @Test
  fun `all library filters select the expected episodes`() {
    val downloaded = episode("downloaded")
    val task = episode("task")
    val continued = episode("continued").copy(lastPlayedEpochMs = 30L)
    val unplayed = episode("unplayed")
    val recent = episode("recent").copy(isPlayed = true, lastPlayedEpochMs = 20L)
    val favorite = episode("favorite").copy(isFavorite = true)
    val episodes = listOf(downloaded, task, continued, unplayed, recent, favorite)
    val downloadStates =
        mapOf(
            downloaded.id to DownloadState(progress = 1f, isCompleted = true),
            task.id to DownloadState(progress = 0.5f, phase = DownloadPhase.Downloading),
        )

    assertFilter(LibraryFilter.Downloaded, listOf("downloaded"), episodes, downloadStates)
    assertFilter(LibraryFilter.DownloadTasks, listOf("task"), episodes, downloadStates)
    assertFilter(LibraryFilter.ContinueListening, listOf("continued"), episodes, downloadStates)
    assertFilter(
        LibraryFilter.Unplayed,
        listOf("downloaded", "task", "continued", "unplayed", "favorite"),
        episodes,
        downloadStates,
    )
    assertFilter(LibraryFilter.Recent, listOf("recent"), episodes, downloadStates)
    assertFilter(LibraryFilter.Favorites, listOf("favorite"), episodes, downloadStates)
    assertFilter(
        LibraryFilter.All,
        episodes.map(EpisodeEntity::id),
        episodes,
        downloadStates,
    )
  }

  private fun assertFilter(
      filter: LibraryFilter,
      expectedIds: List<String>,
      episodes: List<EpisodeEntity>,
      downloadStates: Map<String, DownloadState>,
  ) {
    assertEquals(
        expectedIds,
        filterLibraryEpisodes(filter, episodes, downloadStates).map(EpisodeEntity::id),
    )
  }

  private fun episode(id: String) =
      EpisodeEntity(
          id = id,
          podcastId = "podcast",
          stableKey = id,
          title = "Episode $id",
          description = "Description",
          audioUrl = "https://example.com/$id.mp3",
          publishedEpochMs = 1L,
          durationMs = 1_000L,
          artworkUrl = null,
      )
}
