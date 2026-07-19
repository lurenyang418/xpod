package app.xpod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoVisibility
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Immutable
internal data class MemosComposerActions(
    val setDraft: (String) -> Unit,
    val setVisibility: (CloudMemoVisibility) -> Unit,
    val create: () -> Unit,
)

@Immutable
internal data class MemosListActions(
    val load: () -> Unit,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val setQuery: (String) -> Unit,
    val selectTag: (String?) -> Unit,
    val search: () -> Unit,
)

@Immutable
internal data class MemosShareActions(
    val copyMemo: (CloudMemo) -> Unit,
    val shareMemoLink: (CloudMemo) -> Unit,
    val requestPrivateMemoShare: (String) -> Unit,
    val dismissPrivateMemoShare: () -> Unit,
    val sharePrivateMemoContent: (CloudMemo) -> Unit,
)

@Immutable
internal data class MemosManageActions(
    val archiveMemo: (String) -> Unit,
    val requestDelete: (String) -> Unit,
    val dismissDelete: () -> Unit,
    val moveToTrash: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemosScreen(
    state: MemosUiState,
    isConfigured: Boolean,
    openSettings: () -> Unit,
    composerActions: MemosComposerActions,
    listActions: MemosListActions,
    shareActions: MemosShareActions,
    manageActions: MemosManageActions,
) {
  LaunchedEffect(isConfigured) {
    if (isConfigured) listActions.load()
  }
  if (!isConfigured) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          stringResource(R.string.cloud_memos_not_connected),
          style = MaterialTheme.typography.titleLarge,
      )
      Text(
          stringResource(R.string.cloud_memos_not_connected_summary),
          modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
          style = MaterialTheme.typography.bodyMedium,
      )
      Button(onClick = openSettings) { Text(stringResource(R.string.open_settings)) }
    }
    return
  }
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

  state.pendingPrivateShareMemoId
      ?.let { memoId -> state.items.firstOrNull { memo -> memo.id == memoId } }
      ?.let { memo ->
        AlertDialog(
            onDismissRequest = shareActions.dismissPrivateMemoShare,
            title = { Text(stringResource(R.string.share_private_memo_title)) },
            text = { Text(stringResource(R.string.share_private_memo_message)) },
            confirmButton = {
              TextButton(
                  onClick = {
                    shareActions.sharePrivateMemoContent(memo)
                    shareActions.dismissPrivateMemoShare()
                  }
              ) {
                Text(stringResource(R.string.share_markdown))
              }
            },
            dismissButton = {
              TextButton(onClick = shareActions.dismissPrivateMemoShare) {
                Text(stringResource(R.string.cancel))
              }
            },
        )
      }

  state.pendingDeleteMemoId
      ?.let { memoId -> state.items.firstOrNull { memo -> memo.id == memoId } }
      ?.let { memo ->
        AlertDialog(
            onDismissRequest = manageActions.dismissDelete,
            title = { Text(stringResource(R.string.move_memo_to_trash_title)) },
            text = { Text(stringResource(R.string.move_memo_to_trash_message)) },
            confirmButton = {
              TextButton(onClick = { manageActions.moveToTrash(memo.id) }) {
                Text(
                    stringResource(R.string.move_memo_to_trash),
                    color = MaterialTheme.colorScheme.error,
                )
              }
            },
            dismissButton = {
              TextButton(onClick = manageActions.dismissDelete) {
                Text(stringResource(R.string.cancel))
              }
            },
        )
      }
}

@Composable
private fun MemoComposer(
    draft: String,
    visibility: CloudMemoVisibility,
    isCreating: Boolean,
    actions: MemosComposerActions,
) {
  var showPreview by rememberSaveable { mutableStateOf(false) }
  Card(Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            stringResource(R.string.create_memo),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          FilterChip(
              selected = !showPreview,
              onClick = { showPreview = false },
              label = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit),
                    modifier = Modifier.size(18.dp),
                )
              },
          )
          FilterChip(
              selected = showPreview,
              onClick = { showPreview = true },
              label = {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = stringResource(R.string.preview),
                    modifier = Modifier.size(18.dp),
                )
              },
          )
        }
      }
      if (showPreview) {
        Surface(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 160.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          if (draft.isBlank()) {
            Text(
                stringResource(R.string.markdown_preview_empty),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            MemoMarkdown(
                content = draft,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
          }
        }
      } else {
        OutlinedTextField(
            value = draft,
            onValueChange = actions.setDraft,
            label = { Text(stringResource(R.string.memo_markdown)) },
            minLines = 5,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          CloudMemoVisibility.entries.forEach { option ->
            FilterChip(
                selected = visibility == option,
                onClick = { actions.setVisibility(option) },
                label = {
                  Icon(
                      imageVector =
                          when (option) {
                            CloudMemoVisibility.Private -> Icons.Filled.Lock
                            CloudMemoVisibility.Members -> Icons.Filled.Group
                            CloudMemoVisibility.Public -> Icons.Filled.Public
                          },
                      contentDescription = visibilityLabel(option),
                      modifier = Modifier.size(18.dp),
                  )
                },
            )
          }
        }
        FilledIconButton(
            onClick = actions.create,
            enabled = draft.isNotBlank() && !isCreating,
            modifier = Modifier.padding(start = 8.dp),
        ) {
          Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(R.string.create),
          )
        }
      }
    }
  }
}

@Composable
private fun MemoCard(
    memo: CloudMemo,
    isBusy: Boolean,
    selectTag: (String?) -> Unit,
    shareActions: MemosShareActions,
    manageActions: MemosManageActions,
) {
  val visibility = visibilityLabel(memo.visibility)
  val pinned = stringResource(R.string.pinned)
  var expanded by rememberSaveable(memo.id) { mutableStateOf(false) }
  var renderedHeightPx by remember(memo.id, memo.content) { mutableIntStateOf(0) }
  val collapsedContent =
      remember(memo.id, memo.content) { memo.content.take(MAX_COLLAPSED_MEMO_CHARACTERS) }
  val previewWasTruncated = collapsedContent.length < memo.content.length
  val displayedContent = if (expanded) memo.content else collapsedContent
  val collapsedHeight = 240.dp
  val collapsedHeightPx = with(LocalDensity.current) { collapsedHeight.roundToPx() }
  val canExpand = previewWasTruncated || renderedHeightPx > collapsedHeightPx
  val cardColor = MaterialTheme.colorScheme.surfaceContainer
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = cardColor),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (canExpand && expanded) {
        TextButton(
            onClick = { expanded = false },
            modifier = Modifier.align(Alignment.End),
        ) {
          Text(stringResource(R.string.collapse_memo))
        }
      }
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .then(if (expanded) Modifier else Modifier.heightIn(max = collapsedHeight))
                  .clipToBounds()
      ) {
        MemoMarkdown(
            content = displayedContent,
            loadImages = expanded || !previewWasTruncated,
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size -> renderedHeightPx = size.height },
        )
        if (canExpand && !expanded) {
          Box(
              modifier =
                  Modifier.align(Alignment.BottomCenter)
                      .fillMaxWidth()
                      .height(64.dp)
                      .background(
                          Brush.verticalGradient(
                              colors = listOf(cardColor.copy(alpha = 0f), cardColor)
                          )
                      )
          )
        }
      }
      if (canExpand && !expanded) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.align(Alignment.End),
        ) {
          Text(stringResource(R.string.expand_memo))
        }
      }
      Text(
          buildString {
            append(visibility)
            append(" · ")
            append(formatMemoDate(memo.updatedAtEpochMs))
            if (memo.pinned) append(" · ").append(pinned)
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
      )
      if (memo.tags.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          memo.tags.forEach { tag ->
            AssistChip(onClick = { selectTag(tag) }, label = { Text("#$tag") })
          }
        }
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
      ) {
        IconButton(onClick = { shareActions.copyMemo(memo) }) {
          Icon(
              Icons.Filled.ContentCopy,
              contentDescription = stringResource(R.string.copy_markdown),
          )
        }
        IconButton(
            onClick = {
              if (memo.visibility == CloudMemoVisibility.Private) {
                shareActions.requestPrivateMemoShare(memo.id)
              } else {
                shareActions.shareMemoLink(memo)
              }
            }
        ) {
          Icon(
              Icons.Filled.Share,
              contentDescription =
                  stringResource(
                      if (memo.visibility == CloudMemoVisibility.Private) {
                        R.string.share_markdown
                      } else {
                        R.string.share_memo
                      }
                  ),
          )
        }
        MemoManageMenu(
            isBusy = isBusy,
            archiveMemo = { manageActions.archiveMemo(memo.id) },
            requestDelete = { manageActions.requestDelete(memo.id) },
        )
      }
    }
  }
}

@Composable
private fun MemoManageMenu(
    isBusy: Boolean,
    archiveMemo: () -> Unit,
    requestDelete: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    if (isBusy) {
      Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      }
    } else {
      IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.memo_actions))
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.archive_memo)) },
          onClick = {
            expanded = false
            archiveMemo()
          },
          leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
      )
      DropdownMenuItem(
          text = {
            Text(
                stringResource(R.string.move_memo_to_trash),
                color = MaterialTheme.colorScheme.error,
            )
          },
          onClick = {
            expanded = false
            requestDelete()
          },
          leadingIcon = {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
          },
      )
    }
  }
}

@Composable
private fun MemoMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    loadImages: Boolean = true,
) {
  val typography = MaterialTheme.typography
  Markdown(
      content = content,
      modifier = modifier,
      colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
      typography =
          markdownTypography(
              h1 = typography.titleLarge,
              h2 = typography.titleMedium,
              h3 = typography.titleSmall,
              h4 = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
              h5 = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
              h6 = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              text = typography.bodyLarge,
              paragraph = typography.bodyLarge,
              ordered = typography.bodyLarge,
              bullet = typography.bodyLarge,
              list = typography.bodyLarge,
          ),
      imageTransformer = if (loadImages) BoundedCoil3ImageTransformer else NoMemoImageTransformer,
  )
}

private object BoundedCoil3ImageTransformer : ImageTransformer {
  @Composable
  override fun transform(link: String): ImageData {
    val painter =
        rememberAsyncImagePainter(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(link)
                    .size(MAX_MEMO_IMAGE_SIZE_PX, MAX_MEMO_IMAGE_SIZE_PX)
                    .build()
        )
    return ImageData(
        painter = painter,
        modifier = Modifier.fillMaxWidth().heightIn(max = MAX_MEMO_IMAGE_HEIGHT),
    )
  }

  @Composable
  override fun intrinsicSize(painter: Painter): Size {
    var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
    if (painter is AsyncImagePainter) {
      painter.state.collectAsState().value.painter?.intrinsicSize?.let { size = it }
    }
    return size
  }
}

private val NoMemoImageTransformer = NoOpImageTransformerImpl()
private val MAX_MEMO_IMAGE_HEIGHT = 480.dp
private const val MAX_COLLAPSED_MEMO_CHARACTERS = 2_000
private const val MAX_MEMO_IMAGE_SIZE_PX = 1_280

@Composable
private fun visibilityLabel(visibility: CloudMemoVisibility): String =
    stringResource(
        when (visibility) {
          CloudMemoVisibility.Private -> R.string.memo_visibility_private
          CloudMemoVisibility.Members -> R.string.memo_visibility_members
          CloudMemoVisibility.Public -> R.string.memo_visibility_public
        }
    )

private fun formatMemoDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
