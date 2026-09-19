package app.xpod.ui.notes

import androidx.compose.ui.text.input.TextFieldValue

/** Bounded editor history that keeps cursor/selection state with each content snapshot. */
internal class MarkdownEditHistory(
    initialValue: TextFieldValue,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val maxStoredCharacters: Int = DEFAULT_MAX_STORED_CHARACTERS,
    private val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS,
) {
  private val undoStack = ArrayDeque<TextFieldValue>()
  private val redoStack = ArrayDeque<TextFieldValue>()
  private var undoCharacters = 0
  private var redoCharacters = 0
  private var currentValue = initialValue
  private var lastEditKind: EditKind? = null
  private var lastEditTimeMs: Long? = null
  private var lastEditCaret: Int? = null

  init {
    require(maxSteps > 0)
    require(maxStoredCharacters > 0)
    require(mergeWindowMs >= 0L)
  }

  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  /**
   * Records an edit. Adjacent typing or deletion in the text field is grouped while the caret
   * remains continuous and edits arrive within [mergeWindowMs]. Toolbar edits form their own step.
   */
  fun record(
      nextValue: TextFieldValue,
      nowMs: Long,
      coalesceWithPrevious: Boolean = true,
  ) {
    if (nextValue == currentValue) return

    if (nextValue.text == currentValue.text) {
      if (nextValue.selection != currentValue.selection) clearMergeGroup()
      currentValue = nextValue
      return
    }

    val editKind = if (coalesceWithPrevious) classifyEdit(currentValue, nextValue) else null
    val canMerge =
        editKind != null &&
            editKind == lastEditKind &&
            lastEditTimeMs?.let { nowMs >= it && nowMs - it <= mergeWindowMs } == true &&
            currentValue.selection.collapsed &&
            currentValue.selection.start == lastEditCaret

    if (!canMerge) pushUndo(currentValue)
    clearRedo()
    currentValue = nextValue

    if (editKind == null) {
      clearMergeGroup()
    } else {
      lastEditKind = editKind
      lastEditTimeMs = nowMs
      lastEditCaret = nextValue.selection.start.takeIf { nextValue.selection.collapsed }
    }
  }

  fun undo(): TextFieldValue? {
    if (undoStack.isEmpty()) return null
    val previous = undoStack.removeLast()
    undoCharacters -= previous.text.length
    pushRedo(currentValue)
    currentValue = previous
    clearMergeGroup()
    return previous
  }

  fun redo(): TextFieldValue? {
    if (redoStack.isEmpty()) return null
    val next = redoStack.removeLast()
    redoCharacters -= next.text.length
    pushUndo(currentValue)
    currentValue = next
    clearMergeGroup()
    return next
  }

  /** Replaces the editor contents from an external source and discards stale history. */
  fun reset(value: TextFieldValue) {
    undoStack.clear()
    redoStack.clear()
    undoCharacters = 0
    redoCharacters = 0
    currentValue = value
    clearMergeGroup()
  }

  private fun pushUndo(value: TextFieldValue) {
    undoStack.addLast(value)
    undoCharacters += value.text.length
    while (
        undoStack.size > maxSteps || (undoCharacters > maxStoredCharacters && undoStack.size > 1)
    ) {
      undoCharacters -= undoStack.removeFirst().text.length
    }
  }

  private fun pushRedo(value: TextFieldValue) {
    redoStack.addLast(value)
    redoCharacters += value.text.length
    while (
        redoStack.size > maxSteps || (redoCharacters > maxStoredCharacters && redoStack.size > 1)
    ) {
      redoCharacters -= redoStack.removeFirst().text.length
    }
  }

  private fun clearRedo() {
    redoStack.clear()
    redoCharacters = 0
  }

  private fun clearMergeGroup() {
    lastEditKind = null
    lastEditTimeMs = null
    lastEditCaret = null
  }

  private fun classifyEdit(before: TextFieldValue, after: TextFieldValue): EditKind? {
    if (!before.selection.collapsed || !after.selection.collapsed) return null
    val lengthChange = after.text.length - before.text.length
    return when {
      lengthChange > 0 && after.selection.start == before.selection.start + lengthChange ->
          EditKind.Insert
      lengthChange < 0 &&
          (after.selection.start == before.selection.start + lengthChange ||
              after.selection.start == before.selection.start) -> EditKind.Delete
      else -> null
    }
  }

  private enum class EditKind {
    Insert,
    Delete,
  }

  private companion object {
    const val DEFAULT_MAX_STEPS = 50
    const val DEFAULT_MAX_STORED_CHARACTERS = 1_000_000
    const val DEFAULT_MERGE_WINDOW_MS = 750L
  }
}
