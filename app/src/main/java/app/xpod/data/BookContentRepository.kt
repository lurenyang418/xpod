package app.xpod.data

import app.xpod.data.reader.EpubArchive
import app.xpod.data.reader.EpubBook
import app.xpod.data.reader.EpubBookParser
import app.xpod.data.reader.EpubSpineItem
import app.xpod.data.reader.PdfRendererDocument
import app.xpod.data.reader.ReaderChapter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UnsupportedBookFormatException(val actualFormat: String) :
    IllegalArgumentException("Unsupported book format: $actualFormat")

@Singleton
open class BookContentRepository
@Inject
constructor(
    private val localBooks: LocalBooksRepository,
    private val epubParser: EpubBookParser,
) {
  private val archiveMutex = Mutex()
  private var activeEpubBookId: String? = null
  private var activeEpubArchive: EpubArchive? = null

  open suspend fun openEpub(bookId: String): EpubBook {
    requireBookFormat(bookId, BookFormat.EPUB)
    return withEpubArchive(bookId) { archive -> epubParser.parseMetadata(archive) }
  }

  open suspend fun readChapter(
      bookId: String,
      spineItem: EpubSpineItem,
      spineIndex: Int,
  ): ReaderChapter {
    requireBookFormat(bookId, BookFormat.EPUB)
    return withEpubArchive(bookId) { archive ->
      epubParser.readChapter(
          archive = archive,
          spineItem = spineItem,
          spineIndex = spineIndex,
      )
    }
  }

  open suspend fun readResource(bookId: String, resourceKey: String): ByteArray? {
    requireBookFormat(bookId, BookFormat.EPUB)
    return withEpubArchive(bookId) { archive -> epubParser.readResource(archive, resourceKey) }
  }

  suspend fun closeBook(bookId: String) {
    archiveMutex.withLock {
      if (activeEpubBookId == bookId) {
        closeActiveEpubLocked()
      }
    }
  }

  suspend fun closeActiveEpub() {
    archiveMutex.withLock { closeActiveEpubLocked() }
  }

  suspend fun openFileDescriptor(bookId: String): android.os.ParcelFileDescriptor =
      withContext(Dispatchers.IO) { localBooks.openFileDescriptor(bookId) }

  suspend fun openPdfDocument(bookId: String): PdfRendererDocument {
    requireBookFormat(bookId, BookFormat.PDF)
    return withContext(Dispatchers.IO) {
      PdfRendererDocument.open(localBooks.openFileDescriptor(bookId))
    }
  }

  private suspend fun requireBookFormat(bookId: String, expected: BookFormat) {
    val actual = localBooks.book(bookId)?.format ?: error("Book not found: $bookId")
    if (actual != expected.name) throw UnsupportedBookFormatException(actual)
  }

  private suspend fun <T> withEpubArchive(
      bookId: String,
      block: suspend (EpubArchive) -> T,
  ): T =
      withContext(Dispatchers.IO) {
        archiveMutex.withLock {
          val archive =
              if (activeEpubBookId == bookId && activeEpubArchive != null) {
                activeEpubArchive!!
              } else {
                activeEpubArchive?.close()
                epubParser
                    .openArchive { localBooks.openInputStream(bookId) }
                    .also {
                      activeEpubBookId = bookId
                      activeEpubArchive = it
                    }
              }
          try {
            block(archive)
          } catch (error: Throwable) {
            if (activeEpubArchive === archive) closeActiveEpubLocked()
            throw error
          }
        }
      }

  private fun closeActiveEpubLocked() {
    activeEpubArchive?.close()
    activeEpubBookId = null
    activeEpubArchive = null
  }
}
