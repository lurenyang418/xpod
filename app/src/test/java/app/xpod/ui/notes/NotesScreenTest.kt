package app.xpod.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesScreenTest {
  @Test
  fun insertMarkdownReplacesSelectionAndPlacesCursorBeforeSuffix() {
    val value = TextFieldValue("before after", selection = TextRange(7, 12))

    val result = insertMarkdownAtSelection(value, "**", "**", "text")

    assertEquals("before **text**", result.text)
    assertEquals(TextRange(13), result.selection)
  }

  @Test
  fun insertMarkdownKeepsCursorAfterInsertedPlaceholderAtEnd() {
    val value = TextFieldValue("before", selection = TextRange(6))

    val result = insertMarkdownAtSelection(value, "# ", "", "text")

    assertEquals("before# text", result.text)
    assertEquals(TextRange(12), result.selection)
  }
}
