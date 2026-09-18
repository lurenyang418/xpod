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

internal fun findPdfContentBounds(bitmap: Bitmap): Rect {
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

internal const val PDF_CONTENT_THRESHOLD = 18
internal const val PDF_CONTENT_PADDING_RATIO = 0.02f
internal const val PDF_BACKGROUND_EDGE_THRESHOLD = 36
internal const val PDF_PAGE_ASPECT_RATIO = 0.707f
