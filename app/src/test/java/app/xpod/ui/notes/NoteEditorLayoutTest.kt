package app.xpod.ui.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorLayoutTest {
  @Test
  fun compactWritingModeRequiresLandscapeCompactViewportAndFocusedBody() {
    assertTrue(
        shouldUseCompactLandscapeWritingMode(
            isLandscape = true,
            isCompactViewport = true,
            contentEditorFocused = true,
        )
    )
    assertFalse(
        shouldUseCompactLandscapeWritingMode(
            isLandscape = false,
            isCompactViewport = true,
            contentEditorFocused = true,
        )
    )
    assertFalse(
        shouldUseCompactLandscapeWritingMode(
            isLandscape = true,
            isCompactViewport = false,
            contentEditorFocused = true,
        )
    )
    assertFalse(
        shouldUseCompactLandscapeWritingMode(
            isLandscape = true,
            isCompactViewport = true,
            contentEditorFocused = false,
        )
    )
  }
}
