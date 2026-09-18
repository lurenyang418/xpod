package app.xpod.ui.podcasts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.xpod.R
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.playback.NowPlaying
import app.xpod.ui.navigation.PodcastSubView
import app.xpod.ui.shell.MainUiState

internal data class PodcastHubActions(
    val openPodcast: (String) -> Unit,
    val refresh: (String) -> Unit,
    val refreshAll: () -> Unit,
    val play: (EpisodeEntity) -> Unit,
    val download: (EpisodeEntity) -> Unit,
    val requestRemoveFailedDownload: (EpisodeEntity) -> Unit,
    val favorite: (String) -> Unit,
    val played: (String, Boolean) -> Unit,
    val openEpisode: (EpisodeEntity) -> Unit,
    val togglePlayback: () -> Unit,
    val addToQueue: (EpisodeEntity) -> Unit,
    val showQueue: () -> Unit,
    val delete: (PodcastEntity) -> Unit,
    val requestMarkAllPlayed: (String) -> Unit,
    val openSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastHubScreen(
    state: MainUiState,
    wide: Boolean,
    selectedPodcastId: String?,
    episodes: List<EpisodeEntity>,
    subView: PodcastSubView,
    onSubViewSelected: (PodcastSubView) -> Unit,
    nowPlaying: NowPlaying?,
    downloadStates: Map<String, DownloadState>,
    bulkActionBusy: Boolean,
    actions: PodcastHubActions,
    episodesLoading: Boolean = false,
) {
  Column(Modifier.fillMaxSize().testTag("podcast_hub")) {
    SecondaryTabRow(selectedTabIndex = PodcastSubView.entries.indexOf(subView)) {
      PodcastSubView.entries.forEach { item ->
        Tab(
            selected = item == subView,
            onClick = { onSubViewSelected(item) },
            text = { Text(podcastSubViewLabel(item)) },
            modifier = Modifier.testTag("podcast_subtab_${item.name}"),
        )
      }
    }
    Box(Modifier.fillMaxWidth().weight(1f)) {
      when (subView) {
        PodcastSubView.Subscriptions ->
            SubscriptionScreen(
                state = state,
                wide = wide,
                selectedPodcastId = selectedPodcastId,
                episodes = episodes,
                episodesLoading = episodesLoading,
                select = actions.openPodcast,
                refresh = actions.refresh,
                refreshAll = actions.refreshAll,
                play = actions.play,
                download = actions.download,
                requestRemoveFailedDownload = actions.requestRemoveFailedDownload,
                favorite = actions.favorite,
                played = actions.played,
                nowPlaying = nowPlaying,
                downloadStates = downloadStates,
                openEpisode = actions.openEpisode,
                togglePlayback = actions.togglePlayback,
                addToQueue = actions.addToQueue,
                showQueue = actions.showQueue,
                delete = actions.delete,
                requestMarkAllPlayed = actions.requestMarkAllPlayed,
                bulkActionBusy = bulkActionBusy,
                openSettings = actions.openSettings,
            )
        PodcastSubView.Library ->
            LibraryScreen(
                state = state,
                play = actions.play,
                favorite = actions.favorite,
                download = actions.download,
                requestRemoveFailedDownload = actions.requestRemoveFailedDownload,
                played = actions.played,
                nowPlaying = nowPlaying,
                downloadStates = downloadStates,
                openEpisode = actions.openEpisode,
                togglePlayback = actions.togglePlayback,
                addToQueue = actions.addToQueue,
            )
      }
    }
  }
}

@Composable
private fun podcastSubViewLabel(subView: PodcastSubView): String =
    stringResource(
        when (subView) {
          PodcastSubView.Subscriptions -> R.string.subscriptions
          PodcastSubView.Library -> R.string.library
        }
    )
