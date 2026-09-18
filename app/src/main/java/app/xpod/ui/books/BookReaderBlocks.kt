package app.xpod.ui.books

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xpod.data.reader.ReaderBlock

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
