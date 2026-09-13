package app.xpod.ui

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.data.BookContentRepository
import app.xpod.data.BookFormat
import app.xpod.data.LocalBookEntity
import app.xpod.data.LocalBooksRepository
import app.xpod.data.ReadingPreferencesRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.XpodDatabase
import app.xpod.data.reader.EpubBook
import app.xpod.data.reader.EpubBookParser
import app.xpod.data.reader.EpubPosition
import app.xpod.data.reader.EpubSpineItem
import app.xpod.data.reader.ReaderBlock
import app.xpod.data.reader.ReaderChapter
import app.xpod.data.reader.ReaderPositionCodec
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookReaderViewModelTest {
  private lateinit var database: XpodDatabase
  private lateinit var settings: SettingsRepository
  private lateinit var localBooks: LocalBooksRepository
  private lateinit var applicationScope: CoroutineScope
  private lateinit var viewModel: BookReaderViewModel

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, XpodDatabase::class.java).build()
    settings = SettingsRepository(context)
    localBooks =
        LocalBooksRepository(
            context = context,
            database = database,
            settings = settings,
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC),
            epubParser = EpubBookParser(File(context.cacheDir, "reader-view-model-epub-test")),
        )
    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @After
  fun tearDown() {
    viewModel.closeReaderResources()
    applicationScope.cancel()
    database.close()
  }

  @Test
  fun failedOpenCanBeRetriedAndEventuallySucceeds() =
      runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bookId = "book:reader-retry"
        database
            .localBooks()
            .upsertAll(
                listOf(
                    LocalBookEntity(
                        id = bookId,
                        documentUri = "content://books/document/retry",
                        treeUri = "content://books/tree/root",
                        title = "Retry Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 1L,
                    )
                )
            )
        val content =
            FailingThenOpeningContent(
                localBooks,
                EpubBookParser(File(context.cacheDir, "reader-retry-content-epub-test")),
            )
        viewModel =
            BookReaderViewModel(
                localBooks = localBooks,
                content = content,
                readingPreferences = ReadingPreferencesRepository(context),
                context = context,
                applicationScope = applicationScope,
            )

        viewModel.openBook(bookId)
        val failed = awaitState { it.bookId == bookId && !it.isLoading && it.error != null }
        assertNotNull(failed.error)
        assertEquals(null, failed.book)

        viewModel.openBook(bookId)
        val opened = awaitState {
          it.bookId == bookId && !it.isLoading && it.error == null && it.book?.id == bookId
        }
        assertNotNull(opened.epub)
        assertNotNull(opened.chapter)
        assertEquals(2, content.openAttempts)
      }

  @Test
  fun failedChapterSelectionDoesNotPersistTargetChapter() =
      runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bookId = "book:chapter-failure"
        database
            .localBooks()
            .upsertAll(
                listOf(
                    LocalBookEntity(
                        id = bookId,
                        documentUri = "content://books/document/chapter-failure",
                        treeUri = "content://books/tree/root",
                        title = "Chapter Failure Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 1L,
                    )
                )
            )
        val content =
            FailingThenOpeningContent(
                localBooks,
                EpubBookParser(File(context.cacheDir, "chapter-failure-content-epub-test")),
                failFirst = false,
                chapterCount = 2,
                failingChapterIndex = 1,
            )
        viewModel =
            BookReaderViewModel(
                localBooks = localBooks,
                content = content,
                readingPreferences = ReadingPreferencesRepository(context),
                context = context,
                applicationScope = applicationScope,
            )

        viewModel.openBook(bookId)
        awaitState { it.bookId == bookId && !it.isLoading && it.book?.id == bookId }

        viewModel.selectChapter(1)
        val failed = awaitState { it.bookId == bookId && !it.isLoading && it.error != null }

        assertEquals(0, failed.position.spineIndex)
        assertEquals(null, localBooks.progress(bookId))
      }

  @Test
  fun epubProgressRemainsCurrentAfterAutomaticFlush() =
      runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bookId = "book:reader-progress"
        database
            .localBooks()
            .upsertAll(
                listOf(
                    LocalBookEntity(
                        id = bookId,
                        documentUri = "content://books/document/progress",
                        treeUri = "content://books/tree/root",
                        title = "Progress Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 1L,
                    )
                )
            )
        val content =
            FailingThenOpeningContent(
                localBooks,
                EpubBookParser(File(context.cacheDir, "reader-progress-content-epub-test")),
                failFirst = false,
            )
        viewModel =
            BookReaderViewModel(
                localBooks = localBooks,
                content = content,
                readingPreferences = ReadingPreferencesRepository(context),
                context = context,
                applicationScope = applicationScope,
            )

        viewModel.openBook(bookId)
        awaitState { it.bookId == bookId && !it.isLoading && it.book?.id == bookId }

        val expected = EpubPosition(blockIndex = 2, offsetPx = 17)
        viewModel.recordProgress(bookId, expected)
        awaitState { it.position.blockIndex == expected.blockIndex && it.position.offsetPx == 17 }
        awaitSavedPosition(bookId, expected)

        viewModel.flushProgress()
        assertEquals(expected, awaitSavedPosition(bookId, expected))
      }

  @Test
  fun changedSourceDoesNotRestoreOldProgress() =
      runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bookId = "book:changed-source"
        database
            .localBooks()
            .upsertAll(
                listOf(
                    LocalBookEntity(
                        id = bookId,
                        documentUri = "content://books/document/changed",
                        treeUri = "content://books/tree/root",
                        title = "Changed Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 2L,
                    )
                )
            )
        localBooks.recordProgress(
            bookId = bookId,
            positionVersion = 1,
            positionJson = ReaderPositionCodec.encode(EpubPosition(blockIndex = 7, offsetPx = 9)),
            sourceModifiedEpochMs = 1L,
            readingSeconds = 42L,
        )
        val content =
            FailingThenOpeningContent(
                localBooks,
                EpubBookParser(File(context.cacheDir, "changed-source-content-epub-test")),
                failFirst = false,
            )
        viewModel =
            BookReaderViewModel(
                localBooks = localBooks,
                content = content,
                readingPreferences = ReadingPreferencesRepository(context),
                context = context,
                applicationScope = applicationScope,
            )

        viewModel.openBook(bookId)
        val opened = awaitState { it.bookId == bookId && !it.isLoading && it.book?.id == bookId }

        assertEquals(EpubPosition(), opened.position)
        assertEquals(0L, opened.readingSeconds)
      }

  @Test
  fun staleProgressFromPreviousBookCannotBeAppliedToCurrentBook() =
      runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstBookId = "book:first"
        val secondBookId = "book:second"
        database
            .localBooks()
            .upsertAll(
                listOf(
                    LocalBookEntity(
                        id = firstBookId,
                        documentUri = "content://books/document/first",
                        treeUri = "content://books/tree/root",
                        title = "First Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 1L,
                    ),
                    LocalBookEntity(
                        id = secondBookId,
                        documentUri = "content://books/document/second",
                        treeUri = "content://books/tree/root",
                        title = "Second Book",
                        author = "Author",
                        language = "en",
                        format = BookFormat.EPUB.name,
                        fileSizeBytes = 10L,
                        modifiedEpochMs = 1L,
                    ),
                )
            )
        val content =
            FailingThenOpeningContent(
                localBooks,
                EpubBookParser(File(context.cacheDir, "stale-progress-content-epub-test")),
                failFirst = false,
            )
        viewModel =
            BookReaderViewModel(
                localBooks = localBooks,
                content = content,
                readingPreferences = ReadingPreferencesRepository(context),
                context = context,
                applicationScope = applicationScope,
            )

        viewModel.openBook(firstBookId)
        awaitState { it.bookId == firstBookId && !it.isLoading && it.book?.id == firstBookId }
        viewModel.openBook(secondBookId)
        awaitState { it.bookId == secondBookId && !it.isLoading && it.book?.id == secondBookId }

        viewModel.recordProgress(firstBookId, EpubPosition(blockIndex = 3, offsetPx = 12))
        delay(1_000L)

        assertEquals(null, localBooks.progress(secondBookId))
        assertEquals(EpubPosition(), viewModel.state.value.position)
      }

  private suspend fun awaitState(predicate: (BookReaderUiState) -> Boolean): BookReaderUiState =
      try {
        withTimeout(5_000L) { viewModel.state.first(predicate) }
      } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("Timed out waiting for reader state: ${viewModel.state.value}", error)
      }

  private suspend fun awaitSavedPosition(bookId: String, expected: EpubPosition): EpubPosition =
      withTimeout(5_000L) {
        var savedPosition: EpubPosition? = null
        while (savedPosition == null) {
          val saved = ReaderPositionCodec.decodeEpub(localBooks.progress(bookId)?.positionJson)
          if (saved?.blockIndex == expected.blockIndex && saved.offsetPx == expected.offsetPx) {
            savedPosition = saved
          }
          delay(50L)
        }
        checkNotNull(savedPosition)
      }

  private class FailingThenOpeningContent(
      localBooks: LocalBooksRepository,
      epubParser: EpubBookParser,
      private val failFirst: Boolean = true,
      private val chapterCount: Int = 1,
      private val failingChapterIndex: Int? = null,
  ) : BookContentRepository(localBooks, epubParser) {
    var openAttempts = 0
      private set

    override suspend fun openEpub(bookId: String): EpubBook {
      openAttempts++
      if (failFirst && openAttempts == 1) error("synthetic EPUB open failure")
      return EpubBook(
          title = "Retry Book",
          author = "Author",
          language = "en",
          coverResourceKey = null,
          chapters =
              List(chapterCount) { index ->
                EpubSpineItem(
                    id = "chapter$index",
                    href = "chapter$index.xhtml",
                    title = "Chapter ${index + 1}",
                )
              },
      )
    }

    override suspend fun readChapter(
        bookId: String,
        spineItem: EpubSpineItem,
        spineIndex: Int,
    ): ReaderChapter {
      if (spineIndex == failingChapterIndex) error("synthetic chapter read failure")
      return ReaderChapter(
          spineIndex = spineIndex,
          title = spineItem.title,
          blocks = listOf(ReaderBlock.Text("Loaded after retry")),
      )
    }
  }
}
