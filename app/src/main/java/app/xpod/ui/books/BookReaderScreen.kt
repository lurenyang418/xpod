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
