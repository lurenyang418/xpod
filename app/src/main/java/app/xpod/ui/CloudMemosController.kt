package app.xpod.ui

import android.content.Context
import app.xpod.R
import app.xpod.data.ArticleEntity
import app.xpod.data.CloudMemoDrafts
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.CloudMemosRepository
import app.xpod.data.EpisodeEntity
import app.xpod.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloudMemosUiState(
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
    val isBusy: Boolean = false,
)

/** Coordinates Cloud Memos settings and saving app content without owning the memo list. */
internal class CloudMemosController(
    private val cloudMemos: CloudMemosRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val showStatus: (String, StatusSeverity) -> Unit,
) {
  private val busy = MutableStateFlow(false)

  val state: StateFlow<CloudMemosUiState> =
      combine(cloudMemos.connection, busy) { connection, isBusy ->
            CloudMemosUiState(
                baseUrl = connection.baseUrl,
                isConfigured = connection.isConfigured,
                isBusy = isBusy,
            )
          }
          .stateIn(scope, SharingStarted.WhileSubscribed(5_000), CloudMemosUiState())

  fun configure(baseUrl: String, token: String, onSuccess: () -> Unit = {}) = scope.launch {
    if (busy.value) return@launch
    busy.value = true
    try {
      runCatchingCancellable { cloudMemos.configure(baseUrl, token.ifBlank { null }) }
          .fold(
              {
                showStatus(context.getString(R.string.cloud_memos_connected), StatusSeverity.Info)
                onSuccess()
              },
              { error ->
                showStatus(
                    context.getString(
                        R.string.cloud_memos_connection_failed_reason,
                        failureReason(error),
                    ),
                    StatusSeverity.Error,
                )
              },
          )
    } finally {
      busy.value = false
    }
  }

  fun disconnect() = scope.launch {
    if (busy.value) return@launch
    busy.value = true
    try {
      cloudMemos.disconnect()
      showStatus(context.getString(R.string.cloud_memos_disconnected), StatusSeverity.Info)
    } finally {
      busy.value = false
    }
  }

  fun saveEpisode(episode: EpisodeEntity, podcastTitle: String?) =
      save(CloudMemoDrafts.episode(episode, podcastTitle))

  fun saveArticle(article: ArticleEntity, feedTitle: String?) =
      save(CloudMemoDrafts.article(article, feedTitle))

  private fun save(content: String) = scope.launch {
    if (busy.value) return@launch
    busy.value = true
    try {
      cloudMemos
          .createMemo(content, CloudMemoVisibility.Private)
          .fold(
              { showStatus(context.getString(R.string.cloud_memos_saved), StatusSeverity.Info) },
              { error ->
                showStatus(
                    context.getString(
                        R.string.cloud_memos_save_failed_reason,
                        failureReason(error),
                    ),
                    StatusSeverity.Error,
                )
              },
          )
    } finally {
      busy.value = false
    }
  }

  private fun failureReason(error: Throwable): String =
      cloudMemosFailureReason(
          MemosStrings { resId, formatArgs -> context.getString(resId, *formatArgs) },
          error,
      )
}
