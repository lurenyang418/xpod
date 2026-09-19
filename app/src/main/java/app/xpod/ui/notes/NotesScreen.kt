package app.xpod.ui.notes

import android.net.Uri as AndroidUri
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.xpod.R
import app.xpod.data.LocalMarkdownNoteEntity
import app.xpod.data.MarkdownFontFamily
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.ThemeMode
import app.xpod.data.displayTitle
import app.xpod.data.markdownThemeSpec
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesScreen(
    state: NotesUiState,
    onQueryChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onSortChanged: (NoteSort) -> Unit,
    onExportZip: (Uri) -> Unit,
) {
  var deleteId by remember { mutableStateOf<Long?>(null) }
  var menuExpanded by remember { mutableStateOf(false) }
  val zipLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let(onExportZip)
      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.notes)) },
            actions = {
              var sortMenuExpanded by remember { mutableStateOf(false) }
              IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.note_sort))
              }
              DropdownMenu(
                  expanded = sortMenuExpanded,
                  onDismissRequest = { sortMenuExpanded = false },
              ) {
                NoteSort.entries.forEach { sort ->
                  DropdownMenuItem(
                      text = { Text(stringResource(noteSortLabel(sort))) },
                      onClick = {
                        sortMenuExpanded = false
                        onSortChanged(sort)
                      },
                  )
                }
              }
              IconButton(
                  onClick = {
                    menuExpanded = true
                  }
              ) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.note_actions))
              }
              DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_notes_zip)) },
                    leadingIcon = { Icon(Icons.Filled.Unarchive, null) },
                    onClick = {
                      menuExpanded = false
                      zipLauncher.launch("xpod-notes.zip")
                    },
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
      },
  ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.isExporting) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      OutlinedTextField(
          value = state.query,
          onValueChange = onQueryChanged,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = { Text(stringResource(R.string.search_notes)) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      )
      if (state.notes.isEmpty()) {
        NotesEmptyState(hasQuery = state.query.isNotBlank(), onCreate = onCreate)
      } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.notes, key = LocalMarkdownNoteEntity::id) { note ->
            NoteListItem(
                note = note,
                onClick = { onOpenNote(note.id) },
                onDelete = { deleteId = note.id },
            )
          }
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.create_note))
        }
      }
    }
  }

  deleteId?.let { id ->
    AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text(stringResource(R.string.delete_note_title)) },
        text = { Text(stringResource(R.string.delete_note_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                deleteId = null
                onDeleteNote(id)
              }
          ) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
}

@Composable
private fun NotesEmptyState(hasQuery: Boolean, onCreate: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(if (hasQuery) R.string.no_matching_notes else R.string.no_notes))
      if (!hasQuery) {
        Button(onClick = onCreate) { Text(stringResource(R.string.create_first_note)) }
      }
    }
  }
}

@Composable
internal fun NotesLoadingScreen() {
  val loadingLabel = stringResource(R.string.loading_note)
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingLabel })
  }
}

@Composable
private fun NoteListItem(note: LocalMarkdownNoteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      shape = MaterialTheme.shapes.medium,
      tonalElevation = 1.dp,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Filled.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
            note.displayTitle(stringResource(R.string.untitled_note)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            formatNoteDate(note.modifiedEpochMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val preview = note.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        if (preview.isNotBlank()) {
          Text(
              preview,
              maxLines = 2,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      var menuExpanded by remember { mutableStateOf(false) }
      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.note_actions))
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
              text = {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
              },
              leadingIcon = {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
              },
              onClick = {
                menuExpanded = false
                onDelete()
              },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    editor: NoteEditorUiState,
    appTheme: ThemeMode,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onThemeChanged: (MarkdownThemeMode) -> Unit,
    onExportMarkdown: (Uri) -> Unit,
    onExportHtml: (Uri, MarkdownThemeMode) -> Unit,
    onShareMarkdownText: () -> Unit,
    onShareMarkdownFile: () -> Unit,
    onFlush: () -> Unit,
    isExporting: Boolean,
    showBackupHint: Boolean,
    onDismissBackupHint: () -> Unit,
) {
  var showPreview by rememberSaveableEditorMode()
  var contentFieldValue by remember(editor.id) { mutableStateOf(TextFieldValue(editor.content)) }
  var themeMenuExpanded by remember { mutableStateOf(false) }
  var exportMenuExpanded by remember { mutableStateOf(false) }
  val resolvedTheme = resolveMarkdownTheme(editor.theme, appTheme)
  val lifecycleOwner = LocalLifecycleOwner.current
  val markdownLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        it?.let(onExportMarkdown)
      }
  val htmlLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        it?.let { uri -> onExportHtml(uri, resolvedTheme) }
      }
  LaunchedEffect(editor.id, editor.content) {
    if (contentFieldValue.text != editor.content) {
      contentFieldValue = TextFieldValue(editor.content, TextRange(editor.content.length))
    }
  }
  DisposableEffect(editor.id) { onDispose(onFlush) }
  DisposableEffect(lifecycleOwner, onFlush) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) onFlush()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
              }
            },
            title = {
              Text(
                  editor.title.ifBlank { stringResource(R.string.notes) },
                  maxLines = 1,
              )
            },
            actions = {
              Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Row(modifier = Modifier.padding(2.dp)) {
                  NoteViewModeButton(
                      selected = !showPreview,
                      icon = Icons.Filled.Edit,
                      contentDescription = stringResource(R.string.note_edit),
                      onClick = { showPreview = false },
                      modifier = Modifier.size(40.dp),
                  )
                  NoteViewModeButton(
                      selected = showPreview,
                      icon = Icons.Filled.Visibility,
                      contentDescription = stringResource(R.string.note_preview),
                      onClick = { showPreview = true },
                      modifier = Modifier.size(40.dp),
                  )
                }
              }
              if (editor.isSaving) {
                Text(
                    text = stringResource(R.string.note_saving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
              }
              IconButton(onClick = { themeMenuExpanded = true }) {
                Icon(Icons.Filled.Palette, stringResource(R.string.note_theme))
              }
              DropdownMenu(
                  expanded = themeMenuExpanded,
                  onDismissRequest = { themeMenuExpanded = false },
              ) {
                MarkdownThemeMode.entries.forEach { mode ->
                  DropdownMenuItem(
                      text = { Text(stringResource(markdownThemeLabel(mode))) },
                      onClick = {
                        themeMenuExpanded = false
                        onThemeChanged(mode)
                      },
                  )
                }
              }
              IconButton(onClick = { exportMenuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.note_actions))
              }
              DropdownMenu(
                  expanded = exportMenuExpanded,
                  onDismissRequest = { exportMenuExpanded = false },
              ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_markdown)) },
                    onClick = {
                      exportMenuExpanded = false
                      markdownLauncher.launch(exportBaseName(editor.title, "md"))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_note_text)) },
                    onClick = {
                      exportMenuExpanded = false
                      onShareMarkdownText()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_note_file)) },
                    onClick = {
                      exportMenuExpanded = false
                      onShareMarkdownFile()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_html)) },
                    onClick = {
                      exportMenuExpanded = false
                      htmlLauncher.launch(exportBaseName(editor.title, "html"))
                    },
                )
              }
            },
        )
      },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (isExporting) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      if (showPreview) {
        MarkdownPreview(
            content = editor.content,
            theme = resolvedTheme,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          BasicTextField(
              value = editor.title,
              onValueChange = onTitleChanged,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
              singleLine = true,
              textStyle =
                  MaterialTheme.typography.headlineSmall.copy(
                      color = MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.SemiBold,
                  ),
              decorationBox = { innerTextField ->
                Box {
                  if (editor.title.isBlank()) {
                    Text(
                        text = stringResource(R.string.note_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  innerTextField()
                }
              },
          )
          MarkdownToolbar(
              onInsert = { prefix, suffix, placeholder ->
                val nextValue =
                    insertMarkdownAtSelection(contentFieldValue, prefix, suffix, placeholder)
                contentFieldValue = nextValue
                onContentChanged(nextValue.text)
              },
              modifier = Modifier.fillMaxWidth(),
          )
          Surface(
              modifier = Modifier.fillMaxWidth().weight(1f),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              tonalElevation = 1.dp,
          ) {
            OutlinedTextField(
                value = contentFieldValue,
                onValueChange = { value ->
                  contentFieldValue = value
                  onContentChanged(value.text)
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text(stringResource(R.string.notes_empty_content)) },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                    ),
            )
          }
        }
      }
    }
  }

  if (showBackupHint) {
    AlertDialog(
        onDismissRequest = onDismissBackupHint,
        title = { Text(stringResource(R.string.notes_backup_hint_title)) },
        text = { Text(stringResource(R.string.notes_backup_hint_message)) },
        confirmButton = {
          TextButton(onClick = onDismissBackupHint) {
            Text(stringResource(R.string.notes_backup_hint_action))
          }
        },
    )
  }
}

@Composable
private fun NoteViewModeButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .clip(MaterialTheme.shapes.small)
              .background(
                  if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
              ),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun MarkdownToolbar(
    onInsert: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()
  val actions =
      listOf(
          MarkdownToolbarAction(
              Icons.Filled.Title,
              R.string.notes_toolbar_heading,
              "# ",
              "",
          ),
          MarkdownToolbarAction(
              Icons.Filled.FormatBold,
              R.string.notes_toolbar_bold,
              "**",
              "**",
          ),
          MarkdownToolbarAction(
              Icons.Filled.FormatItalic,
              R.string.notes_toolbar_italic,
              "*",
              "*",
          ),
          MarkdownToolbarAction(
              Icons.Filled.StrikethroughS,
              R.string.notes_toolbar_strike,
              "~~",
              "~~",
          ),
          MarkdownToolbarAction(
              Icons.Filled.InsertLink,
              R.string.notes_toolbar_link,
              "[",
              "](https://)",
          ),
          MarkdownToolbarAction(
              Icons.Filled.FormatQuote,
              R.string.notes_toolbar_quote,
              "> ",
              "",
          ),
          MarkdownToolbarAction(Icons.Filled.Code, R.string.notes_toolbar_code, "`", "`"),
          MarkdownToolbarAction(
              Icons.Filled.DataObject,
              R.string.notes_toolbar_code_block,
              "```\n",
              "\n```",
          ),
          MarkdownToolbarAction(
              Icons.AutoMirrored.Filled.FormatListBulleted,
              R.string.notes_toolbar_bullet,
              "- ",
              "",
          ),
          MarkdownToolbarAction(
              Icons.Filled.FormatListNumbered,
              R.string.notes_toolbar_numbered,
              "1. ",
              "",
          ),
          MarkdownToolbarAction(
              Icons.Filled.Checklist,
              R.string.notes_toolbar_task,
              "- [ ] ",
              "",
          ),
          MarkdownToolbarAction(
              Icons.Filled.HorizontalRule,
              R.string.notes_toolbar_rule,
              "\n---\n",
              "",
          ),
      )
  Box(modifier = modifier) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
      Row(
          modifier = Modifier.horizontalScroll(scrollState).padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        actions.forEach { action ->
          IconButton(
              onClick = { onInsert(action.prefix, action.suffix, "text") },
              modifier = Modifier.size(48.dp),
          ) {
            Icon(
                imageVector = action.icon,
                contentDescription = stringResource(action.labelRes),
            )
          }
        }
      }
    }
    if (scrollState.canScrollBackward) {
      IconButton(
          onClick = {
            coroutineScope.launch {
              scrollState.animateScrollTo(
                  (scrollState.value - TOOLBAR_SCROLL_STEP).coerceAtLeast(0)
              )
            }
          },
          modifier =
              Modifier.align(Alignment.CenterStart)
                  .padding(start = 2.dp)
                  .clip(MaterialTheme.shapes.small)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.notes_toolbar_scroll_previous),
        )
      }
    }
    if (scrollState.canScrollForward) {
      IconButton(
          onClick = {
            coroutineScope.launch {
              scrollState.animateScrollTo(
                  (scrollState.value + TOOLBAR_SCROLL_STEP).coerceAtMost(scrollState.maxValue)
              )
            }
          },
          modifier =
              Modifier.align(Alignment.CenterEnd)
                  .padding(end = 2.dp)
                  .clip(MaterialTheme.shapes.small)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.notes_toolbar_scroll_next),
        )
      }
    }
  }
}

private const val TOOLBAR_SCROLL_STEP = 240

private data class MarkdownToolbarAction(
    val icon: ImageVector,
    val labelRes: Int,
    val prefix: String,
    val suffix: String,
)

internal fun insertMarkdownAtSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
  val text = value.text
  val start = value.selection.start.coerceIn(0, text.length)
  val end = value.selection.end.coerceIn(start, text.length)
  val inserted = "$prefix$placeholder$suffix"
  val nextText = text.replaceRange(start, end, inserted)
  val nextSelection = start + prefix.length + placeholder.length
  return TextFieldValue(
      text = nextText,
      selection = TextRange(nextSelection.coerceIn(0, nextText.length)),
  )
}

@Composable
private fun MarkdownPreview(
    content: String,
    theme: MarkdownThemeMode,
    modifier: Modifier = Modifier,
) {
  val spec = markdownThemeSpec(theme)
  val isSerif = spec.fontFamily == MarkdownFontFamily.Serif
  val documentFont = if (isSerif) FontFamily.Serif else FontFamily.Default
  val bodyTextStyle =
      MaterialTheme.typography.bodyLarge.copy(
          fontFamily = documentFont,
          fontSize = spec.bodyFontSizeSp.sp,
          lineHeight = (spec.bodyFontSizeSp * spec.lineHeightMultiplier).sp,
      )
  Surface(
      modifier = modifier,
      color = Color(spec.backgroundArgb),
  ) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = spec.contentWidthDp.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
    ) {
      Markdown(
          content = content.ifBlank { stringResource(R.string.notes_empty_content) },
          modifier = Modifier.fillMaxWidth(),
          colors = markdownColor(text = Color(spec.textArgb)),
          typography =
              markdownTypography(
                  h1 =
                      MaterialTheme.typography.headlineMedium.copy(
                          fontFamily = documentFont,
                          fontSize = spec.headingFontSizeSp.sp,
                      ),
                  h2 =
                      MaterialTheme.typography.headlineSmall.copy(
                          fontFamily = documentFont,
                          fontSize = (spec.headingFontSizeSp * 0.8f).sp,
                      ),
                  h3 =
                      MaterialTheme.typography.titleLarge.copy(
                          fontFamily = documentFont,
                          fontSize = (spec.headingFontSizeSp * 0.67f).sp,
                      ),
                  text = bodyTextStyle,
                  paragraph = bodyTextStyle,
                  ordered = bodyTextStyle,
                  bullet = bodyTextStyle,
                  list = bodyTextStyle,
              ),
          imageTransformer = HttpsOnlyImageTransformer,
      )
    }
  }
}

private object HttpsOnlyImageTransformer : ImageTransformer {
  private val fallback = NoOpImageTransformerImpl()

  @Composable
  override fun transform(link: String): ImageData? =
      if (AndroidUri.parse(link).scheme.equals("https", ignoreCase = true)) {
        val painter =
            rememberAsyncImagePainter(
                model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(link)
                        .size(1280, 1280)
                        .build()
            )
        ImageData(painter = painter, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
      } else {
        fallback.transform(link)
      }

  @Composable
  override fun intrinsicSize(painter: Painter): Size =
      if (painter is AsyncImagePainter) {
        painter.intrinsicSize
      } else {
        fallback.intrinsicSize(painter)
      }
}

@Composable
private fun rememberSaveableEditorMode(): androidx.compose.runtime.MutableState<Boolean> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

@Composable
private fun resolveMarkdownTheme(mode: MarkdownThemeMode, appTheme: ThemeMode): MarkdownThemeMode =
    when (mode) {
      MarkdownThemeMode.FollowApp ->
          if (
              appTheme == ThemeMode.Dark || (appTheme == ThemeMode.System && isSystemInDarkTheme())
          ) {
            MarkdownThemeMode.Night
          } else {
            MarkdownThemeMode.GitHub
          }
      else -> mode
    }

private fun markdownThemeLabel(theme: MarkdownThemeMode): Int =
    when (theme) {
      MarkdownThemeMode.FollowApp -> R.string.markdown_theme_follow_app
      MarkdownThemeMode.GitHub -> R.string.markdown_theme_github
      MarkdownThemeMode.Newsprint -> R.string.markdown_theme_newsprint
      MarkdownThemeMode.Night -> R.string.markdown_theme_night
    }

private fun exportBaseName(title: String, extension: String): String {
  val hasTitle = title.trim().isNotEmpty()
  val safe =
      title
          .trim()
          .replace(Regex("[\\\\/:*?\"<>|]"), "_")
          .replace(Regex("\\s+"), " ")
          .trim('.')
          .take(80)
          .ifBlank { "note" }
  if (hasTitle) return "$safe.$extension"
  val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
  return "note-$timestamp.$extension"
}

private fun noteSortLabel(sort: NoteSort): Int =
    when (sort) {
      NoteSort.Modified -> R.string.note_sort_modified
      NoteSort.Title -> R.string.note_sort_title
      NoteSort.Created -> R.string.note_sort_created
    }

private fun formatNoteDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
