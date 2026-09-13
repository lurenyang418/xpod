package app.xpod.ui

import android.content.Context
import android.util.Log
import app.xpod.R
import app.xpod.data.ArticlesReadChange
import app.xpod.data.ArticleEntity
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.data.PodcastPlayedChange
import app.xpod.data.PodcastRepository
import app.xpod.data.ReaderRepository
import app.xpod.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BulkActionsUiState(
    val pendingRequest: BulkMarkRequest? = null,
    val undoEvent: BulkUndoEvent? = null,
    val isBusy: Boolean = false,
)

sealed interface BulkMarkRequest {
  val count: Int

  data class Podcast(
      val podcastId: String,
      val podcastTitle: String,
      override val count: Int,
  ) : BulkMarkRequest

  data class Articles(
      val feedId: String?,
      val feedTitle: String?,
      override val count: Int,
  ) : BulkMarkRequest
}

enum class BulkMarkKind {
  PodcastEpisodes,
  Articles,
}

data class BulkUndoEvent(
    val id: Long,
    val kind: BulkMarkKind,
    val count: Int,
)

private sealed interface BulkUndoChange {
  val count: Int

  data class Podcast(val change: PodcastPlayedChange) : BulkUndoChange {
    override val count = change.markedPlayedCount
  }

  data class Articles(val change: ArticlesReadChange) : BulkUndoChange {
    override val count = change.articleIds.size
  }
}

private data class PendingBulkUndo(
    val eventId: Long,
    val change: BulkUndoChange,
)

internal class BulkMarkController(
    private val podcasts: PodcastRepository,
    private val reader: ReaderRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showStatus: (String, StatusSeverity) -> Unit,
) {
  private val _state = MutableStateFlow(BulkActionsUiState())
  val state: StateFlow<BulkActionsUiState> = _state

  private var pendingUndo: PendingBulkUndo? = null
  private var eventSequence = 0L

  fun requestPodcastMarkAllPlayed(
      podcast: PodcastEntity?,
      episodes: List<EpisodeEntity>,
  ) {
    if (_state.value.isBusy || podcast == null) return
    val count = unplayedEpisodeCount(episodes, podcast.id)
    if (count == 0) return
    _state.value =
        _state.value.copy(
            pendingRequest = BulkMarkRequest.Podcast(podcast.id, podcast.title, count)
        )
  }

  fun requestArticlesMarkAllRead(
      feedId: String?,
      feedTitle: String?,
      articles: List<ArticleEntity>,
  ) {
    if (_state.value.isBusy) return
    val count = unreadArticleCount(articles, feedId)
    if (count == 0) return
    _state.value =
        _state.value.copy(pendingRequest = BulkMarkRequest.Articles(feedId, feedTitle, count))
  }

  fun dismissRequest() {
    if (_state.value.isBusy) return
    _state.value = _state.value.copy(pendingRequest = null)
  }

  fun confirm() {
    val request = _state.value.pendingRequest ?: return
    if (_state.value.isBusy) return
    pendingUndo = null
    _state.value = _state.value.copy(pendingRequest = null, undoEvent = null, isBusy = true)
    scope.launch {
      runCatchingCancellable {
            when (request) {
              is BulkMarkRequest.Podcast ->
                  BulkUndoChange.Podcast(podcasts.markAllPlayed(request.podcastId))
              is BulkMarkRequest.Articles ->
                  BulkUndoChange.Articles(reader.markAllRead(request.feedId))
            }
          }
          .fold(
              { change ->
                if (change.count == 0) {
                  _state.value = _state.value.copy(isBusy = false, undoEvent = null)
                  return@fold
                }
                val event =
                    BulkUndoEvent(
                        id = ++eventSequence,
                        kind =
                            when (change) {
                              is BulkUndoChange.Podcast -> BulkMarkKind.PodcastEpisodes
                              is BulkUndoChange.Articles -> BulkMarkKind.Articles
                            },
                        count = change.count,
                    )
                pendingUndo = PendingBulkUndo(event.id, change)
                _state.value = _state.value.copy(isBusy = false, undoEvent = event)
              },
              { error ->
                Log.e("XPOD", "Unable to mark items in bulk", error)
                _state.value = _state.value.copy(isBusy = false, undoEvent = null)
                showStatus(context.getString(R.string.could_not_mark_all), StatusSeverity.Error)
              },
          )
    }
  }

  fun undo(eventId: Long) {
    if (_state.value.isBusy) return
    val pending = pendingUndo?.takeIf { it.eventId == eventId } ?: return
    _state.value = _state.value.copy(isBusy = true, undoEvent = null)
    scope.launch {
      runCatchingCancellable {
            when (val change = pending.change) {
              is BulkUndoChange.Podcast -> podcasts.restorePlayedChange(change.change)
              is BulkUndoChange.Articles -> reader.restoreReadChange(change.change)
            }
          }
          .fold(
              {
                pendingUndo = null
                _state.value = _state.value.copy(isBusy = false, undoEvent = null)
                showStatus(context.getString(R.string.bulk_mark_undone), StatusSeverity.Info)
              },
              { error ->
                Log.e("XPOD", "Unable to undo bulk status change", error)
                val retryEvent =
                    BulkUndoEvent(
                        id = ++eventSequence,
                        kind =
                            when (pending.change) {
                              is BulkUndoChange.Podcast -> BulkMarkKind.PodcastEpisodes
                              is BulkUndoChange.Articles -> BulkMarkKind.Articles
                            },
                        count = pending.change.count,
                    )
                pendingUndo = PendingBulkUndo(retryEvent.id, pending.change)
                _state.value = _state.value.copy(isBusy = false, undoEvent = retryEvent)
                showStatus(
                    context.getString(R.string.could_not_undo_bulk_mark),
                    StatusSeverity.Error,
                )
              },
          )
    }
  }

  fun dismissUndo(eventId: Long) {
    if (pendingUndo?.eventId != eventId) return
    pendingUndo = null
    _state.value = _state.value.copy(undoEvent = null)
  }
}

internal fun unplayedEpisodeCount(episodes: List<EpisodeEntity>, podcastId: String): Int =
    episodes.count { it.podcastId == podcastId && !it.isPlayed }

internal fun unreadArticleCount(
    articles: List<ArticleEntity>,
    feedId: String?,
): Int = articles.count { !it.isRead && (feedId == null || it.feedId == feedId) }
