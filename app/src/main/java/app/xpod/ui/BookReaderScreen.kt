package app.xpod.ui

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var showSettings by remember { mutableStateOf(false) }
  val view = androidx.compose.ui.platform.LocalView.current
  val activity = LocalActivity.current
  val isPdf = state.pdfPageCount > 0

  DisposableEffect(activity, view, isPdf) {
    val window = activity?.window
    if (!isPdf || window == null) {
      onDispose {}
    } else {
      val controller = WindowCompat.getInsetsController(window, view)
      val previousBehavior = controller.systemBarsBehavior
      controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
      onDispose {
        controller.systemBarsBehavior = previousBehavior
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  LaunchedEffect(bookId) { viewModel.openBook(bookId) }
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> viewModel.startReadingSession()
        Lifecycle.Event.ON_STOP -> viewModel.stopReadingSession()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.closeReaderResources()
    }
  }
  BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

  val readerContent: @Composable () -> Unit = {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet {
            val toc =
                state.epub?.toc?.takeIf(List<ReaderTocEntry>::isNotEmpty)
                    ?: state.epub?.chapters.orEmpty().mapIndexed { index, item ->
                      ReaderTocEntry(item.title, index)
                    }
            LazyColumn {
              item {
                Text(
                    stringResource(R.string.table_of_contents),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp),
                )
              }
              itemsIndexed(
                  toc,
                  key = { index, entry ->
                    "toc:$index:${entry.spineIndex}:${entry.title}"
                  },
              ) { _, entry ->
                ReaderTocItem(
                    entry = entry,
                    currentSpineIndex = state.position.spineIndex,
                    depth = 0,
                    onSelect = { spineIndex ->
                      viewModel.selectChapter(spineIndex)
                      scope.launch { drawerState.close() }
                    },
                )
              }
            }
          }
        },
    ) {
      if (isPdf) {
        Box(Modifier.fillMaxSize()) {
          ReaderBody(
              bookId = bookId,
              state = state,
              viewModel = viewModel,
              modifier = Modifier.fillMaxSize().padding(top = 64.dp),
          )
          PdfReaderTopBar(
              title = state.title,
              onBack = {
                viewModel.flushProgress()
                onBack()
              },
              onOpenContents = { scope.launch { drawerState.open() } },
              onOpenSettings = { showSettings = true },
              modifier = Modifier.align(Alignment.TopCenter),
          )
        }
      } else {
        ScaffoldForBookReader(
            title = state.title,
            onBack = {
              viewModel.flushProgress()
              onBack()
            },
            onOpenContents = { scope.launch { drawerState.open() } },
            onOpenSettings = { showSettings = true },
        ) { padding ->
          ReaderBody(
              bookId = bookId,
              state = state,
              viewModel = viewModel,
              modifier = Modifier.fillMaxSize().padding(padding),
          )
        }
      }
    }
  }

  when (state.preferences.theme) {
    ReadingTheme.Dark ->
        MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
          readerContent()
        }
    ReadingTheme.Light ->
        MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
          readerContent()
        }
    ReadingTheme.Sepia ->
        MaterialTheme(
            colorScheme =
                androidx.compose.material3.lightColorScheme(
                    background = Color(0xFFFFF8E7),
                    surface = Color(0xFFFFF8E7),
                    surfaceVariant = Color(0xFFF4E8CA),
                    onBackground = Color(0xFF3B3024),
                    onSurface = Color(0xFF3B3024),
                )
        ) {
          readerContent()
        }
    ReadingTheme.FollowApp -> readerContent()
  }
  if (showSettings) {
    ReadingSettingsDialog(
        fontSizeSp = state.preferences.fontSizeSp,
        lineHeight = state.preferences.lineHeightMultiplier,
        theme = state.preferences.theme,
        setFontSize = viewModel::setFontSize,
        setLineHeight = viewModel::setLineHeight,
        setTheme = viewModel::setTheme,
        onDismiss = { showSettings = false },
    )
  }
}

@Composable
private fun PdfReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val containerColor =
      MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
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
private fun ReaderTocItem(
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
private fun ScaffoldForBookReader(
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

@Composable
private fun ReaderBody(
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
private fun PdfReaderBody(
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

private fun findPdfContentBounds(bitmap: Bitmap): Rect {
  val width = bitmap.width
  val height = bitmap.height
  if (width < 2 || height < 2) return Rect(0, 0, width, height)

  val edgeSamples = pdfEdgeSamples(bitmap)
  val background = pdfBackgroundColor(edgeSamples)
  if (!isPdfBackgroundReliable(edgeSamples, background)) {
    return Rect(0, 0, width, height)
  }
  val step = maxOf(1, maxOf(width, height) / 720)
  var left = width
  var top = height
  var right = 0
  var bottom = 0
  for (y in 0 until height step step) {
    for (x in 0 until width step step) {
      if (pdfPixelDistance(bitmap.getPixel(x, y), background) >= PDF_CONTENT_THRESHOLD) {
        left = min(left, x)
        top = min(top, y)
        right = maxOf(right, x)
        bottom = maxOf(bottom, y)
      }
    }
  }
  if (left >= right || top >= bottom) return Rect(0, 0, width, height)

  val paddingX = maxOf(step * 2, (width * PDF_CONTENT_PADDING_RATIO).roundToInt())
  val paddingY = maxOf(step * 2, (height * PDF_CONTENT_PADDING_RATIO).roundToInt())
  return Rect(
      (left - paddingX).coerceAtLeast(0),
      (top - paddingY).coerceAtLeast(0),
      (right + paddingX + 1).coerceAtMost(width),
      (bottom + paddingY + 1).coerceAtMost(height),
  )
}

private fun pdfEdgeSamples(bitmap: Bitmap): List<Int> {
  val lastX = bitmap.width - 1
  val lastY = bitmap.height - 1
  val samplePoints =
      arrayOf(
          0 to 0,
          lastX to 0,
          0 to lastY,
          lastX to lastY,
          bitmap.width / 2 to 0,
          bitmap.width / 2 to lastY,
          0 to (bitmap.height / 2),
          lastX to (bitmap.height / 2),
      )
  return samplePoints.map { (x, y) -> bitmap.getPixel(x, y) }
}

private fun pdfBackgroundColor(samples: List<Int>): Int {
  return samples.minByOrNull { candidate ->
    samples.sumOf { sample -> pdfPixelDistance(candidate, sample) }
  } ?: samples.first()
}

internal fun isPdfBackgroundReliable(samples: List<Int>, background: Int): Boolean =
    samples.isNotEmpty() &&
        samples.maxOf { sample -> pdfPixelDistance(sample, background) } <=
            PDF_BACKGROUND_EDGE_THRESHOLD

private fun pdfPixelDistance(first: Int, second: Int): Int =
    maxOf(
        abs(pdfRed(first) - pdfRed(second)),
        abs(pdfGreen(first) - pdfGreen(second)),
        abs(pdfBlue(first) - pdfBlue(second)),
    )

private fun pdfRed(color: Int): Int = (color ushr 16) and 0xFF

private fun pdfGreen(color: Int): Int = (color ushr 8) and 0xFF

private fun pdfBlue(color: Int): Int = color and 0xFF

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    viewModel: BookReaderViewModel,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
) {
  val lineHeight = (fontSizeSp * lineHeightMultiplier).sp
  when (block) {
    is ReaderBlock.Text ->
        Text(
            block.text,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeight,
            fontWeight = if (block.style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (block.style.italic) FontStyle.Italic else FontStyle.Normal,
        )
    is ReaderBlock.Heading ->
        Text(
            block.text,
            fontSize = (fontSizeSp + (7 - block.level).coerceAtLeast(1) * 2).sp,
            lineHeight = (fontSizeSp * lineHeightMultiplier * 1.15f).sp,
            fontWeight = FontWeight.Bold,
        )
    is ReaderBlock.Code ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
          Text(
              block.text,
              fontSize = (fontSizeSp * 0.85f).sp,
              lineHeight = (fontSizeSp * 0.85f * lineHeightMultiplier).sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.fillMaxWidth().padding(12.dp),
          )
        }
    is ReaderBlock.Image -> EpubImage(block, viewModel)
    is ReaderBlock.ListBlock ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(if (block.ordered) "${index + 1}." else "•")
              Text(item, fontSize = fontSizeSp.sp, lineHeight = lineHeight)
            }
          }
        }
    is ReaderBlock.Quote ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                block.text,
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeight,
                fontStyle = FontStyle.Italic,
            )
            block.attribution?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
          }
        }
    ReaderBlock.Break ->
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
  }
}

@Composable
private fun EpubImage(block: ReaderBlock.Image, viewModel: BookReaderViewModel) {
  val bytes by
      produceState<ByteArray?>(initialValue = null, block.resourceKey) {
        value = runCatching { viewModel.loadResource(block.resourceKey) }.getOrNull()
      }
  val bitmap =
      remember(bytes) {
        bytes?.let(::decodeReaderImage)
      }
  if (bitmap != null) {
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = block.caption,
        modifier = Modifier.fillMaxWidth(),
    )
  } else {
    Text(block.caption ?: block.resourceKey, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private fun decodeReaderImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sample = 1
  while (true) {
    val sampledWidth = (bounds.outWidth.toLong() + sample - 1L) / sample
    val sampledHeight = (bounds.outHeight.toLong() + sample - 1L) / sample
    if (
        maxOf(sampledWidth, sampledHeight) <= MAX_READER_IMAGE_EDGE_PX &&
            sampledWidth * sampledHeight <= MAX_READER_IMAGE_PIXELS
    ) {
      break
    }
    if (sample >= MAX_READER_IMAGE_SAMPLE) return null
    sample *= 2
  }
  val options = BitmapFactory.Options().apply { inSampleSize = sample }
  val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
  if (
      bitmap.width.toLong() * bitmap.height.toLong() > MAX_READER_IMAGE_PIXELS ||
          bitmap.allocationByteCount.toLong() > MAX_READER_IMAGE_BYTES
  ) {
    bitmap.recycle()
    return null
  }
  return bitmap.asImageBitmap()
}

private const val MAX_READER_IMAGE_EDGE_PX = 2_048
private const val MAX_READER_IMAGE_PIXELS = 4L * 1024L * 1024L
private const val MAX_READER_IMAGE_BYTES = MAX_READER_IMAGE_PIXELS * 4L
private const val MAX_READER_IMAGE_SAMPLE = 1 shl 30
private const val PDF_CONTENT_THRESHOLD = 18
private const val PDF_CONTENT_PADDING_RATIO = 0.02f
private const val PDF_BACKGROUND_EDGE_THRESHOLD = 36
private const val PDF_PAGE_ASPECT_RATIO = 0.707f

@Composable
private fun ChapterNavigation(
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

@Composable
private fun ReadingSettingsDialog(
    fontSizeSp: Float,
    lineHeight: Float,
    theme: ReadingTheme,
    setFontSize: (Float) -> Unit,
    setLineHeight: (Float) -> Unit,
    setTheme: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit,
) {
  var draftFontSizeSp by remember(fontSizeSp) { mutableFloatStateOf(fontSizeSp) }
  var draftLineHeight by remember(lineHeight) { mutableFloatStateOf(lineHeight) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.reading_theme)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.reading_font_size))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FormatSize, null)
            Slider(
                value = draftFontSizeSp,
                onValueChange = { draftFontSizeSp = it },
                onValueChangeFinished = { setFontSize(draftFontSizeSp) },
                valueRange = 12f..32f,
                modifier = Modifier.weight(1f),
            )
          }
          Text(stringResource(R.string.reading_line_height))
          Slider(
              value = draftLineHeight,
              onValueChange = { draftLineHeight = it },
              onValueChangeFinished = { setLineHeight(draftLineHeight) },
              valueRange = 1.1f..2.4f,
          )
          ReadingTheme.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              androidx.compose.material3.RadioButton(
                  selected = theme == option,
                  onClick = { setTheme(option) },
              )
              Text(readingThemeLabel(option))
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
  )
}

@Composable
private fun readingThemeLabel(theme: ReadingTheme): String =
    stringResource(
        when (theme) {
          ReadingTheme.FollowApp -> R.string.reading_theme_follow_app
          ReadingTheme.Light -> R.string.reading_theme_light
          ReadingTheme.Sepia -> R.string.reading_theme_sepia
          ReadingTheme.Dark -> R.string.reading_theme_dark
        }
    )
