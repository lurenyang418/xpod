package app.xpod.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.R
import app.xpod.data.MarkdownCustomTheme
import app.xpod.data.MarkdownFontFamily
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.MarkdownThemeSpec
import app.xpod.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun editorMenuCanUploadNoteToCloudMemos() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var uploadCount = 0

    compose.setContent {
      MaterialTheme {
        NoteEditorScreen(
            editor =
                NoteEditorUiState(
                    id = 2L,
                    title = "Upload me",
                    content = "Markdown body",
                    theme = MarkdownThemeMode.GitHub,
                ),
            appTheme = ThemeMode.System,
            onBack = {},
            onTitleChanged = {},
            onContentChanged = {},
            onThemeChanged = {},
            onExportMarkdown = {},
            onExportHtml = { _, _ -> },
            onExportPdf = { _, _ -> },
            onSaveToCloudMemos = { uploadCount++ },
            onShareMarkdownText = {},
            onShareMarkdownFile = {},
            onAttachImage = {},
            onImageInsertionConsumed = {},
            onFlush = {},
            isExporting = false,
            showBackupHint = false,
            onDismissBackupHint = {},
        )
      }
    }

    compose.onNodeWithContentDescription(context.getString(R.string.note_actions)).performClick()
    compose.onNodeWithText(context.getString(R.string.save_to_cloud_memos)).performClick()
    compose.runOnIdle { assertEquals(1, uploadCount) }
  }

  @Test
  fun themeMenuOffersJsonImportAndSelectsCustomTheme() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val customTheme =
        MarkdownCustomTheme(
            id = "01234567-89ab-4def-8123-456789abcdef",
            name = "Ocean",
            spec =
                MarkdownThemeSpec(
                    mode = MarkdownThemeMode.Custom,
                    backgroundArgb = 0xFF0B1F2A,
                    textArgb = 0xFFF0F7FA,
                    codeBackgroundArgb = 0xFF183746,
                    codeTextArgb = 0xFFF0F7FA,
                    linkArgb = 0xFF8BD8F5,
                    quoteBackgroundArgb = 0xFF16313F,
                    tableBorderArgb = 0xFF3F6577,
                    fontFamily = MarkdownFontFamily.SansSerif,
                ),
        )
    var selectedThemeId: String? = null

    compose.setContent {
      MaterialTheme {
        NoteEditorScreen(
            editor =
                NoteEditorUiState(
                    id = 3L,
                    title = "Theme test",
                    content = "Text",
                    theme = MarkdownThemeMode.Custom,
                    customThemeId = customTheme.id,
                    customTheme = customTheme,
                ),
            appTheme = ThemeMode.System,
            onBack = {},
            onTitleChanged = {},
            onContentChanged = {},
            onThemeChanged = {},
            onExportMarkdown = {},
            onExportHtml = { _, _ -> },
            onExportPdf = { _, _ -> },
            onShareMarkdownText = {},
            onShareMarkdownFile = {},
            onAttachImage = {},
            onImageInsertionConsumed = {},
            onFlush = {},
            isExporting = false,
            showBackupHint = false,
            onDismissBackupHint = {},
            customThemes = listOf(customTheme),
            onCustomThemeSelected = { selectedThemeId = it },
        )
      }
    }

    compose.onNodeWithContentDescription(customTheme.name).performClick()
    compose
        .onNodeWithText(context.getString(R.string.markdown_theme_import_json))
        .assertIsDisplayed()
    compose
        .onNodeWithText(context.getString(R.string.markdown_theme_export_json))
        .assertIsDisplayed()
    compose.onNodeWithText("Ocean").performClick()
    compose.runOnIdle { assertEquals(customTheme.id, selectedThemeId) }
  }

  @Test
  fun notesMenuOffersMarkdownImport() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    compose.setContent {
      MaterialTheme {
        NotesScreen(
            state = NotesUiState(),
            onQueryChanged = {},
            onCreate = {},
            onOpenNote = {},
            onDeleteNote = {},
            onSortChanged = {},
            onImportMarkdown = {},
            onExportZip = {},
        )
      }
    }

    compose.onNodeWithContentDescription(context.getString(R.string.note_actions)).performClick()
    compose.onNodeWithText(context.getString(R.string.import_markdown)).assertIsDisplayed()
  }

  @Test
  fun editorExposesModeThemeAndToolbarActionsAndKeepsPreviewInSync() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var editor by
        mutableStateOf(
            NoteEditorUiState(
                id = 1L,
                title = "Note",
                content = "",
                theme = MarkdownThemeMode.GitHub,
            )
        )

    compose.setContent {
      MaterialTheme {
        NoteEditorScreen(
            editor = editor,
            appTheme = ThemeMode.System,
            onBack = {},
            onTitleChanged = { editor = editor.copy(title = it) },
            onContentChanged = { editor = editor.copy(content = it) },
            onThemeChanged = { editor = editor.copy(theme = it) },
            onExportMarkdown = {},
            onExportHtml = { _, _ -> },
            onExportPdf = { _, _ -> },
            onShareMarkdownText = {},
            onShareMarkdownFile = {},
            onAttachImage = {},
            onImageInsertionConsumed = { reference ->
              if (editor.pendingImageInsertion?.markdownReference == reference) {
                editor = editor.copy(pendingImageInsertion = null)
              }
            },
            onFlush = {},
            isExporting = false,
            showBackupHint = false,
            onDismissBackupHint = {},
        )
      }
    }

    compose.onNodeWithContentDescription(context.getString(R.string.note_edit)).assertIsDisplayed()
    compose
        .onNodeWithContentDescription(context.getString(R.string.note_preview))
        .assertIsDisplayed()
    compose.onNodeWithContentDescription(context.getString(R.string.note_theme)).assertIsDisplayed()
    compose.onNodeWithContentDescription(context.getString(R.string.note_actions)).performClick()
    compose.onNodeWithText(context.getString(R.string.export_pdf)).assertIsDisplayed()
    // Close the dropdown explicitly before exercising the editor below it.
    compose.onNodeWithContentDescription(context.getString(R.string.note_edit)).performClick()
    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_undo))
        .assertIsNotEnabled()
    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_redo))
        .assertIsNotEnabled()

    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_heading))
        .performClick()
    compose.runOnIdle { assertEquals("# text", editor.content) }
    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_undo))
        .assertIsEnabled()
        .performClick()
    compose.runOnIdle { assertEquals("", editor.content) }
    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_undo))
        .assertIsNotEnabled()
    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_redo))
        .assertIsEnabled()
        .performClick()
    compose.runOnIdle { assertEquals("# text", editor.content) }

    compose.onNodeWithContentDescription(context.getString(R.string.note_preview)).performClick()
    compose.waitForIdle()
    compose.waitUntil(2_000L) {
      compose
          .onAllNodesWithText("text", substring = true, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose.onNodeWithText("text", substring = true, useUnmergedTree = true).assertIsDisplayed()

    val imageReference = "xpod-attachment://01234567-89ab-cdef-0123-456789abcdef.png"
    compose.runOnIdle {
      editor =
          editor.copy(
              pendingImageInsertion = NoteImageInsertion(imageReference, "photo"),
          )
    }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("# text![photo]($imageReference)", editor.content)
      assertEquals(null, editor.pendingImageInsertion)
    }
  }

  @Test
  fun loadingScreenExposesProgressIndicator() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    compose.setContent { MaterialTheme { NotesLoadingScreen() } }

    compose
        .onNodeWithContentDescription(context.getString(R.string.loading_note))
        .assertIsDisplayed()
  }

  @Test
  fun editorToolbarAndContentRemainUsableInKeyboardSizedViewport() {
    val editor = mutableStateOf(defaultEditor())
    val viewportHeight = mutableStateOf(360.dp)
    renderEditor(editor, viewportHeight)

    compose
        .onNodeWithTag(NOTE_CONTENT_EDITOR_TEST_TAG)
        .assertIsDisplayed()
        .performClick()
        .performTextInput("draft")
    compose
        .onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getString(R.string.notes_toolbar_heading)
        )
        .assertIsDisplayed()
        .performClick()

    compose.runOnIdle { assertEquals("draft# text", editor.value.content) }
  }

  @Test
  fun editorPreservesCaretWhenAvailableHeightChanges() {
    val editor = mutableStateOf(defaultEditor())
    val viewportHeight = mutableStateOf(640.dp)
    renderEditor(editor, viewportHeight)

    val contentEditor = compose.onNodeWithTag(NOTE_CONTENT_EDITOR_TEST_TAG)
    contentEditor.performClick()
    contentEditor.performTextInput("leftright")
    contentEditor.performTextInputSelection(TextRange(4))
    compose.runOnIdle { viewportHeight.value = 360.dp }
    compose.waitForIdle()

    compose
        .onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getString(R.string.notes_toolbar_heading)
        )
        .assertIsDisplayed()
        .performClick()

    compose.runOnIdle { assertEquals("left# textright", editor.value.content) }
  }

  private fun renderEditor(
      editor: MutableState<NoteEditorUiState>,
      viewportHeight: MutableState<Dp>,
  ) {
    compose.setContent {
      MaterialTheme {
        Box(Modifier.fillMaxWidth().height(viewportHeight.value)) {
          NoteEditorScreen(
              editor = editor.value,
              appTheme = ThemeMode.System,
              onBack = {},
              onTitleChanged = { editor.value = editor.value.copy(title = it) },
              onContentChanged = { editor.value = editor.value.copy(content = it) },
              onThemeChanged = { editor.value = editor.value.copy(theme = it) },
              onExportMarkdown = {},
              onExportHtml = { _, _ -> },
              onExportPdf = { _, _ -> },
              onShareMarkdownText = {},
              onShareMarkdownFile = {},
              onAttachImage = {},
              onImageInsertionConsumed = {},
              onFlush = {},
              isExporting = false,
              showBackupHint = false,
              onDismissBackupHint = {},
          )
        }
      }
    }
  }

  private fun defaultEditor() =
      NoteEditorUiState(
          id = 42L,
          title = "Viewport test",
          content = "",
          theme = MarkdownThemeMode.GitHub,
      )
}
