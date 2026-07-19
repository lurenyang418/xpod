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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

@Composable
internal fun MemosScreen(
    state: MemosUiState,
    isConfigured: Boolean,
    openSettings: () -> Unit,
    load: () -> Unit,
    refresh: () -> Unit,
    loadMore: () -> Unit,
    setDraft: (String) -> Unit,
    setQuery: (String) -> Unit,
    setVisibility: (CloudMemoVisibility) -> Unit,
    selectTag: (String?) -> Unit,
    create: () -> Unit,
    search: () -> Unit,
) {
  LaunchedEffect(isConfigured) {
    if (isConfigured) load()
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

  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item(key = "header") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            stringResource(R.string.memos),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
        )
        IconButton(onClick = refresh, enabled = !state.isRefreshing) {
          Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_memos))
        }
      }
    }
    item(key = "composer") {
      MemoComposer(
          draft = state.draft,
          visibility = state.visibility,
          isCreating = state.isCreating,
          setDraft = setDraft,
          setVisibility = setVisibility,
          create = create,
      )
    }
    item(key = "search") {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.query,
            onValueChange = setQuery,
            label = { Text(stringResource(R.string.search_memos)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = search, enabled = !state.isRefreshing) {
          Icon(Icons.Filled.Search, stringResource(R.string.search))
        }
      }
    }
    if (state.knownTags.isNotEmpty() || state.selectedTag != null) {
      item(key = "tags") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
              selected = state.selectedTag == null,
              onClick = { selectTag(null) },
              label = { Text(stringResource(R.string.all_tags)) },
          )
          state.knownTags.forEach { tag ->
            FilterChip(
                selected = state.selectedTag.equals(tag, ignoreCase = true),
                onClick = { selectTag(tag) },
                label = { Text("#$tag") },
            )
          }
        }
      }
    }
    if (state.isRefreshing) {
      item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
    items(state.items, key = CloudMemo::id) { memo -> MemoCard(memo, selectTag) }
    if (state.nextCursor != null) {
      item(key = "load-more") {
        Button(
            onClick = loadMore,
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

@Composable
private fun MemoComposer(
    draft: String,
    visibility: CloudMemoVisibility,
    isCreating: Boolean,
    setDraft: (String) -> Unit,
    setVisibility: (CloudMemoVisibility) -> Unit,
    create: () -> Unit,
) {
  var showPreview by rememberSaveable { mutableStateOf(false) }
  Card(Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.create_memo), style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !showPreview,
            onClick = { showPreview = false },
            label = { Text(stringResource(R.string.edit)) },
        )
        FilterChip(
            selected = showPreview,
            onClick = { showPreview = true },
            label = { Text(stringResource(R.string.preview)) },
        )
      }
      if (showPreview) {
        Surface(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 128.dp),
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
            onValueChange = setDraft,
            label = { Text(stringResource(R.string.memo_markdown)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
      }
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CloudMemoVisibility.entries.forEach { option ->
          FilterChip(
              selected = visibility == option,
              onClick = { setVisibility(option) },
              label = { Text(visibilityLabel(option)) },
          )
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = create, enabled = draft.isNotBlank() && !isCreating) {
          Icon(Icons.AutoMirrored.Filled.Send, null)
          Text(stringResource(R.string.create), modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
  }
}

@Composable
private fun MemoCard(memo: CloudMemo, selectTag: (String?) -> Unit) {
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
