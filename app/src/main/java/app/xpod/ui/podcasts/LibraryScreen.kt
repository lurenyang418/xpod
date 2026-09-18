package app.xpod.ui.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.playback.NowPlaying
import app.xpod.ui.shell.MainUiState

internal enum class LibraryFilter {
  Downloaded,
  DownloadTasks,
  ContinueListening,
  Unplayed,
  Recent,
  Favorites,
  All,
}

internal fun filterLibraryEpisodes(
    filter: LibraryFilter,
    libraryEpisodes: List<EpisodeEntity>,
    downloadStates: Map<String, DownloadState>,
): List<EpisodeEntity> =
    when (filter) {
      LibraryFilter.ContinueListening ->
          libraryEpisodes
              .filter { !it.isPlayed && it.lastPlayedEpochMs > 0 }
              .sortedByDescending { it.lastPlayedEpochMs }
      LibraryFilter.Recent ->
          libraryEpisodes
              .filter { it.isPlayed && it.lastPlayedEpochMs > 0 }
              .sortedByDescending { it.lastPlayedEpochMs }
      LibraryFilter.Unplayed -> libraryEpisodes.filterNot { it.isPlayed }
      LibraryFilter.Favorites -> libraryEpisodes.filter { it.isFavorite }
      LibraryFilter.DownloadTasks ->
          libraryEpisodes.filter { downloadStates[it.id]?.isCompleted == false }
      LibraryFilter.Downloaded ->
          libraryEpisodes.filter { downloadStates[it.id]?.isCompleted == true }
      LibraryFilter.All -> libraryEpisodes
    }

@Composable
internal fun LibraryScreen(
    state: MainUiState,
    play: (EpisodeEntity) -> Unit,
    favorite: (String) -> Unit,
    download: (EpisodeEntity) -> Unit,
    requestRemoveFailedDownload: (EpisodeEntity) -> Unit,
    played: (String, Boolean) -> Unit,
    nowPlaying: NowPlaying?,
    downloadStates: Map<String, DownloadState>,
    openEpisode: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    addToQueue: (EpisodeEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
  var filter by remember { mutableStateOf(LibraryFilter.Downloaded) }
  val episodes =
      remember(filter, state.libraryEpisodes, downloadStates) {
        filterLibraryEpisodes(filter, state.libraryEpisodes, downloadStates)
      }
  Column(modifier.fillMaxSize().padding(12.dp)) {
    LazyRow(
        modifier = Modifier.testTag("library_filters"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      items(LibraryFilter.entries) { item ->
        FilterChip(
            selected = filter == item,
            onClick = { filter = item },
            label = { Text(libraryFilterLabel(item)) },
            modifier = Modifier.testTag("library_filter_${item.name}"),
        )
      }
    }
    if (episodes.isEmpty()) {
      Box(
          Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(stringResource(R.string.no_library_matches))
          if (filter != LibraryFilter.All) {
            TextButton(onClick = { filter = LibraryFilter.All }) {
              Text(stringResource(R.string.show_all))
            }
          }
        }
      }
    } else {
      LazyColumn(
          Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(episodes, key = { it.id }) {
          EpisodeCard(
              it,
              play,
              download,
              requestRemoveFailedDownload,
              favorite,
              played,
              nowPlaying,
              downloadStates[it.id],
              openEpisode,
              togglePlayback,
              addToQueue,
          )
        }
      }
    }
  }
}

@Composable
private fun libraryFilterLabel(filter: LibraryFilter): String =
    stringResource(
        when (filter) {
          LibraryFilter.All -> R.string.all
          LibraryFilter.ContinueListening -> R.string.continue_listening
          LibraryFilter.Recent -> R.string.recent
          LibraryFilter.Unplayed -> R.string.unplayed
          LibraryFilter.Favorites -> R.string.favorites
          LibraryFilter.DownloadTasks -> R.string.download_tasks
          LibraryFilter.Downloaded -> R.string.downloaded
        }
    )
