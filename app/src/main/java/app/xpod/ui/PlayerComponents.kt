package app.xpod.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.PodcastEntity
import app.xpod.playback.NowPlaying
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
internal fun MiniPlayer(
    nowPlaying: NowPlaying,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onShowSpeedPicker: () -> Unit,
) =
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
      Row(
          Modifier.fillMaxWidth()
              .heightIn(min = 64.dp)
              .clickable(onClick = onOpen)
              .padding(start = 16.dp, end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Artwork(nowPlaying.episode.artworkUrl, null, Modifier.size(40.dp))
        Text(
            nowPlaying.episode.title,
            Modifier.weight(1f).padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
        IconButton(onClick = onShowSpeedPicker) {
          Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onToggle) {
          Icon(
              if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
          )
        }
      }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPicker(selected: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) =
    ModalBottomSheet(onDismissRequest = onDismiss) {
      Column(
          Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
            stringResource(R.string.playback_speed),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
            FilterChip(
                selected = selected == speed,
                onClick = { onSelect(speed) },
                label = { Text(speedLabel(speed)) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(1.75f, 2f).forEach { speed ->
            FilterChip(
                selected = selected == speed,
                onClick = { onSelect(speed) },
                label = { Text(speedLabel(speed)) },
            )
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }

@Composable
internal fun FullPlayerScreen(
    nowPlaying: NowPlaying,
    podcast: PodcastEntity?,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    onOpenPodcast: () -> Unit,
) {
  val duration = nowPlaying.durationMs.coerceAtLeast(1L)
  var scrubPosition by
      remember(nowPlaying.episode.id) { mutableFloatStateOf(nowPlaying.positionMs.toFloat()) }
  var isScrubbing by remember(nowPlaying.episode.id) { mutableStateOf(false) }
  LaunchedEffect(nowPlaying.positionMs, nowPlaying.durationMs) {
    if (!isScrubbing) scrubPosition = nowPlaying.positionMs.coerceIn(0L, duration).toFloat()
  }
  Column(
      Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.weight(1f))
    Artwork(nowPlaying.episode.artworkUrl ?: podcast?.artworkUrl, null, Modifier.size(200.dp))
    Spacer(Modifier.height(36.dp))
    Text(
        nowPlaying.episode.title,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(12.dp))
    podcast?.let {
      Text(
          it.title,
          Modifier.clickable(onClick = onOpenPodcast),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        nowPlaying.episode.description,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.weight(1f))
    Slider(
        value = scrubPosition.coerceIn(0f, duration.toFloat()),
        onValueChange = {
          isScrubbing = true
          scrubPosition = it
        },
        onValueChangeFinished = {
          onSeek(scrubPosition.toLong())
          isScrubbing = false
        },
        valueRange = 0f..duration.toFloat(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(timeLabel(scrubPosition.toLong()), style = MaterialTheme.typography.labelMedium)
      Text(timeLabel(nowPlaying.durationMs), style = MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.height(16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      IconButton(onClick = onShowSpeedPicker) {
        Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelLarge)
      }
      IconButton(onClick = onSkipBack) {
        Icon(Icons.Filled.Replay10, stringResource(R.string.back_10_seconds))
      }
      FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
        Icon(
            if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
            Modifier.size(32.dp),
        )
      }
      IconButton(onClick = onSkipForward) {
        Icon(Icons.Filled.Forward30, stringResource(R.string.forward_30_seconds))
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
internal fun Artwork(url: String?, contentDescription: String?, modifier: Modifier) =
    Box(modifier, contentAlignment = Alignment.Center) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.secondaryContainer) {}
      Icon(
          Icons.Filled.RssFeed,
          null,
          Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
      }
    }

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) String.format(Locale.US, "%.0fx", speed)
    else String.format(Locale.US, "%.2gx", speed)

private fun timeLabel(milliseconds: Long): String {
  val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
  return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
