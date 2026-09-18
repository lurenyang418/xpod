package app.xpod.ui.books

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.BookContentRepository
import app.xpod.data.BookFormat
import app.xpod.data.LocalBooksRepository
import app.xpod.data.ReadingPreferences
import app.xpod.data.ReadingPreferencesRepository
import app.xpod.data.ReadingTheme
import app.xpod.data.UnsupportedBookFormatException
import app.xpod.data.reader.EpubBook
import app.xpod.data.reader.EpubPosition
import app.xpod.data.reader.PdfPosition
import app.xpod.data.reader.PdfRendererDocument
import app.xpod.data.reader.READER_POSITION_VERSION
import app.xpod.data.reader.ReaderChapter
import app.xpod.data.reader.ReaderPositionCodec
import app.xpod.data.reader.clampTo
import app.xpod.data.reader.withBookProgress
import app.xpod.data.reader.withProgress
import app.xpod.di.ApplicationScope
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookReaderUiState(
    val bookId: String? = null,
    val title: String = "",
    val book: app.xpod.data.LocalBookEntity? = null,
    val epub: EpubBook? = null,
    val chapter: ReaderChapter? = null,
    val position: EpubPosition = EpubPosition(),
    val pdfPosition: PdfPosition = PdfPosition(),
    val pdfPageCount: Int = 0,
    val readingSeconds: Long = 0,
    val preferences: ReadingPreferences = ReadingPreferences(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookReaderViewModel
@Inject
constructor(
    private val localBooks: LocalBooksRepository,
    private val content: BookContentRepository,
    private val readingPreferences: ReadingPreferencesRepository,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
  private val readerState = MutableStateFlow(BookReaderUiState())
  val state: StateFlow<BookReaderUiState> =
      combine(readerState, readingPreferences.preferences) { reader, preferences ->
            reader.copy(preferences = preferences)
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookReaderUiState())

  private var openJob: Job? = null
  private var progressJob: Job? = null
  private var cleanupJob: Job? = null
  private var pdfDocument: PdfRendererDocument? = null
  private val pendingProgress = AtomicReference<PendingProgress?>(null)
  private val resourceCache =
      object : LruCache<String, ByteArray>(MAX_RESOURCE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
      }
  private var readingSeconds = 0L
  private var readingSessionStartedAtElapsedMs: Long? = null

  fun openBook(bookId: String) {
    if (
        readerState.value.bookId == bookId &&
            readerState.value.isLoading &&
            openJob?.isActive == true
    ) {
      return
    }
    val previousOpenJob = openJob
    previousOpenJob?.cancel()
    openJob = null
    val previousProgressJob = progressJob
    previousProgressJob?.cancel()
    progressJob = null
    val previousDocument = pdfDocument
    pdfDocument = null
    val transitionCleanup =
        cleanupJob?.takeIf { it.isActive }
            ?: scheduleCleanup(previousOpenJob, previousProgressJob, previousDocument).also {
              cleanupJob = it
            }
    synchronized(resourceCache) { resourceCache.evictAll() }
    openJob = viewModelScope.launch {
      transitionCleanup.join()
      if (cleanupJob === transitionCleanup) cleanupJob = null
      pendingProgress.set(null)
      readingSeconds = 0L
      readingSessionStartedAtElapsedMs = null
      readerState.value =
          BookReaderUiState(
              bookId = bookId,
              isLoading = true,
              title = context.getString(R.string.books),
          )
      runCatchingCancellable {
            val book = localBooks.book(bookId) ?: error("Book not found")
            val saved =
                localBooks.progress(bookId)?.takeIf {
                  isProgressForCurrentSource(it.sourceModifiedEpochMs, book.modifiedEpochMs)
                }
            when (book.format) {
              BookFormat.EPUB.name -> {
                val epub = content.openEpub(bookId)
                val savedPosition =
                    ReaderPositionCodec.decodeEpub(saved?.positionJson)?.clampTo(epub.chapters.size)
                        ?: EpubPosition()
                val chapter =
                    content.readChapter(
                        bookId,
                        epub.chapters[savedPosition.spineIndex],
                        savedPosition.spineIndex,
                    )
                val position =
                    savedPosition.withBookProgress(chapter.blocks.size, epub.chapters.size)
                BookReaderUiState(
                    bookId = bookId,
                    title = book.title,
                    book = book,
                    epub = epub,
                    chapter = chapter,
                    position = position,
                    readingSeconds = saved?.readingSeconds ?: 0,
                )
              }
              BookFormat.PDF.name -> {
                val document = content.openPdfDocument(bookId)
                try {
                  val pageCount = document.pageCount
                  if (pageCount == 0) error("PDF has no pages")
                  val position =
                      ReaderPositionCodec.decodePdf(saved?.positionJson)?.withProgress(pageCount)
                          ?: PdfPosition()
                  pdfDocument = document
                  BookReaderUiState(
                      bookId = bookId,
                      title = book.title,
                      book = book,
                      pdfPosition = position,
                      pdfPageCount = pageCount,
                      readingSeconds = saved?.readingSeconds ?: 0,
                  )
                } catch (error: Throwable) {
                  document.close()
                  throw error
                }
              }
              else -> throw UnsupportedBookFormatException(book.format)
            }
          }
          .fold(
              { opened ->
                readingSeconds = opened.readingSeconds
                readerState.value = opened
                startReadingSession()
              },
              { error ->
                readerState.value =
                    BookReaderUiState(
                        bookId = bookId,
                        isLoading = false,
                        error = userFacingError(error),
                    )
                Log.e("XPOD", "Unable to open book $bookId", error)
              },
          )
    }
  }

  fun selectChapter(index: Int) {
    val current = readerState.value
    val epub = current.epub ?: return
    val bookId = current.bookId ?: return
    val validIndex = index.coerceIn(0, epub.chapters.lastIndex)
    if (validIndex == current.position.spineIndex && current.chapter != null) return
    openJob?.cancel()
    openJob = viewModelScope.launch {
      readerState.update { it.copy(isLoading = true, error = null) }
      runCatchingCancellable {
            content.readChapter(bookId, epub.chapters[validIndex], validIndex)
          }
          .fold(
              { chapter ->
                readerState.update {
                  it.copy(
                      chapter = chapter,
                      position =
                          it.position
                              .copy(
                                  spineIndex = validIndex,
                                  blockIndex = 0,
                                  offsetPx = 0,
                              )
                              .withBookProgress(chapter.blocks.size, epub.chapters.size),
                      isLoading = false,
                  )
                }
                recordProgress(
                    bookId,
                    EpubPosition(spineIndex = validIndex)
                        .withBookProgress(
                            chapter.blocks.size,
                            epub.chapters.size,
                        ),
                )
              },
              { error ->
                readerState.update { it.copy(isLoading = false, error = userFacingError(error)) }
              },
          )
    }
  }

  fun recordProgress(bookId: String, position: EpubPosition) {
    val current = readerState.value
    val epub = current.epub ?: return
    if (current.book?.id != bookId) return
    val normalized = position.clampTo(epub.chapters.size)
    readerState.update { state ->
      if (state.book?.id == bookId && state.epub != null) {
        state.copy(position = normalized)
      } else {
        state
      }
    }
    pendingProgress.set(PendingProgress(bookId, PendingPosition.Epub(normalized)))
    progressJob?.cancel()
    progressJob = viewModelScope.launch {
      delay(PROGRESS_DEBOUNCE_MS)
      flushProgressNow()
    }
  }

  fun recordPdfProgress(
      bookId: String,
      position: PdfPosition,
      isAtDocumentEnd: Boolean = false,
  ) {
    val current = readerState.value
    if (current.book?.id != bookId || current.pdfPageCount <= 0) return
    val normalized = position.withProgress(current.pdfPageCount, isAtDocumentEnd)
    readerState.update { it.copy(pdfPosition = normalized) }
    pendingProgress.set(PendingProgress(bookId, PendingPosition.Pdf(normalized)))
    progressJob?.cancel()
    progressJob = viewModelScope.launch {
      delay(PROGRESS_DEBOUNCE_MS)
      flushProgressNow()
    }
  }

  suspend fun renderPdfPage(pageIndex: Int, widthPx: Int, heightPx: Int): android.graphics.Bitmap? =
      withContext(Dispatchers.IO) {
        pdfDocument?.render(pageIndex, widthPx, heightPx)
      }

  fun flushProgress() {
    progressJob?.cancel()
    progressJob = viewModelScope.launch {
      flushProgressNow()
    }
  }

  fun stopReadingSession() {
    progressJob?.cancel()
    val hadSession = readingSessionStartedAtElapsedMs != null
    if (hadSession) {
      // Fold the elapsed time in synchronously so a quick return to the foreground can
      // start a fresh session without racing the asynchronous flush below.
      accumulateReadingTime()
      readingSessionStartedAtElapsedMs = null
    }
    if (pendingProgress.get() == null && !hadSession) return
    progressJob = viewModelScope.launch { flushProgressNow() }
  }

  fun startReadingSession() {
    if (readerState.value.book != null && readingSessionStartedAtElapsedMs == null) {
      readingSessionStartedAtElapsedMs = SystemClock.elapsedRealtime()
    }
  }

  fun closeReaderResources() {
    val previousOpenJob = openJob
    previousOpenJob?.cancel()
    openJob = null
    val previousProgressJob = progressJob
    previousProgressJob?.cancel()
    progressJob = null
    val previousDocument = pdfDocument
    pdfDocument = null
    if (cleanupJob?.isActive != true) {
      cleanupJob = scheduleCleanup(previousOpenJob, previousProgressJob, previousDocument)
    }
  }

  suspend fun loadResource(resourceKey: String): ByteArray? {
    val bookId = readerState.value.bookId ?: return null
    val cacheKey = "$bookId:$resourceKey"
    synchronized(resourceCache) { resourceCache.get(cacheKey) }
        ?.let {
          return it
        }
    return content.readResource(bookId, resourceKey)?.also { bytes ->
      synchronized(resourceCache) { resourceCache.put(cacheKey, bytes) }
    }
  }

  fun setFontSize(value: Float) {
    viewModelScope.launch { readingPreferences.setFontSizeSp(value) }
  }

  fun setLineHeight(value: Float) {
    viewModelScope.launch { readingPreferences.setLineHeightMultiplier(value) }
  }

  fun setTheme(value: ReadingTheme) {
    viewModelScope.launch { readingPreferences.setTheme(value) }
  }

  private suspend fun flushProgressNow() {
    val current = readerState.value
    val book = current.book ?: return
    val pending = pendingProgress.getAndSet(null)
    val position =
        when {
          pending == null -> currentPosition(current)
          pending.bookId == book.id -> pending.position
          else -> return
        } ?: return
    val totalReadingSeconds = accumulateReadingTime()
    runCatchingCancellable {
          when (position) {
            is PendingPosition.Epub ->
                localBooks.recordProgress(
                    bookId = book.id,
                    positionVersion = READER_POSITION_VERSION,
                    positionJson = ReaderPositionCodec.encode(position.position),
                    sourceModifiedEpochMs = book.modifiedEpochMs,
                    readingSeconds = totalReadingSeconds,
                )
            is PendingPosition.Pdf ->
                localBooks.recordProgress(
                    bookId = book.id,
                    positionVersion = READER_POSITION_VERSION,
                    positionJson = ReaderPositionCodec.encode(position.position),
                    sourceModifiedEpochMs = book.modifiedEpochMs,
                    readingSeconds = totalReadingSeconds,
                )
          }
        }
        .onFailure {
          pendingProgress.compareAndSet(null, pending ?: PendingProgress(book.id, position))
        }
  }

  override fun onCleared() {
    closeReaderResources()
    super.onCleared()
  }

  private companion object {
    const val PROGRESS_DEBOUNCE_MS = 800L
    const val MAX_RESOURCE_CACHE_BYTES = 16 * 1024 * 1024
  }

  private fun currentPosition(state: BookReaderUiState): PendingPosition? =
      when {
        state.epub != null -> PendingPosition.Epub(state.position)
        state.pdfPageCount > 0 -> PendingPosition.Pdf(state.pdfPosition)
        else -> null
      }

  private fun accumulateReadingTime(): Long {
    val started = readingSessionStartedAtElapsedMs ?: return readingSeconds
    val elapsedMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
    readingSeconds += elapsedMs / 1_000L
    readingSessionStartedAtElapsedMs =
        SystemClock.elapsedRealtime() - (elapsedMs % 1_000L).coerceAtLeast(0L)
    return readingSeconds
  }

  private fun userFacingError(error: Throwable): String =
      if (error is UnsupportedBookFormatException) {
        context.getString(R.string.unsupported_format)
      } else {
        context.getString(R.string.could_not_open_book)
      }

  private fun isProgressForCurrentSource(
      savedModifiedEpochMs: Long,
      currentModifiedEpochMs: Long,
  ): Boolean =
      savedModifiedEpochMs <= 0L ||
          currentModifiedEpochMs <= 0L ||
          savedModifiedEpochMs == currentModifiedEpochMs

  private fun scheduleCleanup(
      oldOpenJob: Job?,
      oldProgressJob: Job?,
      oldDocument: PdfRendererDocument?,
  ): Job =
      applicationScope.launch(Dispatchers.IO) {
        oldOpenJob?.join()
        oldProgressJob?.join()
        runCatching { flushProgressNow() }
        readingSessionStartedAtElapsedMs = null
        runCatching { oldDocument?.close() }
        runCatching { pdfDocument?.close() }
        pdfDocument = null
        runCatching { content.closeActiveEpub() }
      }
}

private data class PendingProgress(val bookId: String, val position: PendingPosition)

private sealed interface PendingPosition {
  data class Epub(val position: EpubPosition) : PendingPosition

  data class Pdf(val position: PdfPosition) : PendingPosition
}
