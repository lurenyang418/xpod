package app.xpod.ui.notes

import app.xpod.data.LocalMarkdownNoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownNoteSortTest {
  @Test
  fun titleSortUsesHeadingAndLocalizedUntitledFallbacks() {
    val notes =
        listOf(
            note(id = 1L, title = "", content = "No heading"),
            note(id = 2L, title = "", content = "# Beta"),
            note(id = 3L, title = "Alpha", content = "Body"),
        )

    val sortedIds = notes.sortedWith(noteComparator(NoteSort.Title, "Untitled note")).map { it.id }

    assertEquals(listOf(3L, 2L, 1L), sortedIds)
  }

  private fun note(id: Long, title: String, content: String) =
      LocalMarkdownNoteEntity(
          id = id,
          title = title,
          content = content,
          createdEpochMs = id,
          modifiedEpochMs = id,
      )
}
