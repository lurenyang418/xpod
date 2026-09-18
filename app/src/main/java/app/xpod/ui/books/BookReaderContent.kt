package app.xpod.ui.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.ReadingTheme
import app.xpod.data.reader.EpubPosition
import app.xpod.data.reader.ReaderBlock
import app.xpod.data.reader.ReaderTocEntry
import app.xpod.data.reader.withBookProgress
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ReaderBody(
    bookId: String,
    state: BookReaderUiState,
    viewModel: BookReaderViewModel,
    modifier: Modifier,
) {
  if (state.isLoading) {
    Box(modifier, contentAlignment = Alignment.Center) {
      androidx.compose.material3.CircularProgressIndicator()
    }
    return
  }
  if (state.error != null) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
      Text(state.error, color = MaterialTheme.colorScheme.error)
    }
    return
  }
  if (state.pdfPageCount > 0) {
    PdfReaderBody(bookId = bookId, state = state, viewModel = viewModel, modifier = modifier)
    return
  }
  val chapter = state.chapter ?: return
  val chapterCount = state.epub?.chapters?.size ?: 1
  val listState = rememberLazyListState()
  LaunchedEffect(bookId, chapter.spineIndex) {
    if (chapter.blocks.isNotEmpty()) {
      val targetBlock = state.position.blockIndex.coerceIn(0, chapter.blocks.lastIndex)
      listState.scrollToItem(targetBlock + 1, state.position.offsetPx.coerceAtLeast(0))
    }
    snapshotFlow {
          Triple(
              listState.firstVisibleItemIndex,
              listState.firstVisibleItemScrollOffset,
              !listState.canScrollForward,
          )
        }
        .map { (index, offset, isAtChapterEnd) ->
          val blockIndex = (index - 1).coerceIn(0, (chapter.blocks.size - 1).coerceAtLeast(0))
          EpubPosition(spineIndex = chapter.spineIndex, blockIndex = blockIndex, offsetPx = offset)
              .withBookProgress(
                  chapter.blocks.size,
                  chapterCount,
                  isAtChapterEnd = isAtChapterEnd,
              )
        }
        .distinctUntilChanged()
        .collect { position -> viewModel.recordProgress(bookId, position) }
  }
  SelectionContainer {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "chapter-title:${chapter.spineIndex}") {
        Text(chapter.title, style = MaterialTheme.typography.headlineMedium)
      }
      itemsIndexed(
          items = chapter.blocks,
          key = { index, _ -> "${chapter.spineIndex}:block:$index" },
      ) { _, block ->
        ReaderBlockView(
            block = block,
            viewModel = viewModel,
            fontSizeSp = state.preferences.fontSizeSp,
            lineHeightMultiplier = state.preferences.lineHeightMultiplier,
        )
      }
      item(key = "chapter-navigation:${chapter.spineIndex}") {
        ChapterNavigation(
            hasPrevious = chapter.spineIndex > 0,
            hasNext = state.epub != null && chapter.spineIndex < state.epub.chapters.lastIndex,
            onPrevious = { viewModel.selectChapter(chapter.spineIndex - 1) },
            onNext = { viewModel.selectChapter(chapter.spineIndex + 1) },
        )
      }
    }
  }
}
@Composable
internal fun PdfReaderBody(
    bookId: String,
    state: BookReaderUiState,
    viewModel: BookReaderViewModel,
    modifier: Modifier,
) {
  val listState = rememberLazyListState()
  val density = androidx.compose.ui.platform.LocalDensity.current
  var initialPositionRestored by remember(bookId, state.pdfPageCount) { mutableStateOf(false) }
  LaunchedEffect(bookId, state.pdfPageCount) {
    val target = state.pdfPosition.pageIndex.coerceIn(0, (state.pdfPageCount - 1).coerceAtLeast(0))
    if (listState.firstVisibleItemIndex != target) listState.scrollToItem(target)
    initialPositionRestored = true
  }
  LaunchedEffect(bookId, listState, initialPositionRestored) {
    if (!initialPositionRestored) return@LaunchedEffect
    snapshotFlow { listState.currentPdfPage() to !listState.canScrollForward }
        .distinctUntilChanged()
        .collect { (page, isAtDocumentEnd) ->
          viewModel.recordPdfProgress(
              bookId,
              app.xpod.data.reader.PdfPosition(page, 0f),
              isAtDocumentEnd = isAtDocumentEnd,
          )
        }
  }
  BoxWithConstraints(modifier) {
    val widthPx = with(density) { maxWidth.roundToPx() }
    val heightPx = with(density) { maxHeight.roundToPx() }
    val renderHeightPx = maxOf(heightPx, widthPx * 3)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(
          count = state.pdfPageCount,
          key = { index -> "pdf-page:$index" },
      ) { pageIndex ->
        PdfPage(
            bookId = bookId,
            pageIndex = pageIndex,
            title = state.title,
            viewModel = viewModel,
            widthPx = widthPx,
            renderHeightPx = renderHeightPx,
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

private fun LazyListState.currentPdfPage(): Int {
  val viewportStart = layoutInfo.viewportStartOffset
  val viewportEnd = layoutInfo.viewportEndOffset
  return layoutInfo.visibleItemsInfo
      .maxByOrNull { item ->
        val visibleStart = maxOf(item.offset, viewportStart)
        val visibleEnd = minOf(item.offset + item.size, viewportEnd)
        (visibleEnd - visibleStart).coerceAtLeast(0)
      }
      ?.index
      ?: firstVisibleItemIndex
}

@Composable
private fun PdfPage(
    bookId: String,
    pageIndex: Int,
    title: String,
    viewModel: BookReaderViewModel,
    widthPx: Int,
    renderHeightPx: Int,
    modifier: Modifier,
) {
  val bitmap by
      produceState<Bitmap?>(initialValue = null, bookId, pageIndex, widthPx, renderHeightPx) {
        value =
            runCatching { viewModel.renderPdfPage(pageIndex, widthPx, renderHeightPx) }.getOrNull()
      }
  val contentBounds by
      produceState<Rect?>(initialValue = null, bitmap) {
        value = bitmap?.let { rendered ->
          withContext(Dispatchers.Default) { findPdfContentBounds(rendered) }
        }
      }
  val source =
      bitmap?.let { rendered -> contentBounds ?: Rect(0, 0, rendered.width, rendered.height) }
  val aspectRatio =
      source?.let { it.width().toFloat() / it.height().coerceAtLeast(1).toFloat() } ?: PDF_PAGE_ASPECT_RATIO
  var scale by rememberSaveable(bookId, pageIndex) { mutableFloatStateOf(1f) }
  var offsetX by rememberSaveable(bookId, pageIndex) { mutableFloatStateOf(0f) }
  var offsetY by rememberSaveable(bookId, pageIndex) { mutableFloatStateOf(0f) }
  val pageHeightPx = (widthPx / aspectRatio).roundToInt().coerceAtLeast(1)
  LaunchedEffect(pageIndex, widthPx, pageHeightPx) {
    val maxOffsetX = widthPx * (scale - 1f) / 2f
    val maxOffsetY = pageHeightPx * (scale - 1f) / 2f
    offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
    offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
  }
  val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
    val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
    val maxOffsetX = widthPx * (nextScale - 1f) / 2f
    val maxOffsetY = pageHeightPx * (nextScale - 1f) / 2f
    scale = nextScale
    offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
    offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
  }
  Box(
      modifier =
          modifier
              .aspectRatio(aspectRatio)
              .transformable(
                  state = transformState,
                  // At the default scale, let LazyColumn handle vertical scrolling. Once
                  // zoomed in, consume panning so the current page can be explored.
                  canPan = { scale > 1.01f },
              )
              .graphicsLayer(
                  scaleX = scale,
                  scaleY = scale,
                  translationX = offsetX,
                  translationY = offsetY,
              ),
      contentAlignment = Alignment.Center,
  ) {
    bitmap?.let { rendered ->
      val bounds = source ?: Rect(0, 0, rendered.width, rendered.height)
      val pageDescription = stringResource(R.string.book_page, title, pageIndex + 1)
      Canvas(
          Modifier.fillMaxSize().semantics { contentDescription = pageDescription }
      ) {
        val sourceWidth = bounds.width().coerceAtLeast(1)
        val sourceHeight = bounds.height().coerceAtLeast(1)
        val fitScale =
            min(size.width / sourceWidth.toFloat(), size.height / sourceHeight.toFloat())
        val destinationWidth = (sourceWidth * fitScale).roundToInt().coerceAtLeast(1)
        val destinationHeight = (sourceHeight * fitScale).roundToInt().coerceAtLeast(1)
        drawImage(
            image = rendered.asImageBitmap(),
            srcOffset = androidx.compose.ui.unit.IntOffset(bounds.left, bounds.top),
            srcSize = androidx.compose.ui.unit.IntSize(sourceWidth, sourceHeight),
            dstOffset =
                androidx.compose.ui.unit.IntOffset(
                    ((size.width - destinationWidth) / 2f).roundToInt(),
                    ((size.height - destinationHeight) / 2f).roundToInt(),
                ),
            dstSize = androidx.compose.ui.unit.IntSize(destinationWidth, destinationHeight),
            filterQuality = FilterQuality.Medium,
        )
      }
    }
  }
}


@Composable
internal fun ChapterNavigation(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    TextButton(onClick = onPrevious, enabled = hasPrevious) {
      Text(stringResource(R.string.previous_chapter))
    }
    TextButton(onClick = onNext, enabled = hasNext) {
      Text(stringResource(R.string.next_chapter))
    }
  }
}
