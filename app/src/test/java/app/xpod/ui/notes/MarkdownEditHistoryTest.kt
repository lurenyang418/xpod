package app.xpod.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditHistoryTest {
  @Test
  fun groupsAdjacentTypingAndRestoresItAsOneUndoStep() {
    val history = MarkdownEditHistory(value(""))
    history.record(value("h"), nowMs = 0L)
    history.record(value("he"), nowMs = 100L)
    history.record(value("hello"), nowMs = 200L)

    assertEquals(value(""), history.undo())
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)
    assertEquals(value("hello"), history.redo())
  }

  @Test
  fun toolbarEditIsAnIndependentStepAndRestoresSelection() {
    val initial = value("leftright", selection = 4)
    val history = MarkdownEditHistory(initial)
    val inserted = value("left# textright", selection = 10)
    history.record(inserted, nowMs = 0L, coalesceWithPrevious = false)

    assertEquals(initial, history.undo())
    assertEquals(inserted, history.redo())
  }

  @Test
  fun editsAfterUndoClearRedoAndSeparatedTypingUsesSeparateSteps() {
    val history = MarkdownEditHistory(value(""))
    history.record(value("a"), nowMs = 0L)
    history.record(value("ab"), nowMs = 800L)

    assertEquals(value("a"), history.undo())
    assertEquals(value(""), history.undo())
    assertEquals(value("a"), history.redo())
    history.record(value("ax"), nowMs = 1_000L)

    assertFalse(history.canRedo)
    assertEquals(value("a"), history.undo())
  }

  @Test
  fun historyIsBoundedAndResetClearsBothStacks() {
    val history =
        MarkdownEditHistory(
            initialValue = value(""),
            maxSteps = 2,
            maxStoredCharacters = 100,
            mergeWindowMs = 0L,
        )
    history.record(value("a"), nowMs = 0L)
    history.record(value("ab"), nowMs = 1L)
    history.record(value("abc"), nowMs = 2L)

    assertEquals(value("ab"), history.undo())
    assertEquals(value("a"), history.undo())
    assertNull(history.undo())
    assertEquals(value("ab"), history.redo())

    history.reset(value("external"))
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertNull(history.undo())
  }

  private fun value(text: String, selection: Int = text.length) =
      TextFieldValue(text = text, selection = TextRange(selection))
}
