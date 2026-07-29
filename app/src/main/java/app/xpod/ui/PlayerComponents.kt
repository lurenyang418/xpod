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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.playback.NowPlaying
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
internal fun MiniPlayer(
    nowPlaying: NowPlaying,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
        Artwork(
            nowPlaying.item.artworkUri,
            null,
            Modifier.size(40.dp),
            nowPlaying.item.mediaType,
        )
        Text(
            nowPlaying.item.title,
            Modifier.weight(1f).padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
        if (nowPlaying.item.mediaType == PlaybackMediaType.Podcast) {
          IconButton(onClick = onShowSpeedPicker) {
            Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelMedium)
          }
        } else {
          IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous_track))
          }
        }
        IconButton(onClick = onToggle) {
          Icon(
              if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
          )
        }
        if (nowPlaying.item.mediaType == PlaybackMediaType.Music) {
          IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.next_track))
          }
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    onOpenPodcast: () -> Unit,
) {
  val duration = knownDuration(nowPlaying.durationMs)
  var scrubPosition by
      remember(nowPlaying.item.id) { mutableFloatStateOf(nowPlaying.positionMs.toFloat()) }
  var isScrubbing by remember(nowPlaying.item.id) { mutableStateOf(false) }
  LaunchedEffect(nowPlaying.positionMs, nowPlaying.durationMs) {
    if (!isScrubbing) {
      scrubPosition =
          duration?.let { nowPlaying.positionMs.coerceIn(0L, it) }?.toFloat()
              ?: nowPlaying.positionMs.coerceAtLeast(0L).toFloat()
    }
  }
  Column(
      Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.weight(1f))
    Artwork(
        nowPlaying.item.artworkUri ?: podcast?.artworkUrl,
        null,
        Modifier.size(200.dp),
        nowPlaying.item.mediaType,
    )
    Spacer(Modifier.height(36.dp))
    Text(
        nowPlaying.item.title,
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
    if (nowPlaying.item.subtitle.isNotBlank()) {
      Text(
          nowPlaying.item.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.weight(1f))
    if (duration != null) {
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
        Text(mediaTimeLabel(scrubPosition.toLong()), style = MaterialTheme.typography.labelMedium)
        Text(mediaTimeLabel(duration), style = MaterialTheme.typography.labelMedium)
      }
    } else {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(mediaTimeLabel(nowPlaying.positionMs), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(R.string.unknown_duration),
            style = MaterialTheme.typography.labelMedium,
        )
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      if (nowPlaying.item.mediaType == PlaybackMediaType.Podcast) {
        IconButton(onClick = onShowSpeedPicker) {
          Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onSkipBack) {
          Icon(Icons.Filled.Replay10, stringResource(R.string.back_10_seconds))
        }
      } else {
        IconButton(onClick = onPrevious) {
          Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous_track))
        }
      }
      FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
        Icon(
            if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
            Modifier.size(32.dp),
        )
      }
      if (nowPlaying.item.mediaType == PlaybackMediaType.Podcast) {
        IconButton(onClick = onSkipForward) {
          Icon(Icons.Filled.Forward30, stringResource(R.string.forward_30_seconds))
        }
      } else {
        IconButton(onClick = onNext) {
          Icon(Icons.Filled.SkipNext, stringResource(R.string.next_track))
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
internal fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    mediaType: PlaybackMediaType = PlaybackMediaType.Podcast,
) =
    Box(modifier, contentAlignment = Alignment.Center) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.secondaryContainer) {}
      Icon(
          if (mediaType == PlaybackMediaType.Music) Icons.Filled.MusicNote
          else Icons.Filled.RssFeed,
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

internal fun knownDuration(durationMs: Long): Long? = durationMs.takeIf { it > 0L }

internal fun mediaTimeLabel(milliseconds: Long): String {
  val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
  return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
