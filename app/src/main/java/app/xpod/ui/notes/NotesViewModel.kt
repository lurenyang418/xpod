package app.xpod.ui.notes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.BuildConfig
import app.xpod.R
import app.xpod.data.LocalMarkdownNoteEntity
import app.xpod.data.LocalMarkdownNotesRepository
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.SettingsRepository
import app.xpod.data.displayTitle
import app.xpod.ui.shared.StatusSeverity
import app.xpod.ui.shared.UiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NoteSort {
  Modified,
  Title,
  Created,
}

data class NotesUiState(
    val notes: List<LocalMarkdownNoteEntity> = emptyList(),
    val query: String = "",
    val sort: NoteSort = NoteSort.Modified,
    val theme: MarkdownThemeMode = MarkdownThemeMode.FollowApp,
    val isExporting: Boolean = false,
    val showBackupHint: Boolean = false,
    val status: UiStatus? = null,
)

data class NoteEditorUiState(
    val id: Long,
    val title: String,
    val content: String,
    val theme: MarkdownThemeMode,
    val isSaving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel
@Inject
constructor(
    private val repository: LocalMarkdownNotesRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val sort = MutableStateFlow(NoteSort.Modified)
  private val isExporting = MutableStateFlow(false)
  private val status = MutableStateFlow<UiStatus?>(null)
  private val showBackupHint = MutableStateFlow(false)
  private val editor = MutableStateFlow<NoteEditorUiState?>(null)
  private val saveMutex = Mutex()
  private var openNoteJob: Job? = null
  private var saveJob: Job? = null
  private var maxSaveJob: Job? = null

  private val notes: StateFlow<List<LocalMarkdownNoteEntity>> =
      query
          .flatMapLatest(repository::observe)
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  private val sortedNotes: StateFlow<List<LocalMarkdownNoteEntity>> =
      combine(notes, sort) { items, requestedSort ->
            items.sortedWith(
                noteComparator(requestedSort, context.getString(R.string.untitled_note))
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  private val theme: StateFlow<MarkdownThemeMode> =
      settings.markdownTheme.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          MarkdownThemeMode.FollowApp,
      )

  private val contentState: StateFlow<NotesUiState> =
      combine(sortedNotes, query, sort, theme, isExporting) {
              items,
              currentQuery,
              requestedSort,
              currentTheme,
              exporting ->
            NotesUiState(
                notes = items,
                query = currentQuery,
                sort = requestedSort,
                theme = currentTheme,
                isExporting = exporting,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())
  private val baseState: StateFlow<NotesUiState> =
      combine(contentState, status) { current, message -> current.copy(status = message) }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())
  val state: StateFlow<NotesUiState> =
      combine(baseState, showBackupHint) { current, backupHint ->
            current.copy(showBackupHint = backupHint)
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

  val editorState: StateFlow<NoteEditorUiState?> = editor.asStateFlow()

  fun setQuery(value: String) {
    query.value = value.take(MAX_QUERY_LENGTH)
  }

  fun setSort(value: NoteSort) {
    sort.value = value
  }

  fun createNote(onCreated: (Long) -> Unit) {
    viewModelScope.launch {
      var created = false
      runCatching { repository.create(clock.millis()) }
          .onSuccess {
            created = true
            onCreated(it)
          }
          .onFailure { showError(R.string.note_create_failed) }
      if (created && settings.notesBackupHintShown.first().not()) {
        settings.markNotesBackupHintShown()
        showBackupHint.value = true
      }
    }
  }

  fun openNote(id: Long, onNotFound: () -> Unit = {}) {
    openNoteJob?.cancel()
    openNoteJob = viewModelScope.launch {
      saveJob?.cancel()
      maxSaveJob?.cancel()
      persistEditor()
      val note = repository.find(id)
      if (note == null) {
        showError(R.string.note_not_found)
        onNotFound()
      } else {
        editor.value =
            NoteEditorUiState(
                id = note.id,
                title = note.title,
                content = note.content,
                theme = theme.value,
            )
      }
    }
  }

  fun closeEditor() {
    openNoteJob?.cancel()
    openNoteJob = null
    viewModelScope.launch {
      saveJob?.cancel()
      maxSaveJob?.cancel()
      persistEditor()
      editor.value = null
    }
  }

  fun setTitle(value: String) {
    editor.update { it?.copy(title = value.take(MAX_TITLE_LENGTH)) }
    scheduleSave()
  }

  fun setContent(value: String) {
    editor.update { it?.copy(content = value.take(MAX_CONTENT_LENGTH)) }
    scheduleSave()
  }

  fun setTheme(value: MarkdownThemeMode) {
    editor.update { it?.copy(theme = value) }
    viewModelScope.launch {
      // The selected theme is global for the local Markdown workspace. Keeping it in the
      // settings repository lets the editor and preview agree after process recreation.
      settings.setMarkdownTheme(value)
    }
  }

  fun flushEditor() {
    viewModelScope.launch {
      saveJob?.cancel()
      maxSaveJob?.cancel()
      persistEditor()
    }
  }

  fun exportMarkdown(noteId: Long, target: Uri) {
    export(noteId) { note -> repository.exportMarkdown(note, target) }
  }

  fun exportHtml(noteId: Long, target: Uri, theme: MarkdownThemeMode) {
    export(noteId) { note -> repository.exportHtml(note, target, theme) }
  }

  fun exportZip(target: Uri) {
    viewModelScope.launch {
      isExporting.value = true
      runCatching {
            repository.exportZip(
                allNotes = repository.all(),
                target = target,
                exportedAtEpochMs = clock.millis(),
                appVersion = BuildConfig.VERSION_NAME,
            )
          }
          .onSuccess { showStatus(R.string.notes_zip_exported) }
          .onFailure { showError(R.string.notes_export_failed) }
      isExporting.value = false
    }
  }

  fun dismissStatus() {
    status.value = null
  }

  fun dismissBackupHint() {
    showBackupHint.value = false
  }

  fun shareMarkdownText(noteId: Long, onReady: (String) -> Unit) {
    viewModelScope.launch {
      repository.find(noteId)?.let { onReady(it.content) } ?: showError(R.string.note_not_found)
    }
  }

  fun prepareMarkdownShareFile(noteId: Long, onReady: (Uri) -> Unit) {
    viewModelScope.launch {
      runCatching {
            val note = requireNotNull(repository.find(noteId)) { "Note no longer exists" }
            val directory = File(context.cacheDir, "shared-notes")
            val file =
                File(
                    directory,
                    "${note.displayTitle(context.getString(R.string.untitled_note)).hashCode()}.md",
                )
            repository.writeMarkdownToFile(note, file)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          }
          .onSuccess(onReady)
          .onFailure { showError(R.string.notes_share_failed) }
    }
  }

  fun deleteNote(id: Long) {
    viewModelScope.launch {
      runCatching { repository.delete(id) }
          .onSuccess {
            if (editor.value?.id == id) editor.value = null
            showStatus(R.string.note_deleted)
          }
          .onFailure { showError(R.string.note_delete_failed) }
    }
  }

  private fun export(noteId: Long, action: suspend (LocalMarkdownNoteEntity) -> Unit) {
    viewModelScope.launch {
      isExporting.value = true
      runCatching {
            val note = requireNotNull(repository.find(noteId)) { "Note no longer exists" }
            action(note)
          }
          .onSuccess { showStatus(R.string.note_exported) }
          .onFailure { showError(R.string.notes_export_failed) }
      isExporting.value = false
    }
  }

  private fun scheduleSave() {
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
      delay(AUTOSAVE_DELAY_MS)
      persistEditor()
    }
    if (maxSaveJob?.isActive != true) {
      maxSaveJob = viewModelScope.launch {
        delay(MAX_AUTOSAVE_DELAY_MS)
        persistEditor()
        maxSaveJob = null
      }
    }
  }

  private suspend fun persistEditor() {
    saveMutex.withLock {
      val current = editor.value ?: return
      editor.value = current.copy(isSaving = true)
      runCatching {
            if (!repository.save(current.id, current.title, current.content, clock.millis())) {
              showError(R.string.note_save_missing)
            }
          }
          .onFailure {
            showError(R.string.note_save_failed)
          }
      editor.value = editor.value?.copy(isSaving = false)
    }
  }

  private fun showStatus(messageRes: Int) {
    status.value = UiStatus(context.getString(messageRes), StatusSeverity.Info)
  }

  private fun showError(messageRes: Int) {
    status.value = UiStatus(context.getString(messageRes), StatusSeverity.Error)
  }
}

private const val AUTOSAVE_DELAY_MS = 350L
private const val MAX_AUTOSAVE_DELAY_MS = 1_500L
private const val MAX_QUERY_LENGTH = 100
private const val MAX_TITLE_LENGTH = 200
private const val MAX_CONTENT_LENGTH = 500_000

internal fun noteComparator(
    sort: NoteSort,
    untitledLabel: String,
): Comparator<LocalMarkdownNoteEntity> =
    when (sort) {
      NoteSort.Modified ->
          compareByDescending<LocalMarkdownNoteEntity> { it.modifiedEpochMs }
              .thenByDescending { it.id }
      NoteSort.Title ->
          compareBy<LocalMarkdownNoteEntity> { it.displayTitle(untitledLabel).lowercase() }
              .thenByDescending { it.modifiedEpochMs }
      NoteSort.Created ->
          compareByDescending<LocalMarkdownNoteEntity> { it.createdEpochMs }
              .thenByDescending { it.id }
    }
