package app.xpod.ui.books

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map

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
