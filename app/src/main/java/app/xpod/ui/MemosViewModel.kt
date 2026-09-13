package app.xpod.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoState
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.CloudMemosConnection
import app.xpod.data.CloudMemosCredentialsException
import app.xpod.data.CloudMemosGateway
import app.xpod.data.CloudMemosHttpException
import app.xpod.data.CloudMemosNotConfiguredException
import app.xpod.data.CloudMemosProtocolException
import app.xpod.data.CloudMemosRecycleBinUnsupportedException
import app.xpod.data.InvalidCloudMemosTokenException
import app.xpod.data.InvalidCloudMemosUrlException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemosUiState(
    val items: List<CloudMemo> = emptyList(),
    val draft: String = "",
    val query: String = "",
    val appliedQuery: String = "",
    val selectedTag: String? = null,
    val appliedTag: String? = null,
    val knownTags: List<String> = emptyList(),
    val visibility: CloudMemoVisibility = CloudMemoVisibility.Private,
    val nextCursor: String? = null,
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isCreating: Boolean = false,
    val busyMemoIds: Set<String> = emptySet(),
    val pendingPrivateShareMemoId: String? = null,
    val pendingDeleteMemoId: String? = null,
    val archivedMemoForUndo: CloudMemo? = null,
    val archivedMemoUndoSequence: Long = 0,
    val error: String? = null,
)

@HiltViewModel
class MemosViewModel
@Inject
constructor(
    private val cloudMemos: CloudMemosGateway,
    private val memosStrings: MemosStrings,
) : ViewModel() {
  private val _memosState = MutableStateFlow(MemosUiState())
  val memosState: StateFlow<MemosUiState> = _memosState
  private val _status = MutableStateFlow<UiStatus?>(null)
  val status: StateFlow<UiStatus?> = _status
  val connection: StateFlow<CloudMemosConnection> =
      cloudMemos.connection.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          CloudMemosConnection(baseUrl = "", isConfigured = false),
      )
  private val _reloadToken = MutableStateFlow(0)
  val reloadToken: StateFlow<Int> = _reloadToken

  private var memosLoadJob: Job? = null
  private var memosCreateJob: Job? = null
  private val memosMutationJobs = mutableMapOf<String, Job>()
  private var memosAccountGeneration = 0L
  private var memosLoadGeneration = 0L

  init {
    // Account switch / connect / disconnect (done from Settings) invalidates this list domain.
    viewModelScope.launch {
      cloudMemos.connection.drop(1).collect { resetForAccountChange() }
    }
  }

  fun dismissStatus() {
    _status.value = null
  }

  fun setMemoDraft(value: String) {
    _memosState.value = _memosState.value.copy(draft = value.take(MAX_MEMO_CHARACTERS))
  }

  fun setMemoQuery(value: String) {
    _memosState.value = _memosState.value.copy(query = value.take(MAX_MEMO_QUERY_CHARACTERS))
  }

  fun selectMemoTag(value: String?) {
    val tag = value?.trim()?.take(MAX_MEMO_TAG_CHARACTERS)?.takeIf(String::isNotEmpty)
    if (_memosState.value.selectedTag == tag) return
    _memosState.value = _memosState.value.copy(selectedTag = tag)
    startMemosLoad(reset = true)
  }

  fun setMemoVisibility(value: CloudMemoVisibility) {
    _memosState.value = _memosState.value.copy(visibility = value)
  }

  fun requestPrivateMemoShare(memoId: String) {
    val current = _memosState.value
    if (
        current.items.none { memo ->
          memo.id == memoId && memo.visibility == CloudMemoVisibility.Private
        }
    ) {
      return
    }
    _memosState.value = current.copy(pendingPrivateShareMemoId = memoId, pendingDeleteMemoId = null)
  }

  fun dismissPrivateMemoShare() {
    if (_memosState.value.pendingPrivateShareMemoId == null) return
    _memosState.value = _memosState.value.copy(pendingPrivateShareMemoId = null)
  }

  fun requestMemoDelete(memoId: String) {
    val current = _memosState.value
    if (memoId in current.busyMemoIds || current.items.none { it.id == memoId }) return
    _memosState.value = current.copy(pendingDeleteMemoId = memoId, pendingPrivateShareMemoId = null)
  }

  fun dismissMemoDelete() {
    if (_memosState.value.pendingDeleteMemoId == null) return
    _memosState.value = _memosState.value.copy(pendingDeleteMemoId = null)
  }

  fun archiveMemo(memoId: String) {
    val current = _memosState.value
    val memo = current.items.firstOrNull { it.id == memoId } ?: return
    if (
        memo.state != CloudMemoState.Active ||
            memoId in current.busyMemoIds ||
            memosMutationJobs.containsKey(memoId)
    ) {
      return
    }
    _memosState.value =
        current.copy(
            busyMemoIds = current.busyMemoIds + memoId,
            pendingPrivateShareMemoId =
                current.pendingPrivateShareMemoId.takeUnless { it == memoId },
            pendingDeleteMemoId = current.pendingDeleteMemoId.takeUnless { it == memoId },
            error = null,
        )
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result =
              cloudMemos.updateMemoState(
                  memoId = memo.id,
                  version = memo.version,
                  state = CloudMemoState.Archived,
              )
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              { archivedMemo ->
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        items = latest.items.filterNot { it.id == memoId },
                        busyMemoIds = latest.busyMemoIds - memoId,
                        archivedMemoForUndo = archivedMemo,
                        archivedMemoUndoSequence = latest.archivedMemoUndoSequence + 1,
                    )
                startMemosLoad(reset = true)
              },
              { error ->
                handleMemoMutationFailure(memoId, error, R.string.cloud_memo_archive_failed_reason)
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun restoreArchivedMemo(memoId: String) {
    val current = _memosState.value
    val memo = current.archivedMemoForUndo?.takeIf { it.id == memoId } ?: return
    if (memoId in current.busyMemoIds || memosMutationJobs.containsKey(memoId)) return
    _memosState.value = current.copy(busyMemoIds = current.busyMemoIds + memoId, error = null)
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result =
              cloudMemos.updateMemoState(
                  memoId = memo.id,
                  version = memo.version,
                  state = CloudMemoState.Active,
              )
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              {
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        busyMemoIds = latest.busyMemoIds - memoId,
                        archivedMemoForUndo =
                            latest.archivedMemoForUndo?.takeUnless { it.id == memoId },
                    )
                _status.value = UiStatus(memosStrings.get(R.string.cloud_memo_archive_undone))
                startMemosLoad(reset = true)
              },
              { error ->
                val latest = _memosState.value
                val isVersionConflict =
                    error is CloudMemosHttpException && error.errorCode == "VERSION_CONFLICT"
                _memosState.value = latest.afterArchiveRestoreFailure(memoId, isVersionConflict)
                handleMemoMutationFailure(
                    memoId,
                    error,
                    R.string.cloud_memo_restore_failed_reason,
                )
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun dismissArchivedMemoUndo(memoId: String) {
    val current = _memosState.value
    if (current.archivedMemoForUndo?.id != memoId) return
    _memosState.value = current.copy(archivedMemoForUndo = null)
  }

  fun moveMemoToTrash(memoId: String) {
    val current = _memosState.value
    if (current.pendingDeleteMemoId != memoId) return
    if (
        current.items.none { it.id == memoId } ||
            memoId in current.busyMemoIds ||
            memosMutationJobs.containsKey(memoId)
    ) {
      return
    }
    _memosState.value =
        current.copy(
            busyMemoIds = current.busyMemoIds + memoId,
            pendingDeleteMemoId = null,
            error = null,
        )
    val accountGeneration = memosAccountGeneration
    val job =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          val result = cloudMemos.deleteMemo(memoId)
          memosMutationJobs.remove(memoId)
          if (accountGeneration != memosAccountGeneration) return@launch
          result.fold(
              {
                val latest = _memosState.value
                _memosState.value =
                    latest.copy(
                        items = latest.items.filterNot { it.id == memoId },
                        busyMemoIds = latest.busyMemoIds - memoId,
                        pendingPrivateShareMemoId =
                            latest.pendingPrivateShareMemoId.takeUnless { it == memoId },
                    )
                _status.value = UiStatus(memosStrings.get(R.string.cloud_memo_moved_to_trash))
                startMemosLoad(reset = true)
              },
              { error ->
                handleMemoMutationFailure(
                    memoId,
                    error,
                    R.string.cloud_memo_delete_failed_reason,
                )
              },
          )
        }
    memosMutationJobs[memoId] = job
    job.start()
  }

  fun loadMemos() {
    if (_memosState.value.hasLoaded) return
    refreshMemos()
  }

  fun refreshMemos() = startMemosLoad(reset = true)

  fun searchMemos() = startMemosLoad(reset = true)

  fun loadMoreMemos() {
    val current = _memosState.value
    if (current.isRefreshing || current.isLoadingMore || current.nextCursor == null) return
    startMemosLoad(reset = false)
  }

  fun createMemo() {
    val current = _memosState.value
    val content = current.draft.trim()
    if (content.isEmpty() || current.isCreating) return
    _memosState.value = current.copy(isCreating = true, error = null)
    val accountGeneration = memosAccountGeneration
    memosCreateJob = viewModelScope.launch {
      val result = cloudMemos.createMemo(content, current.visibility)
      if (accountGeneration != memosAccountGeneration) return@launch
      result.fold(
          {
            _memosState.value = _memosState.value.copy(draft = "", isCreating = false)
            _status.value = UiStatus(memosStrings.get(R.string.cloud_memos_saved))
            startMemosLoad(reset = true)
          },
          { error ->
            _memosState.value =
                _memosState.value.copy(
                    isCreating = false,
                    error =
                        memosStrings.get(
                            R.string.cloud_memos_save_failed_reason,
                            cloudMemosFailureReason(memosStrings, error),
                        ),
                )
          },
      )
    }
  }

  /** Account switched or disconnected from Settings: cancel work and drop all cached state. */
  private fun resetForAccountChange() {
    memosAccountGeneration++
    memosLoadGeneration++
    memosLoadJob?.cancel()
    memosLoadJob = null
    memosCreateJob?.cancel()
    memosCreateJob = null
    memosMutationJobs.values.forEach(Job::cancel)
    memosMutationJobs.clear()
    _memosState.value = MemosUiState()
    _status.value = null
    _reloadToken.value++
  }

  private fun startMemosLoad(reset: Boolean) {
    memosLoadJob?.cancel()
    val accountGeneration = memosAccountGeneration
    val loadGeneration = ++memosLoadGeneration
    _memosState.value = _memosState.value.copy(isRefreshing = false, isLoadingMore = false)
    memosLoadJob = viewModelScope.launch { fetchMemos(reset, accountGeneration, loadGeneration) }
  }

  private suspend fun fetchMemos(
      reset: Boolean,
      accountGeneration: Long,
      loadGeneration: Long,
  ) {
    val current = _memosState.value
    if (current.isRefreshing || current.isLoadingMore) return
    if (!reset && current.nextCursor == null) return
    _memosState.value =
        current.copy(
            isRefreshing = reset,
            isLoadingMore = !reset,
            error = null,
            nextCursor = if (reset) null else current.nextCursor,
        )
    val requestQuery = if (reset) current.query.trim() else current.appliedQuery
    val requestTag = if (reset) current.selectedTag else current.appliedTag
    val result =
        cloudMemos.listMemos(
            query = requestQuery.takeIf(String::isNotEmpty),
            tag = requestTag,
            cursor = if (reset) null else current.nextCursor,
        )
    if (accountGeneration != memosAccountGeneration || loadGeneration != memosLoadGeneration) {
      return
    }
    result.fold(
        { page ->
          _memosState.value =
              _memosState.value.copy(
                  items =
                      if (reset) page.items else (current.items + page.items).distinctBy { it.id },
                  appliedQuery = if (reset) requestQuery else current.appliedQuery,
                  appliedTag = if (reset) requestTag else current.appliedTag,
                  knownTags =
                      (_memosState.value.knownTags + page.items.flatMap { it.tags })
                          .distinctBy { it.lowercase(Locale.ROOT) }
                          .sortedBy { it.lowercase(Locale.ROOT) },
                  nextCursor = page.nextCursor,
                  hasLoaded = true,
                  isRefreshing = false,
                  isLoadingMore = false,
                  pendingPrivateShareMemoId =
                      if (reset) null else _memosState.value.pendingPrivateShareMemoId,
                  pendingDeleteMemoId = if (reset) null else _memosState.value.pendingDeleteMemoId,
              )
        },
        { error ->
          _memosState.value =
              _memosState.value.copy(
                  isRefreshing = false,
                  isLoadingMore = false,
                  nextCursor = current.nextCursor,
                  error =
                      memosStrings.get(
                          R.string.cloud_memos_load_failed_reason,
                          cloudMemosFailureReason(memosStrings, error),
                      ),
              )
        },
    )
  }

  private fun handleMemoMutationFailure(memoId: String, error: Throwable, messageRes: Int) {
    val latest = _memosState.value
    _memosState.value = latest.copy(busyMemoIds = latest.busyMemoIds - memoId)
    if (error is CloudMemosHttpException && error.errorCode == "VERSION_CONFLICT") {
      _status.value =
          UiStatus(
              memosStrings.get(R.string.cloud_memo_version_conflict),
              StatusSeverity.Error,
          )
      startMemosLoad(reset = true)
    } else {
      _memosState.value =
          _memosState.value.copy(
              error = memosStrings.get(messageRes, cloudMemosFailureReason(memosStrings, error))
          )
    }
  }

  private companion object {
    const val MAX_MEMO_CHARACTERS = 100_000
    const val MAX_MEMO_QUERY_CHARACTERS = 100
    const val MAX_MEMO_TAG_CHARACTERS = 80
  }
}

internal fun MemosUiState.afterArchiveRestoreFailure(
    memoId: String,
    isVersionConflict: Boolean,
): MemosUiState {
  val isCurrentUndo = archivedMemoForUndo?.id == memoId
  return copy(
      archivedMemoForUndo = if (isVersionConflict && isCurrentUndo) null else archivedMemoForUndo,
      archivedMemoUndoSequence =
          if (!isVersionConflict && isCurrentUndo) {
            archivedMemoUndoSequence + 1
          } else {
            archivedMemoUndoSequence
          },
  )
}

internal fun cloudMemosFailureReason(strings: MemosStrings, error: Throwable): String =
    when (error) {
      is InvalidCloudMemosUrlException -> strings.get(R.string.cloud_memos_error_https_required)
      is InvalidCloudMemosTokenException -> strings.get(R.string.cloud_memos_error_token)
      is CloudMemosNotConfiguredException -> strings.get(R.string.cloud_memos_error_not_configured)
      is CloudMemosCredentialsException -> strings.get(R.string.cloud_memos_error_credentials)
      is CloudMemosRecycleBinUnsupportedException ->
          strings.get(R.string.cloud_memos_error_recycle_bin_required)
      is CloudMemosHttpException ->
          when {
            error.statusCode == 401 || error.errorCode == "INVALID_API_TOKEN" ->
                strings.get(R.string.cloud_memos_error_unauthorized)
            error.errorCode == "INSUFFICIENT_SCOPE" -> strings.get(R.string.cloud_memos_error_scope)
            error.statusCode >= 500 ->
                strings.get(R.string.cloud_memos_error_server, error.statusCode)
            else -> strings.get(R.string.cloud_memos_error_http, error.statusCode)
          }
      is CloudMemosProtocolException -> strings.get(R.string.cloud_memos_error_response)
      is IOException -> strings.get(R.string.cloud_memos_error_network)
      else -> strings.get(R.string.cloud_memos_error_response)
    }
