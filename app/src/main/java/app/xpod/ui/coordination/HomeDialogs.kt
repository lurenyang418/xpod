package app.xpod.ui.coordination

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.xpod.R
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity

@Composable
internal fun BulkMarkDialog(
    request: BulkMarkRequest?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  request ?: return
  val isPodcast = request is BulkMarkRequest.Podcast
  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
            stringResource(
                if (isPodcast) R.string.mark_all_episodes_played_title
                else R.string.mark_all_articles_read_title
            )
        )
      },
      text = {
        Text(
            when (request) {
              is BulkMarkRequest.Podcast ->
                  pluralStringResource(
                      R.plurals.mark_all_episodes_played_message,
                      request.count,
                      request.count,
                      request.podcastTitle,
                  )
              is BulkMarkRequest.Articles ->
                  if (request.feedTitle == null) {
                    pluralStringResource(
                        R.plurals.mark_all_articles_read_message,
                        request.count,
                        request.count,
                    )
                  } else {
                    pluralStringResource(
                        R.plurals.mark_feed_read_message,
                        request.count,
                        request.count,
                        request.feedTitle,
                    )
                  }
            }
        )
      },
      confirmButton = {
        TextButton(onClick = onConfirm, enabled = !isBusy) {
          Text(
              stringResource(
                  if (isPodcast) R.string.mark_all_episodes_played else R.string.mark_as_read
              )
          )
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss, enabled = !isBusy) {
          Text(stringResource(R.string.cancel))
        }
      },
  )
}

@Composable
internal fun HomeDialogs(
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
    onClearQueue: () -> Unit,
) {
  podcastToDelete?.let { podcast ->
    AlertDialog(
        onDismissRequest = onDismissPodcastDelete,
        title = { Text(stringResource(R.string.remove_subscription_title)) },
        text = { Text(stringResource(R.string.remove_subscription_message)) },
        confirmButton = {
          TextButton(onClick = { onRemovePodcast(podcast.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissPodcastDelete) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
  articleFeedToDelete?.let { feed ->
    AlertDialog(
        onDismissRequest = onDismissArticleFeedDelete,
        title = { Text(stringResource(R.string.remove_subscription_title)) },
        text = { Text(stringResource(R.string.remove_article_subscription_message)) },
        confirmButton = {
          TextButton(onClick = { onRemoveArticleFeed(feed.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissArticleFeedDelete) {
            Text(stringResource(R.string.cancel))
          }
        },
    )
  }
  downloadToRemove?.let { episode ->
    AlertDialog(
        onDismissRequest = onDismissDownloadRemove,
        title = { Text(stringResource(R.string.remove_download_title)) },
        text = { Text(stringResource(R.string.remove_download_message, episode.title)) },
        confirmButton = {
          TextButton(onClick = { onRemoveDownload(episode.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissDownloadRemove) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
  if (confirmClearQueue) {
    AlertDialog(
        onDismissRequest = onDismissClearQueue,
        title = { Text(stringResource(R.string.clear_queue_title)) },
        text = { Text(stringResource(R.string.clear_queue_message)) },
        confirmButton = {
          TextButton(onClick = onClearQueue) { Text(stringResource(R.string.clear_queue)) }
        },
        dismissButton = {
          TextButton(onClick = onDismissClearQueue) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
}
