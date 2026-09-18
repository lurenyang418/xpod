package app.xpod.ui.coordination

import android.content.Context
import app.xpod.R
import app.xpod.data.DownloadPhase
import app.xpod.data.DownloadRepository
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.ui.shared.StatusSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class DownloadAction {
  Enqueue,
  Retry,
  Remove,
  ShowInProgress,
}

internal fun downloadAction(state: DownloadState?): DownloadAction =
    when {
      state == null -> DownloadAction.Enqueue
      state.isCompleted -> DownloadAction.Remove
      state.phase == DownloadPhase.Failed -> DownloadAction.Retry
      else -> DownloadAction.ShowInProgress
    }

/** Owns download commands and exposes the repository's live download state. */
internal class DownloadActionsController(
    private val downloads: DownloadRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showStatus: (String, StatusSeverity) -> Unit,
) {
  val states: StateFlow<Map<String, DownloadState>> = downloads.states

  fun download(episode: EpisodeEntity) = scope.launch {
    when (downloadAction(downloads.states.value[episode.id])) {
      DownloadAction.Enqueue ->
          downloads
              .enqueue(episode)
              .fold(
                  { showStatus(context.getString(R.string.download_queued), StatusSeverity.Info) },
                  { showStatus(context.getString(R.string.could_not_download), StatusSeverity.Error) },
              )
      DownloadAction.Remove -> {
        downloads.remove(episode.id)
        showStatus(context.getString(R.string.download_removed), StatusSeverity.Info)
      }
      DownloadAction.Retry ->
          downloads
              .retry(episode)
              .fold(
                  {
                    showStatus(
                        context.getString(R.string.download_queued),
                        StatusSeverity.Info,
                    )
                  },
                  {
                    showStatus(
                        context.getString(R.string.could_not_download),
                        StatusSeverity.Error,
                    )
                  },
              )
      DownloadAction.ShowInProgress ->
          showStatus(
              context.getString(R.string.download_in_progress),
              StatusSeverity.Info,
          )
    }
  }

  fun remove(episodeId: String) {
    downloads.remove(episodeId)
    showStatus(context.getString(R.string.download_removed), StatusSeverity.Info)
  }
}
