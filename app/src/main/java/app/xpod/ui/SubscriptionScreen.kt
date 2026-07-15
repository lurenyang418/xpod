package app.xpod.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.DownloadPhase
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.playback.NowPlaying
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun SubscriptionScreen(
    state: MainUiState,
    wide: Boolean,
    select: (String?) -> Unit,
    refresh: (String) -> Unit,
    play: (EpisodeEntity) -> Unit,
    download: (EpisodeEntity) -> Unit,
    favorite: (String) -> Unit,
    played: (String, Boolean) -> Unit,
    nowPlaying: NowPlaying?,
    downloadStates: Map<String, DownloadState>,
    openEpisode: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    addToQueue: (EpisodeEntity) -> Unit,
    showQueue: () -> Unit,
    delete: (PodcastEntity) -> Unit,
    openSettings: () -> Unit
) {
  when {
    state.podcasts.isEmpty() -> EmptySubscriptions(openSettings, Modifier.fillMaxSize())
    wide ->
        Row(Modifier.fillMaxSize()) {
          PodcastList(
              state.podcasts,
              state.newEpisodeCounts,
              select,
              refresh,
              showQueue,
              delete,
              Modifier.weight(0.42f))
          EpisodeList(
              state.episodes,
              play,
              download,
              favorite,
              played,
              nowPlaying,
              downloadStates,
              openEpisode,
              togglePlayback,
              addToQueue,
              Modifier.weight(0.58f),
              true)
        }
    state.selectedPodcastId == null ->
        PodcastList(
            state.podcasts,
            state.newEpisodeCounts,
            select,
            refresh,
            showQueue,
            delete,
            Modifier.fillMaxSize())
    else ->
        EpisodeList(
            state.episodes,
            play,
            download,
            favorite,
            played,
            nowPlaying,
            downloadStates,
            openEpisode,
            togglePlayback,
            addToQueue,
            Modifier.fillMaxSize(),
            false)
  }
}

@Composable
private fun EmptySubscriptions(openSettings: () -> Unit, modifier: Modifier) =
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(
          Icons.Filled.RssFeed,
          null,
          Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(16.dp))
      Text(stringResource(R.string.no_subscriptions), style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      FilledIconButton(onClick = openSettings) {
        Icon(Icons.Filled.Settings, stringResource(R.string.open_settings))
      }
    }

@Composable
private fun PodcastList(
    items: List<PodcastEntity>,
    newEpisodeCounts: Map<String, Int>,
    select: (String?) -> Unit,
    refresh: (String) -> Unit,
    showQueue: () -> Unit,
    delete: (PodcastEntity) -> Unit,
    modifier: Modifier
) =
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
              stringResource(R.string.subscriptions),
              Modifier.weight(1f),
              style = MaterialTheme.typography.headlineSmall)
          IconButton(onClick = showQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
          }
        }
      }
      items(items, key = { it.id }) { podcast ->
        Card(
            onClick = { select(podcast.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
              Row(
                  Modifier.fillMaxWidth().padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    Artwork(podcast.artworkUrl, null, Modifier.size(56.dp))
                    Column(
                        Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                          Text(
                              podcast.title,
                              style = MaterialTheme.typography.titleMedium,
                              maxLines = 2,
                              overflow = TextOverflow.Ellipsis)
                          Text(
                              podcast.author.ifBlank { stringResource(R.string.unknown_author) },
                              style = MaterialTheme.typography.bodyMedium,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                          newEpisodeCounts[podcast.id]
                              ?.takeIf { it > 0 }
                              ?.let {
                                Text(
                                    stringResource(R.string.new_episodes_count, it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                              }
                        }
                    IconButton(onClick = { refresh(podcast.feedUrl) }) {
                      Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_feed))
                    }
                    IconButton(onClick = { delete(podcast) }) {
                      Icon(Icons.Filled.Delete, stringResource(R.string.remove_subscription))
                    }
                  }
            }
      }
    }

@Composable
internal fun EpisodeList(
    items: List<EpisodeEntity>,
    play: (EpisodeEntity) -> Unit,
    download: (EpisodeEntity) -> Unit,
    favorite: (String) -> Unit,
    played: (String, Boolean) -> Unit,
    nowPlaying: NowPlaying?,
    downloadStates: Map<String, DownloadState>,
    openEpisode: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    addToQueue: (EpisodeEntity) -> Unit,
    modifier: Modifier,
    showTitle: Boolean
) =
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (showTitle)
          item {
            Text(stringResource(R.string.episodes), style = MaterialTheme.typography.headlineSmall)
          }
      items(items, key = { it.id }) {
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

@Composable
internal fun EpisodeCard(
    episode: EpisodeEntity,
    play: (EpisodeEntity) -> Unit,
    download: (EpisodeEntity) -> Unit,
    favorite: (String) -> Unit,
    played: (String, Boolean) -> Unit,
    nowPlaying: NowPlaying?,
    downloadState: DownloadState?,
    openEpisode: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    addToQueue: (EpisodeEntity) -> Unit
) =
    Card(onClick = { openEpisode(episode) }, modifier = Modifier.fillMaxWidth()) {
      Column(
          Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
              Artwork(episode.artworkUrl, null, Modifier.size(52.dp))
              Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                episode.publishedEpochMs
                    .takeIf { it > 0L }
                    ?.let {
                      Text(formatPublishedAt(it), style = MaterialTheme.typography.bodySmall)
                    }
                if (episode.description.isNotBlank())
                    Text(episode.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (downloadState != null && !downloadState.isCompleted) {
                  if (downloadState.phase != DownloadPhase.Failed && downloadState.progress != null)
                      LinearProgressIndicator(
                          progress = { downloadState.progress },
                          modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                  else if (downloadState.phase != DownloadPhase.Failed)
                      LinearProgressIndicator(
                          modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                  Text(
                      when (downloadState.phase) {
                        DownloadPhase.WaitingForNetwork ->
                            stringResource(R.string.waiting_for_network)
                        DownloadPhase.Queued -> stringResource(R.string.waiting_for_download)
                        DownloadPhase.Downloading ->
                            stringResource(
                                R.string.downloaded_size,
                                Formatter.formatFileSize(
                                    LocalContext.current, downloadState.bytesDownloaded))
                        DownloadPhase.Failed -> stringResource(R.string.download_failed)
                      },
                      style = MaterialTheme.typography.bodySmall)
                }
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              val active = nowPlaying?.episode?.id == episode.id
              IconButton(onClick = { if (active) togglePlayback() else play(episode) }) {
                Icon(
                    if (active && nowPlaying.isPlaying) Icons.Filled.Pause
                    else Icons.Filled.PlayArrow,
                    stringResource(
                        if (active && nowPlaying.isPlaying) R.string.pause else R.string.play))
              }
              IconButton(onClick = { favorite(episode.id) }) {
                Icon(
                    if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    stringResource(
                        if (episode.isFavorite) R.string.remove_favorite else R.string.favorite))
              }
              IconButton(onClick = { played(episode.id, !episode.isPlayed) }) {
                Icon(
                    if (episode.isPlayed) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    stringResource(
                        if (episode.isPlayed) R.string.mark_unplayed else R.string.mark_played))
              }
              IconButton(onClick = { download(episode) }) {
                val failed = downloadState?.phase == DownloadPhase.Failed
                val icon =
                    if (downloadState?.isCompleted == true) Icons.Filled.CheckCircle
                    else if (failed) Icons.Filled.Error
                    else if (downloadState != null) Icons.Filled.CloudDownload
                    else Icons.Filled.Download
                val label =
                    if (downloadState?.isCompleted == true) R.string.remove_download
                    else if (failed) R.string.retry_download
                    else if (downloadState != null) R.string.download_in_progress
                    else R.string.download
                Icon(icon, stringResource(label))
              }
              IconButton(onClick = { addToQueue(episode) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.add_to_queue))
              }
            }
          }
    }

@Composable
internal fun EpisodeDetailScreen(
    episode: EpisodeEntity,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onTogglePlayback: () -> Unit,
    onFavorite: () -> Unit,
    onPlayed: () -> Unit,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) =
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          item { Text(episode.title, style = MaterialTheme.typography.headlineSmall) }
          episode.publishedEpochMs
              .takeIf { it > 0L }
              ?.let {
                item { Text(formatPublishedAt(it), style = MaterialTheme.typography.bodyMedium) }
              }
          item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledIconButton(onClick = { if (isPlaying) onTogglePlayback() else onPlay() }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (isPlaying) R.string.pause else R.string.play))
              }
              IconButton(onClick = onFavorite) {
                Icon(
                    if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    stringResource(
                        if (episode.isFavorite) R.string.remove_favorite else R.string.favorite))
              }
              IconButton(onClick = onPlayed) {
                Icon(
                    if (episode.isPlayed) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    stringResource(
                        if (episode.isPlayed) R.string.mark_unplayed else R.string.mark_played))
              }
              IconButton(onClick = onDownload) {
                val failed = downloadState?.phase == DownloadPhase.Failed
                val icon =
                    if (downloadState?.isCompleted == true) Icons.Filled.CheckCircle
                    else if (failed) Icons.Filled.Error
                    else if (downloadState != null) Icons.Filled.CloudDownload
                    else Icons.Filled.Download
                val label =
                    if (downloadState?.isCompleted == true) R.string.remove_download
                    else if (failed) R.string.retry_download
                    else if (downloadState != null) R.string.download_in_progress
                    else R.string.download
                Icon(icon, stringResource(label))
              }
              IconButton(onClick = onPlayNext) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.play_next))
              }
              IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.add_to_queue))
              }
            }
          }
          if (episode.description.isNotBlank())
              item { Text(episode.description, style = MaterialTheme.typography.bodyLarge) }
        }

private fun formatPublishedAt(publishedEpochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(publishedEpochMs))
