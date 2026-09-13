package app.xpod.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.xpod.R

@Composable
internal fun HomeBottomBar(
    visible: Boolean,
    summary: MiniPlaybackSummary?,
    destination: AppRoute,
    routes: List<AppRoute>,
    onDestinationSelected: (AppRoute) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowSpeedPicker: () -> Unit,
) {
  if (!visible) return
  Column {
    summary?.let {
      MiniPlayer(
          summary = it,
          onToggle = onToggle,
          onPrevious = onPrevious,
          onNext = onNext,
          onOpen = onOpenPlayer,
          onShowSpeedPicker = onShowSpeedPicker,
      )
    }
    NavigationBar {
      routes.forEach { route ->
        NavigationBarItem(
            selected = route == destination,
            onClick = { onDestinationSelected(route) },
            icon = { DestinationIcon(route) },
            label = { Text(destinationLabel(route)) },
        )
      }
    }
  }
}

@Composable
internal fun DestinationIcon(destination: AppRoute) =
    when (destination) {
      AppRoute.Podcasts -> Icon(Icons.Filled.RssFeed, null)
      AppRoute.Reader -> Icon(Icons.AutoMirrored.Filled.Article, null)
      AppRoute.Music -> Icon(Icons.Filled.MusicNote, null)
      AppRoute.Memos -> Icon(Icons.AutoMirrored.Filled.Notes, null)
      AppRoute.Books -> Icon(Icons.AutoMirrored.Filled.MenuBook, null)
      AppRoute.Settings -> Icon(Icons.Filled.Settings, null)
    }

internal fun destinationLabelResource(destination: AppRoute): Int =
    when (destination) {
      AppRoute.Podcasts -> R.string.podcasts
      AppRoute.Reader -> R.string.reader
      AppRoute.Music -> R.string.local_music
      AppRoute.Memos -> R.string.memos
      AppRoute.Books -> R.string.books
      AppRoute.Settings -> R.string.settings
    }

@Composable
internal fun destinationLabel(destination: AppRoute): String =
    stringResource(destinationLabelResource(destination))
