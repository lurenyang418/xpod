package app.xpod.ui.shell

import app.xpod.data.MarkdownThemeMode
import app.xpod.ui.notes.NoteEditorUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesRouteStateTest {
  @Test
  fun loadingIsShownUntilTheSelectedNoteEditorIsReady() {
    val loadedEditor =
        NoteEditorUiState(
            id = 10L,
            title = "Note",
            content = "",
            theme = MarkdownThemeMode.FollowApp,
        )

    assertTrue(shouldShowNotesLoading(selectedNoteId = 10L, editor = null))
    assertTrue(shouldShowNotesLoading(selectedNoteId = 11L, editor = loadedEditor))
    assertFalse(shouldShowNotesLoading(selectedNoteId = 10L, editor = loadedEditor))
    assertFalse(shouldShowNotesLoading(selectedNoteId = null, editor = loadedEditor))
  }
}
