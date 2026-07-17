package app.xpod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.EpisodeEntity
import app.xpod.playback.PlaybackQueue
import app.xpod.playback.PlaybackStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: PlaybackQueue,
    playbackStatus: PlaybackStatus?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onOpenEpisode: (EpisodeEntity) -> Unit,
    onPlay: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
) =
    ModalBottomSheet(onDismissRequest = onDismiss) {
      Column(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
              stringResource(R.string.queue),
              Modifier.weight(1f),
              style = MaterialTheme.typography.titleLarge,
          )
          IconButton(onClick = onClear, enabled = queue.episodes.isNotEmpty()) {
            Icon(Icons.Filled.Delete, stringResource(R.string.clear_queue))
          }
        }
        LazyColumn(
            Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          itemsIndexed(queue.episodes, key = { _, episode -> episode.id }) { index, episode ->
            val active = episode.id == queue.currentEpisodeId
            val activeStatus = playbackStatus.takeIf { active } ?: PlaybackStatus.Paused
            Row(
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Artwork(episode.artworkUrl, null, Modifier.size(44.dp))
              Column(
                  Modifier.weight(1f)
                      .clickable { onOpenEpisode(episode) }
                      .padding(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text(
                    episode.title,
                    color =
                        if (active) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                  Text(
                      stringResource(
                          when (activeStatus) {
                            PlaybackStatus.Playing -> R.string.now_playing
                            PlaybackStatus.Paused -> R.string.playback_paused
                            PlaybackStatus.Buffering -> R.string.playback_buffering
                            PlaybackStatus.Ended -> R.string.playback_finished
                            PlaybackStatus.Error -> R.string.playback_error
                          }
                      ),
                      color = MaterialTheme.colorScheme.primary,
                      style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
              IconButton(onClick = { if (active) onTogglePlayback() else onPlay(episode.id) }) {
                val playing = active && activeStatus.showsPauseAction
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play),
                )
              }
              var menuExpanded by remember { mutableStateOf(false) }
              Box {
                IconButton(onClick = { menuExpanded = true }) {
                  Icon(Icons.Filled.MoreVert, stringResource(R.string.queue))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.move_up)) },
                      onClick = {
                        onMove(index, index - 1)
                        menuExpanded = false
                      },
                      enabled =
                          index > 0 &&
                              !active &&
                              queue.episodes[index - 1].id != queue.currentEpisodeId,
                  )
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.move_down)) },
                      onClick = {
                        onMove(index, index + 1)
                        menuExpanded = false
                      },
                      enabled = index < queue.episodes.lastIndex && !active,
                  )
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.remove_from_queue)) },
                      onClick = {
                        onRemove(episode.id)
                        menuExpanded = false
                      },
                  )
                }
              }
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }
    }
