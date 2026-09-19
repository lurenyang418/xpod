package app.xpod.ui.notes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.BuildConfig
import app.xpod.R
import app.xpod.data.InvalidMarkdownThemeException
import app.xpod.data.LocalMarkdownNoteEntity
import app.xpod.data.LocalMarkdownNotesRepository
import app.xpod.data.MAX_MARKDOWN_NOTE_CONTENT_LENGTH
import app.xpod.data.MAX_MARKDOWN_NOTE_TITLE_LENGTH
import app.xpod.data.MAX_MARKDOWN_THEME_JSON_LENGTH
import app.xpod.data.MarkdownCustomTheme
import app.xpod.data.MarkdownCustomThemeLimitException
import app.xpod.data.MarkdownImageTooLargeException
import app.xpod.data.MarkdownImportTooLargeException
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.MarkdownThemeSelection
import app.xpod.data.MarkdownThemeSpec
import app.xpod.data.SettingsRepository
import app.xpod.data.UnsupportedMarkdownImageException
import app.xpod.data.UnsupportedMarkdownImportException
import app.xpod.data.displayTitle
import app.xpod.data.encodeMarkdownThemeFile
import app.xpod.data.parseMarkdownThemeFile
import app.xpod.data.parseMarkdownThemeSelection
import app.xpod.ui.shared.StatusSeverity
import app.xpod.ui.shared.UiStatus
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

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
    val customThemes: List<MarkdownCustomTheme> = emptyList(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val showBackupHint: Boolean = false,
    val status: UiStatus? = null,
)

data class NoteEditorUiState(
    val id: Long,
    val title: String,
    val content: String,
    val theme: MarkdownThemeMode,
    val customThemeId: String? = null,
    val customTheme: MarkdownCustomTheme? = null,
    val isSaving: Boolean = false,
    val attachmentUris: Map<String, Uri> = emptyMap(),
    val pendingImageInsertion: NoteImageInsertion? = null,
    val isAttachingImage: Boolean = false,
)

data class NoteImageInsertion(val markdownReference: String, val altText: String)

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
  private val isImporting = MutableStateFlow(false)
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
  private val customThemes: StateFlow<List<MarkdownCustomTheme>> =
      settings.markdownCustomThemes.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          emptyList(),
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
          .combine(customThemes) { current, currentCustomThemes ->
            current.copy(customThemes = currentCustomThemes)
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())
  private val baseState: StateFlow<NotesUiState> =
      combine(contentState, isImporting) { current, importing ->
            current.copy(isImporting = importing)
          }
          .combine(status) { current, message -> current.copy(status = message) }
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
      val id =
          runCatching {
                val selection = settings.markdownThemeSelection.first()
                val customThemeIds = settings.markdownCustomThemes.first().map { it.id }.toSet()
                val validSelection =
                    if (
                        selection.mode == MarkdownThemeMode.Custom &&
                            selection.customThemeId !in customThemeIds
                    ) {
                      MarkdownThemeSelection(MarkdownThemeMode.FollowApp)
                    } else {
                      selection
                    }
                repository.create(clock.millis(), themeSelection = validSelection)
              }
              .getOrElse {
                showError(R.string.note_create_failed)
                return@launch
              }
      onCreated(id)
      showBackupHintIfNeeded()
    }
  }

  fun importMarkdown(uri: Uri, onImported: (Long) -> Unit) {
    viewModelScope.launch {
      isImporting.value = true
      try {
        runCatchingCancellable {
              withContext(Dispatchers.IO) {
                repository.importMarkdown(
                    uri = uri,
                    nowEpochMs = clock.millis(),
                    untitledLabel = context.getString(R.string.untitled_note),
                )
              }
            }
            .fold(
                onSuccess = { id ->
                  showStatus(R.string.note_imported)
                  showBackupHintIfNeeded()
                  onImported(id)
                },
                onFailure = { error ->
                  when (error) {
                    is UnsupportedMarkdownImportException ->
                        showError(R.string.note_import_unsupported)
                    is MarkdownImportTooLargeException -> showError(R.string.note_import_too_large)
                    else -> showError(R.string.note_import_failed)
                  }
                },
            )
      } finally {
        isImporting.value = false
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
        val attachmentUris = repository.imageAttachments(id)
        val selection = parseMarkdownThemeSelection(note.theme)
        val customTheme =
            selection.customThemeId?.let { themeId ->
              settings.markdownCustomThemes.first().firstOrNull { it.id == themeId }
            }
        val selectedTheme =
            if (selection.mode == MarkdownThemeMode.Custom && customTheme == null) {
              MarkdownThemeMode.FollowApp
            } else {
              selection.mode
            }
        editor.value =
            NoteEditorUiState(
                id = note.id,
                title = note.title,
                content = note.content,
                theme = selectedTheme,
                customThemeId = customTheme?.id,
                customTheme = customTheme,
                attachmentUris = attachmentUris,
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
    editor.update { it?.copy(title = value.take(MAX_MARKDOWN_NOTE_TITLE_LENGTH)) }
    scheduleSave()
  }

  fun setContent(value: String) {
    editor.update { it?.copy(content = value.take(MAX_MARKDOWN_NOTE_CONTENT_LENGTH)) }
    scheduleSave()
  }

  fun setTheme(value: MarkdownThemeMode) {
    if (value == MarkdownThemeMode.Custom) return
    editor.update { it?.copy(theme = value, customThemeId = null, customTheme = null) }
    scheduleSave()
    viewModelScope.launch {
      // Reuse the last selected theme as the default for newly created notes. Existing notes
      // persist their own theme in Room.
      settings.setMarkdownThemeSelection(MarkdownThemeSelection(value))
    }
  }

  fun setCustomTheme(id: String) {
    val customTheme = customThemes.value.firstOrNull { it.id == id } ?: return
    editor.update {
      it?.copy(
          theme = MarkdownThemeMode.Custom,
          customThemeId = customTheme.id,
          customTheme = customTheme,
      )
    }
    scheduleSave()
    viewModelScope.launch {
      settings.setMarkdownThemeSelection(
          MarkdownThemeSelection(MarkdownThemeMode.Custom, customTheme.id)
      )
    }
  }

  fun importCustomTheme(uri: Uri) {
    viewModelScope.launch {
      runCatchingCancellable {
            val source =
                withContext(Dispatchers.IO) {
                  val input =
                      context.contentResolver.openInputStream(uri)
                          ?: error("Could not open the selected theme")
                  val bytes = input.use { it.readNBytes(MAX_MARKDOWN_THEME_JSON_LENGTH + 1) }
                  if (bytes.size > MAX_MARKDOWN_THEME_JSON_LENGTH) {
                    throw InvalidMarkdownThemeException()
                  }
                  String(bytes, Charsets.UTF_8)
                }
            val customTheme = parseMarkdownThemeFile(source)
            if (!settings.addMarkdownCustomTheme(customTheme)) {
              throw MarkdownCustomThemeLimitException()
            }
            customTheme
          }
          .fold(
              onSuccess = { customTheme ->
                editor.update {
                  it?.copy(
                      theme = MarkdownThemeMode.Custom,
                      customThemeId = customTheme.id,
                      customTheme = customTheme,
                  )
                }
                scheduleSave()
                settings.setMarkdownThemeSelection(
                    MarkdownThemeSelection(MarkdownThemeMode.Custom, customTheme.id)
                )
                showStatus(R.string.markdown_theme_imported)
              },
              onFailure = { error ->
                when (error) {
                  is InvalidMarkdownThemeException ->
                      showError(R.string.markdown_theme_import_invalid)
                  is MarkdownCustomThemeLimitException ->
                      showError(R.string.markdown_theme_limit_reached)
                  else -> showError(R.string.markdown_theme_import_failed)
                }
              },
          )
    }
  }

  fun exportCustomTheme(themeId: String, target: Uri) {
    viewModelScope.launch {
      isExporting.value = true
      try {
        runCatchingCancellable {
              val customTheme =
                  settings.markdownCustomThemes.first().firstOrNull { it.id == themeId }
                      ?: error("Custom theme no longer exists")
              withContext(Dispatchers.IO) {
                val output =
                    context.contentResolver.openOutputStream(target)
                        ?: error("Could not open the theme export target")
                output.bufferedWriter(Charsets.UTF_8).use {
                  it.write(encodeMarkdownThemeFile(customTheme))
                }
              }
            }
            .fold(
                onSuccess = { showStatus(R.string.markdown_theme_exported) },
                onFailure = { showError(R.string.markdown_theme_export_failed) },
            )
      } finally {
        isExporting.value = false
      }
    }
  }

  fun attachImage(uri: Uri) {
    val current = editor.value ?: return
    if (current.isAttachingImage) return
    editor.value = current.copy(isAttachingImage = true)
    viewModelScope.launch {
      runCatchingCancellable {
            withContext(Dispatchers.IO) { repository.attachImage(current.id, uri) }
          }
          .fold(
              onSuccess = { attachment ->
                val latest = editor.value
                if (latest?.id != current.id) {
                  repository.removeImageAttachment(current.id, attachment.markdownReference)
                } else {
                  editor.value =
                      latest.copy(
                          attachmentUris =
                              latest.attachmentUris +
                                  (attachment.markdownReference to attachment.fileUri),
                          pendingImageInsertion =
                              NoteImageInsertion(
                                  markdownReference = attachment.markdownReference,
                                  altText = attachment.altText,
                              ),
                          isAttachingImage = false,
                      )
                  showStatus(R.string.note_image_attached)
                }
              },
              onFailure = { error ->
                editor.update { state ->
                  if (state?.id == current.id) state.copy(isAttachingImage = false) else state
                }
                when (error) {
                  is MarkdownImageTooLargeException -> showError(R.string.note_image_too_large)
                  is UnsupportedMarkdownImageException -> showError(R.string.note_image_unsupported)
                  else -> showError(R.string.note_image_attach_failed)
                }
              },
          )
    }
  }

  fun consumeImageInsertion(reference: String) {
    editor.update { state ->
      if (state?.pendingImageInsertion?.markdownReference == reference) {
        state.copy(pendingImageInsertion = null)
      } else {
        state
      }
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

  fun exportHtml(noteId: Long, target: Uri, theme: MarkdownThemeSpec) {
    export(noteId) { note -> repository.exportHtml(note, target, theme) }
  }

  fun exportPdf(noteId: Long, target: Uri, theme: MarkdownThemeSpec) {
    export(noteId) { note -> repository.exportPdf(note, target, theme) }
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
                customThemes = settings.markdownCustomThemes.first(),
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
            if (editor.value?.id == noteId) {
              saveJob?.cancel()
              maxSaveJob?.cancel()
              persistEditor()
            }
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
            if (
                !repository.save(
                    current.id,
                    current.title,
                    current.content,
                    clock.millis(),
                    themeSelection = MarkdownThemeSelection(current.theme, current.customThemeId),
                )
            ) {
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

  private suspend fun showBackupHintIfNeeded() {
    if (!settings.notesBackupHintShown.first()) {
      settings.markNotesBackupHintShown()
      showBackupHint.value = true
    }
  }
}

private const val AUTOSAVE_DELAY_MS = 350L
private const val MAX_AUTOSAVE_DELAY_MS = 1_500L
private const val MAX_QUERY_LENGTH = 100

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
