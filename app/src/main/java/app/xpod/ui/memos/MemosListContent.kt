package app.xpod.ui.memos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import app.xpod.R
import app.xpod.data.CloudMemo
import java.util.Locale

@Composable
internal fun MemosListContent(
    state: MemosUiState,
    composerActions: MemosComposerActions,
    listActions: MemosListActions,
    shareActions: MemosShareActions,
    manageActions: MemosManageActions,
) {
  val visibleTags =
      remember(state.knownTags, state.selectedTag) {
        (state.knownTags + listOfNotNull(state.selectedTag)).distinctBy {
          it.lowercase(Locale.ROOT)
        }
      }

  PullToRefreshBox(
      isRefreshing = state.isRefreshing,
      onRefresh = listActions.refresh,
      modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "header") {
        Text(
            stringResource(R.string.memos),
            style = MaterialTheme.typography.headlineSmall,
        )
      }
      item(key = "composer") {
        MemoComposer(
            draft = state.draft,
            visibility = state.visibility,
            isCreating = state.isCreating,
            actions = composerActions,
        )
      }
      item(key = "search") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
              value = state.query,
              onValueChange = listActions.setQuery,
              label = { Text(stringResource(R.string.search_memos)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions =
                  KeyboardActions(
                      onSearch = {
                        if (!state.isRefreshing) listActions.search()
                      }
                  ),
              modifier = Modifier.weight(1f),
          )
          IconButton(onClick = listActions.search, enabled = !state.isRefreshing) {
            Icon(Icons.Filled.Search, stringResource(R.string.search))
          }
        }
      }
      if (visibleTags.isNotEmpty()) {
        item(key = "tags") {
          Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
                selected = state.selectedTag == null,
                onClick = { listActions.selectTag(null) },
                label = { Text(stringResource(R.string.all_tags)) },
            )
            visibleTags.forEach { tag ->
              FilterChip(
                  selected = tag.equals(state.selectedTag, ignoreCase = true),
                  onClick = { listActions.selectTag(tag) },
                  label = { Text("#$tag") },
              )
            }
          }
        }
      }
      item(key = "list-header") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              pluralStringResource(
                  R.plurals.showing_memos_count,
                  state.items.size,
                  state.items.size,
              ),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
          )
          IconButton(onClick = listActions.refresh, enabled = !state.isRefreshing) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.refresh_memos),
            )
          }
        }
      }
      state.error?.let { message ->
        item(key = "error") {
          Text(
              message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      if (state.hasLoaded && !state.isRefreshing && state.items.isEmpty()) {
        item(key = "empty") {
          Text(stringResource(R.string.no_memos), style = MaterialTheme.typography.bodyLarge)
        }
      }
      items(state.items, key = CloudMemo::id) { memo ->
        MemoCard(
            memo = memo,
            isBusy = memo.id in state.busyMemoIds,
            selectTag = listActions.selectTag,
            shareActions = shareActions,
            manageActions = manageActions,
        )
      }
      if (state.nextCursor != null) {
        item(key = "load-more") {
          Button(
              onClick = listActions.loadMore,
              enabled = !state.isRefreshing && !state.isLoadingMore,
          ) {
            if (state.isLoadingMore) {
              CircularProgressIndicator(
                  modifier = Modifier.padding(end = 8.dp).size(18.dp),
                  strokeWidth = 2.dp,
              )
            }
            Text(stringResource(R.string.load_more))
          }
        }
      }
    }
  }
}
