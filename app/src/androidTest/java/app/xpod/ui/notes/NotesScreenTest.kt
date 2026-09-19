package app.xpod.ui.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.R
import app.xpod.data.MarkdownThemeMode
import app.xpod.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {
  @get:Rule val compose = createComposeRule()

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
            onShareMarkdownText = {},
            onShareMarkdownFile = {},
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

    compose
        .onNodeWithContentDescription(context.getString(R.string.notes_toolbar_heading))
        .performClick()
    compose.runOnIdle { assertEquals("# text", editor.content) }

    compose.onNodeWithContentDescription(context.getString(R.string.note_preview)).performClick()
    compose.onNodeWithText("text", substring = true).assertIsDisplayed()
  }

  @Test
  fun loadingScreenExposesProgressIndicator() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    compose.setContent { MaterialTheme { NotesLoadingScreen() } }

    compose
        .onNodeWithContentDescription(context.getString(R.string.loading_note))
        .assertIsDisplayed()
  }
}
