package app.xpod.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.LocalTrackEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.playback.NowPlaying

@Composable
internal fun MusicScreen(
    state: MusicUiState,
    nowPlaying: NowPlaying?,
    chooseFolder: () -> Unit,
    refresh: () -> Unit,
    cancelScan: () -> Unit,
    setQuery: (String) -> Unit,
    play: (LocalTrackEntity) -> Unit,
    togglePlayback: () -> Unit,
    playNext: (LocalTrackEntity) -> Unit,
    addToQueue: (LocalTrackEntity) -> Unit,
) {
  if (state.selectedTreeUri == null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(Icons.Filled.FolderOpen, null, Modifier.size(56.dp))
      Text(
          stringResource(R.string.no_local_music_folder),
          Modifier.padding(top = 16.dp),
          style = MaterialTheme.typography.titleLarge,
      )
      Text(
          stringResource(R.string.local_music_folder_summary),
          Modifier.padding(top = 8.dp, bottom = 20.dp),
          style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = chooseFolder, enabled = !state.isScanning) {
        Text(stringResource(R.string.choose_music_folder))
      }
      if (state.isScanning) {
        CircularProgressIndicator(Modifier.padding(top = 20.dp))
        TextButton(onClick = cancelScan, modifier = Modifier.testTag("local_music_cancel_scan")) {
          Text(stringResource(R.string.cancel_local_music_scan))
        }
      }
    }
    return
  }

  Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          stringResource(R.string.local_music),
          Modifier.weight(1f),
          style = MaterialTheme.typography.headlineSmall,
      )
      if (state.isScanning) {
        CircularProgressIndicator(Modifier.size(24.dp))
        IconButton(onClick = cancelScan, modifier = Modifier.testTag("local_music_cancel_scan")) {
          Icon(Icons.Filled.Close, stringResource(R.string.cancel_local_music_scan))
        }
      } else {
        IconButton(onClick = refresh) {
          Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_local_music))
        }
      }
      IconButton(onClick = chooseFolder, enabled = !state.isScanning) {
        Icon(Icons.Filled.FolderOpen, stringResource(R.string.choose_music_folder))
      }
    }
    OutlinedTextField(
        value = state.query,
        onValueChange = setQuery,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        label = { Text(stringResource(R.string.search_local_music)) },
        singleLine = true,
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          pluralStringResource(
              R.plurals.local_track_count,
              state.visibleTracks.size,
              state.visibleTracks.size,
          ),
          Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
      )
      Button(
          onClick = { state.visibleTracks.firstOrNull()?.let(play) },
          enabled = state.visibleTracks.isNotEmpty() && !state.isScanning,
          modifier = Modifier.testTag("local_music_play_all"),
      ) {
        Icon(Icons.Filled.PlayArrow, null)
        Text(stringResource(R.string.play_all), Modifier.padding(start = 6.dp))
      }
    }
    if (state.visibleTracks.isEmpty()) {
      Text(
          stringResource(
              if (state.query.isBlank()) R.string.no_local_tracks else R.string.no_music_matches
          ),
          Modifier.padding(20.dp),
          style = MaterialTheme.typography.bodyLarge,
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.visibleTracks, key = LocalTrackEntity::id) { track ->
          val active = nowPlaying?.item?.id == track.id
          TrackRow(
              track = track,
              active = active,
              isPlaying = active && nowPlaying.isPlaying,
              enabled = !state.isScanning,
              onPlay = {
                if (active) togglePlayback() else play(track)
              },
              onPlayNext = { playNext(track) },
              onAddToQueue = { addToQueue(track) },
          )
        }
      }
    }
  }
}

@Composable
private fun TrackRow(
    track: LocalTrackEntity,
    active: Boolean,
    isPlaying: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
  Surface(
      color =
          if (active) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.surface,
      shape = MaterialTheme.shapes.medium,
  ) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Artwork(null, null, Modifier.size(44.dp), PlaybackMediaType.Music)
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            trackSubtitle(track),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
      }
      if (track.durationMs > 0L) {
        Text(
            mediaTimeLabel(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
        )
      }
      IconButton(onClick = onPlay, enabled = enabled) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (isPlaying) R.string.pause else R.string.play),
        )
      }
      var expanded by remember(track.id) { mutableStateOf(false) }
      IconButton(onClick = { expanded = true }, enabled = enabled) {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.local_track_actions))
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.play_next)) },
            leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
            onClick = {
              onPlayNext()
              expanded = false
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_to_queue)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
            onClick = {
              onAddToQueue()
              expanded = false
            },
        )
      }
    }
  }
}

@Composable
private fun trackSubtitle(track: LocalTrackEntity): String =
    listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ").ifBlank {
      stringResource(R.string.unknown_artist)
    }
