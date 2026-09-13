package app.xpod.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.BookFormat
import app.xpod.data.BookWithProgress
import app.xpod.data.LocalBooksRepository
import app.xpod.data.reader.ReaderPositionCodec
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookFilter {
  All,
  Recent,
  Favorites,
  Epub,
  Pdf,
}

enum class BookSort {
  Title,
  Author,
  Recent,
  Added,
}

data class BookListItem(
    val book: BookWithProgress,
    val progressFraction: Float,
) {
  val format: BookFormat?
    get() = BookFormat.entries.firstOrNull { it.name == book.format }
}

data class BooksUiState(
    val books: List<BookListItem> = emptyList(),
    val treeUri: String? = null,
    val query: String = "",
    val filter: BookFilter = BookFilter.All,
    val sort: BookSort = BookSort.Title,
    val isScanning: Boolean = false,
)

@HiltViewModel
class BooksViewModel
@Inject
constructor(
    private val localBooks: LocalBooksRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val query = MutableStateFlow("")
  private val filter = MutableStateFlow(BookFilter.All)
  private val sort = MutableStateFlow(BookSort.Title)
  private val scanning = MutableStateFlow(false)
  private var scanJob: Job? = null
  private val _status = MutableStateFlow<UiStatus?>(null)
  val status: StateFlow<UiStatus?> = _status

  val state: StateFlow<BooksUiState> =
      combine(localBooks.books, localBooks.treeUri, query, filter, sort, scanning) { values ->
            @Suppress("UNCHECKED_CAST") val books = values[0] as List<BookWithProgress>
            val treeUri = values[1] as String?
            val requestedQuery = values[2] as String
            val requestedFilter = values[3] as BookFilter
            val requestedSort = values[4] as BookSort
            val isScanning = values[5] as Boolean
            val normalizedQuery = requestedQuery.trim()
            val filtered =
                books
                    .asSequence()
                    .filter { item ->
                      when (requestedFilter) {
                        BookFilter.All -> true
                        BookFilter.Recent -> item.lastOpenedEpochMs > 0L
                        BookFilter.Favorites -> item.isFavorite
                        BookFilter.Epub -> item.format.equals(BookFormat.EPUB.name, true)
                        BookFilter.Pdf -> item.format.equals(BookFormat.PDF.name, true)
                      }
                    }
                    .filter { item ->
                      normalizedQuery.isBlank() ||
                          item.title.contains(normalizedQuery, ignoreCase = true) ||
                          item.author.contains(normalizedQuery, ignoreCase = true) ||
                          item.relativePath.contains(normalizedQuery, ignoreCase = true)
                    }
                    .sortedWith(bookComparator(requestedSort))
                    .map { item ->
                      BookListItem(
                          book = item,
                          progressFraction = progressFraction(item),
                      )
                    }
                    .toList()
            BooksUiState(
                books = filtered,
                treeUri = treeUri,
                query = requestedQuery,
                filter = requestedFilter,
                sort = requestedSort,
                isScanning = isScanning,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BooksUiState())

  fun dismissStatus() {
    _status.value = null
  }

  fun selectBookFolder(uri: Uri) {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { scan { localBooks.selectTree(uri) } }
  }

  fun refresh() {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { scan { localBooks.refresh() } }
  }

  fun cancelScan() {
    if (scanJob?.isActive != true) return
    scanJob?.cancel()
    _status.value = UiStatus(context.getString(R.string.books_scan_cancelled))
  }

  fun setQuery(value: String) {
    query.value = value
  }

  fun setFilter(value: BookFilter) {
    filter.value = value
    // Switch the visible sort along with the filter so the sort menu stays truthful;
    // the user can still pick another sort afterwards.
    if (value == BookFilter.Recent) sort.value = BookSort.Recent
  }

  fun setSort(value: BookSort) {
    sort.value = value
  }

  fun toggleFavorite(bookId: String) {
    viewModelScope.launch {
      runCatchingCancellable { localBooks.toggleFavorite(bookId) }
          .onFailure {
            _status.value =
                UiStatus(context.getString(R.string.could_not_update_book), StatusSeverity.Error)
          }
    }
  }

  private suspend fun scan(block: suspend () -> app.xpod.data.BookScanResult) {
    scanning.value = true
    try {
      runCatchingCancellable { block() }
          .onSuccess { result ->
            _status.value =
                UiStatus(
                    context.resources.getQuantityString(
                        R.plurals.books_scan_complete,
                        result.bookCount,
                        result.bookCount,
                        result.failureCount,
                    )
                )
          }
          .onFailure {
            _status.value =
                UiStatus(context.getString(R.string.books_scan_failed), StatusSeverity.Error)
          }
    } finally {
      scanning.value = false
      scanJob = null
    }
  }
}

internal fun progressFraction(item: BookWithProgress): Float {
  val savedSourceModifiedEpochMs = item.sourceModifiedEpochMs
  if (
      savedSourceModifiedEpochMs != null &&
          savedSourceModifiedEpochMs > 0L &&
          item.modifiedEpochMs > 0L &&
          savedSourceModifiedEpochMs != item.modifiedEpochMs
  ) {
    return 0f
  }
  val fraction =
      if (item.format.equals(BookFormat.PDF.name, true)) {
        ReaderPositionCodec.decodePdf(item.positionJson)?.percent ?: 0f
      } else {
        ReaderPositionCodec.decodeEpub(item.positionJson)?.percent ?: 0f
      }
  return fraction.coerceIn(0f, 1f)
}

private fun bookComparator(sort: BookSort): Comparator<BookWithProgress> =
    when (sort) {
      BookSort.Title ->
          compareBy<BookWithProgress> { it.title.lowercase(Locale.ROOT) }
              .thenBy { it.author.lowercase(Locale.ROOT) }
      BookSort.Author ->
          compareBy<BookWithProgress> { it.author.lowercase(Locale.ROOT) }
              .thenBy { it.title.lowercase(Locale.ROOT) }
      BookSort.Recent ->
          compareByDescending<BookWithProgress> { it.lastOpenedEpochMs }
              .thenBy { it.title.lowercase(Locale.ROOT) }
      BookSort.Added ->
          compareByDescending<BookWithProgress> { it.addedEpochMs }
              .thenBy { it.title.lowercase(Locale.ROOT) }
    }
