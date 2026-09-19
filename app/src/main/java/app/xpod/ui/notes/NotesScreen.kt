package app.xpod.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.LocalMarkdownNoteEntity
import app.xpod.data.displayTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesScreen(
    state: NotesUiState,
    onQueryChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onSortChanged: (NoteSort) -> Unit,
    onImportMarkdown: (Uri) -> Unit,
    onExportZip: (Uri) -> Unit,
) {
  var deleteId by remember { mutableStateOf<Long?>(null) }
  var menuExpanded by remember { mutableStateOf(false) }
  val zipLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let(onExportZip)
      }
  val markdownLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(onImportMarkdown)
      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.notes)) },
            actions = {
              var sortMenuExpanded by remember { mutableStateOf(false) }
              IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.note_sort))
              }
              DropdownMenu(
                  expanded = sortMenuExpanded,
                  onDismissRequest = { sortMenuExpanded = false },
              ) {
                NoteSort.entries.forEach { sort ->
                  DropdownMenuItem(
                      text = { Text(stringResource(noteSortLabel(sort))) },
                      onClick = {
                        sortMenuExpanded = false
                        onSortChanged(sort)
                      },
                  )
                }
              }
              IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.note_actions))
              }
              DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_markdown)) },
                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                    enabled = !state.isImporting,
                    onClick = {
                      menuExpanded = false
                      markdownLauncher.launch(
                          arrayOf("text/markdown", "text/plain", "application/octet-stream")
                      )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_notes_zip)) },
                    leadingIcon = { Icon(Icons.Filled.Unarchive, null) },
                    onClick = {
                      menuExpanded = false
                      zipLauncher.launch("xpod-notes.zip")
                    },
                )
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
      },
  ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.isExporting || state.isImporting) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      OutlinedTextField(
          value = state.query,
          onValueChange = onQueryChanged,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = { Text(stringResource(R.string.search_notes)) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      )
      if (state.notes.isEmpty()) {
        NotesEmptyState(hasQuery = state.query.isNotBlank(), onCreate = onCreate)
      } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.notes, key = LocalMarkdownNoteEntity::id) { note ->
            NoteListItem(
                note = note,
                onClick = { onOpenNote(note.id) },
                onDelete = { deleteId = note.id },
            )
          }
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.create_note))
        }
      }
    }
  }

  deleteId?.let { id ->
    AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text(stringResource(R.string.delete_note_title)) },
        text = { Text(stringResource(R.string.delete_note_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                deleteId = null
                onDeleteNote(id)
              }
          ) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
}

@Composable
private fun NotesEmptyState(hasQuery: Boolean, onCreate: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(if (hasQuery) R.string.no_matching_notes else R.string.no_notes))
      if (!hasQuery) {
        Button(onClick = onCreate) { Text(stringResource(R.string.create_first_note)) }
      }
    }
  }
}

@Composable
internal fun NotesLoadingScreen() {
  val loadingLabel = stringResource(R.string.loading_note)
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingLabel })
  }
}

@Composable
private fun NoteListItem(note: LocalMarkdownNoteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      shape = MaterialTheme.shapes.medium,
      tonalElevation = 1.dp,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Filled.Description,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
            note.displayTitle(stringResource(R.string.untitled_note)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            formatNoteDate(note.modifiedEpochMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val preview = note.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        if (preview.isNotBlank()) {
          Text(
              preview,
              maxLines = 2,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      var menuExpanded by remember { mutableStateOf(false) }
      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.note_actions))
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
              text = {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
              },
              leadingIcon = {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
              },
              onClick = {
                menuExpanded = false
                onDelete()
              },
          )
        }
      }
    }
  }
}

private fun noteSortLabel(sort: NoteSort): Int =
    when (sort) {
      NoteSort.Modified -> R.string.note_sort_modified
      NoteSort.Title -> R.string.note_sort_title
      NoteSort.Created -> R.string.note_sort_created
    }

private fun formatNoteDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
