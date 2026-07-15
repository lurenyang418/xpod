package app.xpod.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.EpisodeEntity
import app.xpod.playback.PlaybackQueue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: PlaybackQueue,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onOpenEpisode: (EpisodeEntity) -> Unit,
    onPlay: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit
) =
    ModalBottomSheet(onDismissRequest = onDismiss) {
      Column(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(
                  stringResource(R.string.queue),
                  Modifier.weight(1f),
                  style = MaterialTheme.typography.titleLarge)
              IconButton(onClick = onClear, enabled = queue.episodes.isNotEmpty()) {
                Icon(Icons.Filled.Delete, stringResource(R.string.clear_queue))
              }
            }
            LazyColumn(
                Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  itemsIndexed(queue.episodes, key = { _, episode -> episode.id }) { index, episode
                    ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                      Artwork(episode.artworkUrl, null, Modifier.size(44.dp))
                      Text(
                          episode.title,
                          Modifier.weight(1f)
                              .clickable { onOpenEpisode(episode) }
                              .padding(horizontal = 12.dp),
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                      )
                      IconButton(onClick = { onPlay(episode.id) }) {
                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.play))
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
                              enabled = index > 0,
                          )
                          DropdownMenuItem(
                              text = { Text(stringResource(R.string.move_down)) },
                              onClick = {
                                onMove(index, index + 1)
                                menuExpanded = false
                              },
                              enabled = index < queue.episodes.lastIndex,
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
