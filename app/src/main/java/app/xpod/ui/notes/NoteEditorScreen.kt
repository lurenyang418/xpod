package app.xpod.ui.notes

import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.xpod.R
import app.xpod.data.MarkdownCustomTheme
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.MarkdownThemeSpec
import app.xpod.data.ThemeMode
import app.xpod.data.markdownThemeSpec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    onExportHtml: (Uri, MarkdownThemeSpec) -> Unit,
    onExportPdf: (Uri, MarkdownThemeSpec) -> Unit,
    cloudMemosBusy: Boolean = false,
    onSaveToCloudMemos: () -> Unit = {},
    onShareMarkdownText: () -> Unit,
    onShareMarkdownFile: () -> Unit,
    onAttachImage: (Uri) -> Unit,
    onImageInsertionConsumed: (String) -> Unit,
    onFlush: () -> Unit,
    isExporting: Boolean,
    showBackupHint: Boolean,
    onDismissBackupHint: () -> Unit,
    customThemes: List<MarkdownCustomTheme> = emptyList(),
    onCustomThemeSelected: (String) -> Unit = {},
    onImportTheme: (Uri) -> Unit = {},
    onExportTheme: (String, Uri) -> Unit = { _, _ -> },
) {
  var showPreview by rememberSaveableEditorMode()
  var contentFieldValue by remember(editor.id) { mutableStateOf(TextFieldValue(editor.content)) }
  val editHistory = remember(editor.id) { MarkdownEditHistory(TextFieldValue(editor.content)) }
  var themeMenuExpanded by remember { mutableStateOf(false) }
  var exportMenuExpanded by remember { mutableStateOf(false) }
  val selectedCustomTheme =
      editor.customTheme ?: customThemes.firstOrNull { it.id == editor.customThemeId }
  val resolvedThemeSpec = resolveMarkdownThemeSpec(editor.theme, selectedCustomTheme, appTheme)
  var contentEditorFocused by remember(editor.id) { mutableStateOf(false) }
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val compactLandscapeWritingMode =
      shouldUseCompactLandscapeWritingMode(
          isLandscape = isLandscape,
          isCompactViewport =
              LocalWindowInfo.current.containerDpSize.height < LANDSCAPE_COMPACT_VIEWPORT_HEIGHT,
          contentEditorFocused = contentEditorFocused,
      )
  var pendingThemeExportId by remember { mutableStateOf<String?>(null) }
  val syntaxColors =
      MarkdownSyntaxColors(
          keyword = MaterialTheme.colorScheme.primary,
          string = MaterialTheme.colorScheme.tertiary,
          comment = MaterialTheme.colorScheme.onSurfaceVariant,
          number = MaterialTheme.colorScheme.secondary,
      )
  val codeVisualTransformation =
      remember(syntaxColors) { MarkdownCodeVisualTransformation(syntaxColors) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val markdownLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        it?.let(onExportMarkdown)
      }
  val htmlLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) {
        it?.let { uri -> onExportHtml(uri, resolvedThemeSpec) }
      }
  val pdfLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        it?.let { uri -> onExportPdf(uri, resolvedThemeSpec) }
      }
  val themeImportLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(onImportTheme)
      }
  val themeExportLauncher =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        val themeId = pendingThemeExportId
        pendingThemeExportId = null
        if (uri != null && themeId != null) onExportTheme(themeId, uri)
      }
  val imageLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(onAttachImage)
      }

  fun updateContent(value: TextFieldValue, coalesceWithPrevious: Boolean = false) {
    editHistory.record(value, SystemClock.uptimeMillis(), coalesceWithPrevious)
    contentFieldValue = value
    onContentChanged(value.text)
  }

  LaunchedEffect(editor.id, editor.content) {
    if (contentFieldValue.text != editor.content) {
      contentFieldValue = TextFieldValue(editor.content, TextRange(editor.content.length))
      editHistory.reset(contentFieldValue)
    }
  }
  LaunchedEffect(editor.id, editor.pendingImageInsertion) {
    val insertion = editor.pendingImageInsertion ?: return@LaunchedEffect
    val nextValue =
        insertMarkdownAtSelection(
            contentFieldValue,
            prefix = "![",
            suffix = "](${insertion.markdownReference})",
            placeholder = insertion.altText,
        )
    updateContent(nextValue)
    onImageInsertionConsumed(insertion.markdownReference)
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
      modifier = Modifier.fillMaxSize(),
      topBar = {
        if (!compactLandscapeWritingMode) {
          TopAppBar(
              navigationIcon = {
                IconButton(onClick = onBack) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
              },
              title = {
                Text(editor.title.ifBlank { stringResource(R.string.notes) }, maxLines = 1)
              },
              actions = {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                  androidx.compose.foundation.layout.Row(modifier = Modifier.padding(2.dp)) {
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
                  Icon(
                      Icons.Filled.Palette,
                      selectedCustomTheme?.name ?: stringResource(R.string.note_theme),
                  )
                }
                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false },
                ) {
                  MarkdownThemeMode.entries
                      .filterNot { it == MarkdownThemeMode.Custom }
                      .forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(markdownThemeLabel(mode))) },
                            onClick = {
                              themeMenuExpanded = false
                              onThemeChanged(mode)
                            },
                        )
                      }
                  if (customThemes.isNotEmpty()) HorizontalDivider()
                  customThemes.forEach { customTheme ->
                    DropdownMenuItem(
                        text = { Text(customTheme.name) },
                        trailingIcon = {
                          if (customTheme.id == editor.customThemeId) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                          }
                        },
                        onClick = {
                          themeMenuExpanded = false
                          onCustomThemeSelected(customTheme.id)
                        },
                    )
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.markdown_theme_import_json)) },
                      onClick = {
                        themeMenuExpanded = false
                        themeImportLauncher.launch(arrayOf("application/json", "text/json"))
                      },
                  )
                  selectedCustomTheme?.let { customTheme ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.markdown_theme_export_json)) },
                        onClick = {
                          themeMenuExpanded = false
                          pendingThemeExportId = customTheme.id
                          themeExportLauncher.launch(
                              exportBaseName(customTheme.name, "xpod-theme.json")
                          )
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
                      text = {
                        Text(
                            stringResource(
                                if (cloudMemosBusy) R.string.cloud_memos_saving
                                else R.string.save_to_cloud_memos
                            )
                        )
                      },
                      enabled = !cloudMemosBusy,
                      onClick = {
                        exportMenuExpanded = false
                        onSaveToCloudMemos()
                      },
                  )
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
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.export_pdf)) },
                      onClick = {
                        exportMenuExpanded = false
                        pdfLauncher.launch(exportBaseName(editor.title, "pdf"))
                      },
                  )
                }
              },
          )
        }
      },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (isExporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      if (editor.isAttachingImage) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      if (showPreview) {
        MarkdownPreview(
            content = editor.content,
            themeSpec = resolvedThemeSpec,
            attachments = editor.attachmentUris,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      } else {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .then(if (isLandscape) Modifier.imePadding() else Modifier)
                    .padding(horizontal = if (compactLandscapeWritingMode) 8.dp else 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(if (compactLandscapeWritingMode) 4.dp else 12.dp),
        ) {
          if (!compactLandscapeWritingMode) {
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
          }
          MarkdownToolbar(
              canUndo = editHistory.canUndo,
              canRedo = editHistory.canRedo,
              onUndo = {
                editHistory.undo()?.let { value ->
                  contentFieldValue = value
                  onContentChanged(value.text)
                }
              },
              onRedo = {
                editHistory.redo()?.let { value ->
                  contentFieldValue = value
                  onContentChanged(value.text)
                }
              },
              onInsert = { prefix, suffix, placeholder ->
                val nextValue =
                    insertMarkdownAtSelection(contentFieldValue, prefix, suffix, placeholder)
                updateContent(nextValue)
              },
              onChooseImage = {
                imageLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/gif"))
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
                  updateContent(value, coalesceWithPrevious = true)
                },
                modifier =
                    Modifier.fillMaxSize()
                        .onFocusChanged { contentEditorFocused = it.isFocused }
                        .testTag(NOTE_CONTENT_EDITOR_TEST_TAG),
                placeholder = { Text(stringResource(R.string.notes_empty_content)) },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = codeVisualTransformation,
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

internal const val NOTE_CONTENT_EDITOR_TEST_TAG = "note_content_editor"

internal fun shouldUseCompactLandscapeWritingMode(
    isLandscape: Boolean,
    isCompactViewport: Boolean,
    contentEditorFocused: Boolean,
): Boolean = isLandscape && isCompactViewport && contentEditorFocused

private val LANDSCAPE_COMPACT_VIEWPORT_HEIGHT = 240.dp

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
private fun rememberSaveableEditorMode(): androidx.compose.runtime.MutableState<Boolean> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

@Composable
private fun resolveMarkdownThemeSpec(
    mode: MarkdownThemeMode,
    customTheme: MarkdownCustomTheme?,
    appTheme: ThemeMode,
): MarkdownThemeSpec =
    when (mode) {
      MarkdownThemeMode.Custom ->
          customTheme?.spec ?: markdownThemeSpec(MarkdownThemeMode.FollowApp)
      MarkdownThemeMode.FollowApp ->
          markdownThemeSpec(
              if (
                  appTheme == ThemeMode.Dark ||
                      (appTheme == ThemeMode.System &&
                          androidx.compose.foundation.isSystemInDarkTheme())
              ) {
                MarkdownThemeMode.Night
              } else {
                MarkdownThemeMode.GitHub
              }
          )
      else -> markdownThemeSpec(mode)
    }

private fun markdownThemeLabel(theme: MarkdownThemeMode): Int =
    when (theme) {
      MarkdownThemeMode.FollowApp -> R.string.markdown_theme_follow_app
      MarkdownThemeMode.GitHub -> R.string.markdown_theme_github
      MarkdownThemeMode.Newsprint -> R.string.markdown_theme_newsprint
      MarkdownThemeMode.Night -> R.string.markdown_theme_night
      MarkdownThemeMode.Custom -> R.string.markdown_theme_custom
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
