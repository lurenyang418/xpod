package app.xpod.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.BookFormat
import coil3.compose.AsyncImage

@Composable
internal fun BooksScreen(
    state: BooksUiState,
    chooseFolder: () -> Unit,
    refresh: () -> Unit,
    cancelScan: () -> Unit,
    setQuery: (String) -> Unit,
    setFilter: (BookFilter) -> Unit,
    setSort: (BookSort) -> Unit,
    toggleFavorite: (String) -> Unit,
    openBook: (String) -> Unit,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          stringResource(R.string.books),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.headlineSmall,
      )
      if (state.isScanning) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        IconButton(onClick = cancelScan) {
          Icon(Icons.Filled.Close, stringResource(R.string.cancel_books_scan))
        }
      } else {
        IconButton(onClick = refresh, enabled = state.treeUri != null) {
          Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_books))
        }
        Box {
          IconButton(onClick = { sortMenuExpanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.book_sort))
          }
          DropdownMenu(
              expanded = sortMenuExpanded,
              onDismissRequest = { sortMenuExpanded = false },
          ) {
            BookSort.entries.forEach { option ->
              DropdownMenuItem(
                  text = { Text(bookSortLabel(option)) },
                  onClick = {
                    setSort(option)
                    sortMenuExpanded = false
                  },
              )
            }
          }
        }
      }
    }
    if (state.treeUri == null) {
      Text(
          stringResource(R.string.no_books_select_folder),
          style = MaterialTheme.typography.titleMedium,
      )
      Text(
          stringResource(R.string.books_folder_summary),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = chooseFolder) { Text(stringResource(R.string.choose_books_folder)) }
    } else {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.query,
            onValueChange = setQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.search_books)) },
        )
        IconButton(onClick = chooseFolder) {
          Icon(Icons.Filled.Book, stringResource(R.string.choose_books_folder))
        }
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BookFilter.entries.size) { index ->
          val filter = BookFilter.entries[index]
          FilterChip(
              selected = state.filter == filter,
              onClick = { setFilter(filter) },
              label = { Text(bookFilterLabel(filter)) },
          )
        }
      }
    }
    if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.treeUri != null && state.books.isEmpty() && !state.isScanning) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(
            if (state.query.isBlank() && state.filter == BookFilter.All) {
              stringResource(R.string.no_books_found)
            } else {
              stringResource(R.string.no_books_match)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else if (state.books.isNotEmpty()) {
      LazyVerticalGrid(
          columns = GridCells.Adaptive(160.dp),
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(state.books, key = { it.book.id }) { item ->
          BookCard(
              item = item,
              onClick = { openBook(item.book.id) },
              onToggleFavorite = { toggleFavorite(item.book.id) },
          )
        }
      }
    } else {
      Box(Modifier.weight(1f))
    }
  }
}

@Composable
private fun BookCard(
    item: BookListItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .height(150.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
      ) {
        if (item.book.coverCachePath == null) {
          Icon(
              Icons.Filled.Book,
              contentDescription = stringResource(R.string.open_book),
              modifier = Modifier.size(56.dp),
              tint = MaterialTheme.colorScheme.primary,
          )
        } else {
          AsyncImage(
              model = item.book.coverCachePath,
              contentDescription = item.book.title,
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              item.book.title,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
          )
          Box {
            IconButton(onClick = { menuExpanded = true }) {
              Icon(Icons.Filled.MoreVert, stringResource(R.string.book_actions))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
              DropdownMenuItem(
                  text = {
                    Text(
                        stringResource(
                            if (item.book.isFavorite) R.string.remove_favorite
                            else R.string.favorite
                        )
                    )
                  },
                  leadingIcon = {
                    Icon(
                        if (item.book.isFavorite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        null,
                    )
                  },
                  onClick = {
                    menuExpanded = false
                    onToggleFavorite()
                  },
              )
            }
          }
        }
        if (item.book.author.isNotBlank()) {
          Text(
              item.book.author,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodySmall,
          )
        }
        Text(bookFormatLabel(item.format), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { item.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.book_progress, (item.progressFraction * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun bookFilterLabel(filter: BookFilter): String =
    stringResource(
        when (filter) {
          BookFilter.All -> R.string.all
          BookFilter.Recent -> R.string.book_sort_recent
          BookFilter.Favorites -> R.string.favorites
          BookFilter.Epub -> R.string.book_format_epub
          BookFilter.Pdf -> R.string.book_format_pdf
        }
    )

@Composable
private fun bookFormatLabel(format: BookFormat?): String =
    stringResource(
        when (format) {
          BookFormat.EPUB -> R.string.book_format_epub
          BookFormat.PDF -> R.string.book_format_pdf
          null -> R.string.unsupported_format
        }
    )

@Composable
private fun bookSortLabel(sort: BookSort): String =
    stringResource(
        when (sort) {
          BookSort.Title -> R.string.book_sort_title
          BookSort.Author -> R.string.book_sort_author
          BookSort.Recent -> R.string.book_sort_recent
          BookSort.Added -> R.string.book_sort_added
        }
    )
