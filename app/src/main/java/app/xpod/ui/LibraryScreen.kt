package app.xpod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.playback.NowPlaying

private enum class LibraryFilter {
  All,
  ContinueListening,
  Recent,
  Unplayed,
  Favorites,
  DownloadTasks,
  Downloaded
}

@Composable
internal fun LibraryScreen(
    state: MainUiState,
    play: (EpisodeEntity) -> Unit,
    favorite: (String) -> Unit,
    download: (EpisodeEntity) -> Unit,
    played: (String, Boolean) -> Unit,
    nowPlaying: NowPlaying?,
    downloadStates: Map<String, DownloadState>,
    openEpisode: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    addToQueue: (EpisodeEntity) -> Unit
) {
  var filter by remember { mutableStateOf(LibraryFilter.All) }
  val episodes =
      when (filter) {
        LibraryFilter.ContinueListening ->
            state.libraryEpisodes
                .filter { !it.isPlayed && it.lastPlayedEpochMs > 0 }
                .sortedByDescending { it.lastPlayedEpochMs }
        LibraryFilter.Recent ->
            state.libraryEpisodes
                .filter { it.lastPlayedEpochMs > 0 }
                .sortedByDescending { it.lastPlayedEpochMs }
        LibraryFilter.Unplayed -> state.libraryEpisodes.filterNot { it.isPlayed }
        LibraryFilter.Favorites -> state.libraryEpisodes.filter { it.isFavorite }
        LibraryFilter.DownloadTasks ->
            state.libraryEpisodes.filter { downloadStates[it.id]?.isCompleted == false }
        LibraryFilter.Downloaded ->
            state.libraryEpisodes.filter { downloadStates[it.id]?.isCompleted == true }
        LibraryFilter.All -> state.libraryEpisodes
      }
  Column(Modifier.fillMaxSize().padding(12.dp)) {
    Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(LibraryFilter.entries) { item ->
        FilterChip(filter == item, { filter = item }, label = { Text(libraryFilterLabel(item)) })
      }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(episodes, key = { it.id }) {
        EpisodeCard(
            it,
            play,
            download,
            favorite,
            played,
            nowPlaying,
            downloadStates[it.id],
            openEpisode,
            togglePlayback,
            addToQueue)
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
        })
