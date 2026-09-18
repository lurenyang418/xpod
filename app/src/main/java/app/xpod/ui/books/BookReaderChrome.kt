package app.xpod.ui.books

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.reader.ReaderTocEntry

@Composable
internal fun PdfReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val containerColor =
      MaterialTheme.colorScheme.surface
          .copy(alpha = 0.92f)
          .compositeOver(MaterialTheme.colorScheme.background)
  Surface(
      modifier = modifier.fillMaxWidth().statusBarsPadding(),
      color = containerColor,
      tonalElevation = 3.dp,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_reader))
      }
      Text(
          text = title,
          maxLines = 1,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onOpenContents) {
        Icon(Icons.Filled.Menu, stringResource(R.string.table_of_contents))
      }
      IconButton(onClick = onOpenSettings) {
        Icon(Icons.Filled.Settings, stringResource(R.string.reading_theme))
      }
    }
  }
}

@Composable
internal fun ReaderTocItem(
    entry: ReaderTocEntry,
    currentSpineIndex: Int,
    depth: Int,
    onSelect: (Int) -> Unit,
) {
  NavigationDrawerItem(
      label = { Text(entry.title.ifBlank { stringResource(R.string.chapter) }) },
      selected = entry.spineIndex == currentSpineIndex,
      onClick = { entry.spineIndex?.let(onSelect) },
      modifier =
          Modifier.padding(start = (12 + depth * 16).dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
  )
  entry.children.forEach { child ->
    ReaderTocItem(child, currentSpineIndex, depth + 1, onSelect)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaffoldForBookReader(
    title: String,
    onBack: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
  androidx.compose.material3.Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_reader))
              }
            },
            actions = {
              IconButton(onClick = onOpenContents) {
                Icon(Icons.Filled.Menu, stringResource(R.string.table_of_contents))
              }
              IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, stringResource(R.string.reading_theme))
              }
            },
        )
      },
      content = content,
  )
}
