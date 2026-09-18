package app.xpod.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.EpisodeEntity
import app.xpod.ui.navigation.AppRoute
import app.xpod.ui.navigation.DestinationIcon
import app.xpod.ui.navigation.HomeBottomBar
import app.xpod.ui.navigation.destinationLabel
import app.xpod.ui.player.MiniPlaybackSummary
import app.xpod.ui.shared.StatusSeverity
import app.xpod.ui.shared.UiStatus

@Composable
internal fun XpodHomeScaffold(
    snackbar: SnackbarHostState,
    showTopBar: Boolean,
    fullPlayer: Boolean,
    selectedEpisode: EpisodeEntity?,
    selectedPodcastId: String?,
    selectedPodcastUnplayedCount: Int,
    bulkActionBusy: Boolean,
    onRequestPodcastMarkAllPlayed: (String) -> Unit,
    onBack: () -> Unit,
    onShowQueue: () -> Unit,
    showBottomBar: Boolean,
    summary: MiniPlaybackSummary?,
    destination: AppRoute,
    routes: List<AppRoute>,
    onDestinationSelected: (AppRoute) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    showNavigationRail: Boolean,
    immersiveContent: Boolean,
    saveableStateHolder: SaveableStateHolder,
    contentRouteId: String,
    content: @Composable () -> Unit,
) {
  Scaffold(
      topBar = {
        HomeTopBar(
            show = showTopBar,
            fullPlayer = fullPlayer,
            selectedEpisode = selectedEpisode,
            selectedPodcastId = selectedPodcastId,
            selectedPodcastUnplayedCount = selectedPodcastUnplayedCount,
            bulkActionBusy = bulkActionBusy,
            onRequestPodcastMarkAllPlayed = onRequestPodcastMarkAllPlayed,
            onBack = onBack,
            onShowQueue = onShowQueue,
        )
      },
      bottomBar = {
        HomeBottomBar(
            visible = showBottomBar,
            summary = summary,
            destination = destination,
            routes = routes,
            onDestinationSelected = onDestinationSelected,
            onToggle = onToggle,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenPlayer = onOpenPlayer,
            onShowSpeedPicker = onShowSpeedPicker,
        )
      },
      snackbarHost = { XpodSnackbarHost(snackbar) },
  ) { padding ->
    Box(if (immersiveContent) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(padding)) {
      Row(Modifier.fillMaxSize()) {
        if (showNavigationRail) {
          NavigationRail {
            routes.forEach { route ->
              NavigationRailItem(
                  selected = route == destination,
                  onClick = { onDestinationSelected(route) },
                  icon = { DestinationIcon(route) },
                  label = { Text(destinationLabel(route)) },
              )
            }
          }
        }
        // SaveableStateProvider (unlike a bare key()) restores each route's rememberSaveable
        // state and scroll positions when the user navigates back to it.
        Box(Modifier.weight(1f).fillMaxHeight()) {
          saveableStateHolder.SaveableStateProvider(contentRouteId) { content() }
        }
      }
    }
  }
}

@Composable
internal fun XpodSnackbarHost(snackbar: SnackbarHostState) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
      SnackbarHost(snackbar, Modifier.fillMaxWidth()) { data ->
        val isError = (data.visuals as? XpodSnackbarVisuals)?.severity == StatusSeverity.Error
        val containerColor =
            if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        val contentColor =
            if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface
        val actionColor =
            if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.primary
        Snackbar(
            containerColor = containerColor,
            contentColor = contentColor,
            actionContentColor = actionColor,
            dismissActionContentColor = contentColor,
            action =
                data.visuals.actionLabel?.let { label ->
                  {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                    ) {
                      Text(label)
                    }
                  }
                },
            dismissAction =
                if (data.visuals.withDismissAction) {
                  {
                    IconButton(onClick = data::dismiss) {
                      Icon(Icons.Filled.Close, stringResource(R.string.dismiss_snackbar))
                    }
                  }
                } else {
                  null
                },
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (isError) {
              Icon(
                  Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 8.dp),
              )
            }
            Text(data.visuals.message)
          }
        }
      }
    }
  }
}

private data class XpodSnackbarVisuals(
    override val message: String,
    val severity: StatusSeverity,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

internal suspend fun SnackbarHostState.showXpodSnackbar(
    message: String,
    severity: StatusSeverity = StatusSeverity.Info,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short,
): SnackbarResult =
    showSnackbar(
        XpodSnackbarVisuals(
            message = message,
            severity = severity,
            actionLabel = actionLabel,
            withDismissAction = withDismissAction,
            duration = duration,
        )
    )

internal suspend fun SnackbarHostState.showXpodSnackbar(status: UiStatus): SnackbarResult =
    showXpodSnackbar(status.message, status.severity)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    show: Boolean,
    fullPlayer: Boolean,
    selectedEpisode: EpisodeEntity?,
    selectedPodcastId: String?,
    selectedPodcastUnplayedCount: Int,
    bulkActionBusy: Boolean,
    onRequestPodcastMarkAllPlayed: (String) -> Unit,
    onBack: () -> Unit,
    onShowQueue: () -> Unit,
) {
  if (!show) return
  var podcastActionsExpanded by remember(selectedPodcastId) { mutableStateOf(false) }
  TopAppBar(
      title = {
        Text(
            if (fullPlayer) stringResource(R.string.now_playing)
            else if (selectedEpisode != null) stringResource(R.string.episode)
            else stringResource(R.string.episodes),
        )
      },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
      },
      actions = {
        if (fullPlayer) {
          IconButton(onClick = onShowQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
          }
        } else if (selectedEpisode == null && selectedPodcastId != null) {
          Box {
            IconButton(onClick = { podcastActionsExpanded = true }) {
              Icon(Icons.Filled.MoreVert, stringResource(R.string.subscription_actions))
            }
            DropdownMenu(
                expanded = podcastActionsExpanded,
                onDismissRequest = { podcastActionsExpanded = false },
            ) {
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.mark_all_episodes_played)) },
                  leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                  enabled = selectedPodcastUnplayedCount > 0 && !bulkActionBusy,
                  onClick = {
                    podcastActionsExpanded = false
                    onRequestPodcastMarkAllPlayed(selectedPodcastId)
                  },
              )
            }
          }
        }
      },
  )
}
