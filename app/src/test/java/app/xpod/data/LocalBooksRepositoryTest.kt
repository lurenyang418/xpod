package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBooksRepositoryTest {
  @Test
  fun identifiesSupportedFormatsByMimeOrExtensionIgnoringCase() {
    assertEquals(BookFormat.EPUB, bookFormatFor("application/epub+zip", "book.bin"))
    assertEquals(BookFormat.EPUB, bookFormatFor("application/octet-stream", "BOOK.EPUB"))
    assertEquals(BookFormat.PDF, bookFormatFor("application/pdf", "book.bin"))
    assertEquals(BookFormat.PDF, bookFormatFor("application/octet-stream", "BOOK.PdF"))
    assertNull(bookFormatFor("text/plain", "book.txt"))
  }

  @Test
  fun stableIdUsesProviderAndDocumentId() {
    assertEquals(localBookId("provider", "document"), localBookId("provider", "document"))
    assertNotEquals(localBookId("provider", "document"), localBookId("other", "document"))
    assertNotEquals(localBookId("provider", "document"), localBookId("provider", "other"))
  }

  @Test
  fun titleFallbackRemovesOnlyTheFinalExtension() {
    assertEquals("A Book.v2", bookTitleFrom("A Book.v2.epub"))
    assertEquals("Untitled book", bookTitleFrom(".epub"))
  }

  @Test
  fun removedScannedCoverDoesNotReuseExistingCache() {
    assertNull(mergeCoverCachePath(null, "/covers/book.jpg") { true })
  }

  @Test
  fun existingCoverCacheIsReusedOnlyWhenScannedBookStillHasCover() {
    assertEquals(
        "/covers/book.jpg",
        mergeCoverCachePath("/covers/book.jpg", "/covers/book.jpg") { true },
    )
    assertEquals(
        "/covers/new-book.jpg",
        mergeCoverCachePath("/covers/new-book.jpg", "/covers/book.jpg") { false },
    )
  }
}
