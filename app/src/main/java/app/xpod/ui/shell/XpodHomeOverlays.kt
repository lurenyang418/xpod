package app.xpod.ui.shell

import androidx.compose.runtime.Composable
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.EpisodeEntity
import app.xpod.data.MusicPlaybackSettings
import app.xpod.data.PlaybackItem
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.playback.NowPlaying
import app.xpod.playback.PlaybackQueue
import app.xpod.ui.coordination.BulkMarkDialog
import app.xpod.ui.coordination.BulkMarkRequest
import app.xpod.ui.coordination.HomeDialogs
import app.xpod.ui.player.QueueSheet
import app.xpod.ui.player.SpeedPicker

@Composable
internal fun XpodHomeOverlays(
    nowPlaying: NowPlaying?,
    showSpeedPicker: Boolean,
    onSetPlaybackSpeed: (Float) -> Unit,
    onDismissSpeedPicker: () -> Unit,
    showQueue: Boolean,
    queue: PlaybackQueue,
    musicPlaybackSettings: MusicPlaybackSettings,
    onDismissQueue: () -> Unit,
    onClearQueue: () -> Unit,
    onOpenQueueItem: (PlaybackItem) -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    podcastToDelete: PodcastEntity?,
    articleFeedToDelete: ArticleFeedEntity?,
    downloadToRemove: EpisodeEntity?,
    confirmClearQueue: Boolean,
    onDismissPodcastDelete: () -> Unit,
    onRemovePodcast: (String) -> Unit,
    onDismissArticleFeedDelete: () -> Unit,
    onRemoveArticleFeed: (String) -> Unit,
    onDismissDownloadRemove: () -> Unit,
    onRemoveDownload: (String) -> Unit,
    onDismissClearQueue: () -> Unit,
    onClearQueueDialog: () -> Unit,
    bulkMarkRequest: BulkMarkRequest?,
    bulkActionBusy: Boolean,
    onConfirmBulkMark: () -> Unit,
    onDismissBulkMark: () -> Unit,
) {
  nowPlaying
      ?.takeIf { showSpeedPicker && it.item.mediaType == PlaybackMediaType.Podcast }
      ?.let { playing ->
        SpeedPicker(
            selected = playing.speed,
            onSelect = onSetPlaybackSpeed,
            onDismiss = onDismissSpeedPicker,
        )
      }

  if (showQueue) {
    QueueSheet(
        queue = queue,
        playbackStatus = nowPlaying?.takeIf { it.item.id == queue.currentMediaId }?.status,
        musicPlaybackSettings = musicPlaybackSettings,
        onDismiss = onDismissQueue,
        onClear = onClearQueue,
        onOpenItem = onOpenQueueItem,
        onPlay = onPlayQueueItem,
        onTogglePlayback = onTogglePlayback,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeatMode = onCycleRepeatMode,
        onMove = onMoveQueueItem,
        onRemove = onRemoveQueueItem,
    )
  }

  HomeDialogs(
      podcastToDelete = podcastToDelete,
      articleFeedToDelete = articleFeedToDelete,
      downloadToRemove = downloadToRemove,
      confirmClearQueue = confirmClearQueue,
      onDismissPodcastDelete = onDismissPodcastDelete,
      onRemovePodcast = onRemovePodcast,
      onDismissArticleFeedDelete = onDismissArticleFeedDelete,
      onRemoveArticleFeed = onRemoveArticleFeed,
      onDismissDownloadRemove = onDismissDownloadRemove,
      onRemoveDownload = onRemoveDownload,
      onDismissClearQueue = onDismissClearQueue,
      onClearQueue = onClearQueueDialog,
  )

  BulkMarkDialog(
      request = bulkMarkRequest,
      isBusy = bulkActionBusy,
      onConfirm = onConfirmBulkMark,
      onDismiss = onDismissBulkMark,
  )
}
