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
internal fun ReaderBlockView(
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
