package app.xpod.ui

import app.xpod.data.BookFormat
import app.xpod.data.BookWithProgress
import app.xpod.data.reader.EpubPosition
import app.xpod.data.reader.ReaderPositionCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class BooksViewModelTest {
  @Test
  fun changedSourceDoesNotShowPersistedProgress() {
    val item = bookWithProgress(sourceModifiedEpochMs = 10L, modifiedEpochMs = 11L)

    assertEquals(0f, progressFraction(item), 0.0001f)
  }

  @Test
  fun matchingSourceShowsPersistedProgress() {
    val item = bookWithProgress(sourceModifiedEpochMs = 10L, modifiedEpochMs = 10L)

    assertEquals(0.75f, progressFraction(item), 0.0001f)
  }

  private fun bookWithProgress(sourceModifiedEpochMs: Long, modifiedEpochMs: Long) =
      BookWithProgress(
          id = "book:test",
          documentUri = "content://books/document/test",
          treeUri = "content://books/tree/root",
          title = "Test Book",
          author = "Author",
          language = "en",
          format = BookFormat.EPUB.name,
          fileSizeBytes = 1L,
          modifiedEpochMs = modifiedEpochMs,
          relativePath = "",
          coverCachePath = null,
          addedEpochMs = 1L,
          lastOpenedEpochMs = 2L,
          isFavorite = false,
          positionVersion = 1,
          positionJson =
              ReaderPositionCodec.encode(
                  EpubPosition(spineIndex = 1, blockIndex = 1, percent = 0.75f)
              ),
          sourceModifiedEpochMs = sourceModifiedEpochMs,
          progressUpdatedEpochMs = 2L,
          readingSeconds = 1L,
      )
}
