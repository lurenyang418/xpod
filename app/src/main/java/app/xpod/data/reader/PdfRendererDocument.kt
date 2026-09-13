package app.xpod.data.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.Closeable

class PdfRendererDocument
private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
  private val lock = Any()
  private val pageSizes = arrayOfNulls<PageSize>(renderer.pageCount)
  private val bitmapCache =
      object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
      }

  val pageCount: Int
    get() = renderer.pageCount

  fun render(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap {
    val target = targetSize(pageIndex, widthPx, heightPx)
    val key = "${target.pageIndex}:${target.width}x${target.height}"
    synchronized(lock) {
      bitmapCache.get(key)?.let {
        return it
      }
      val bitmap = createBitmap(target.width, target.height)
      renderer.openPage(target.pageIndex).use { page ->
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      }
      bitmapCache.put(key, bitmap)
      return bitmap
    }
  }

  override fun close() {
    synchronized(lock) {
      bitmapCache.evictAll()
      renderer.close()
      descriptor.close()
    }
  }

  private fun targetSize(pageIndex: Int, widthPx: Int, heightPx: Int): TargetSize {
    require(pageCount > 0) { "PDF has no pages" }
    val validPage = pageIndex.coerceIn(0, pageCount - 1)
    val requestedWidth = widthPx.coerceAtLeast(1)
    val requestedHeight = heightPx.coerceAtLeast(1)
    val page = pageSize(validPage)
    val fitScale =
        minOf(
            requestedWidth.toFloat() / page.width.toFloat(),
            requestedHeight.toFloat() / page.height.toFloat(),
        )
    val baseWidth = (page.width * fitScale).toInt().coerceAtLeast(1)
    val baseHeight = (page.height * fitScale).toInt().coerceAtLeast(1)
    val scale =
        minOf(
            1f,
            MAX_PAGE_WIDTH_PX.toFloat() / baseWidth,
            MAX_PAGE_HEIGHT_PX.toFloat() / baseHeight,
            kotlin.math.sqrt(
                MAX_PAGE_PIXELS.toFloat() / (baseWidth.toFloat() * baseHeight.toFloat())
            ),
        )
    return TargetSize(
        pageIndex = validPage,
        width = (baseWidth * scale).toInt().coerceAtLeast(1),
        height = (baseHeight * scale).toInt().coerceAtLeast(1),
    )
  }

  private fun pageSize(pageIndex: Int): PageSize =
      synchronized(lock) {
        pageSizes[pageIndex]?.let {
          return it
        }
        renderer
            .openPage(pageIndex)
            .use { page ->
              PageSize(page.width, page.height)
            }
            .also { pageSizes[pageIndex] = it }
      }

  private data class TargetSize(val pageIndex: Int, val width: Int, val height: Int)

  private data class PageSize(val width: Int, val height: Int)

  companion object {
    private const val MAX_PAGE_WIDTH_PX = 4096
    private const val MAX_PAGE_HEIGHT_PX = 4096
    private const val MAX_PAGE_PIXELS = MAX_PAGE_WIDTH_PX * MAX_PAGE_HEIGHT_PX
    private const val MAX_CACHE_BYTES = 80 * 1024 * 1024

    fun open(descriptor: ParcelFileDescriptor): PdfRendererDocument =
        runCatching { PdfRendererDocument(descriptor, PdfRenderer(descriptor)) }
            .getOrElse {
              descriptor.close()
              throw it
            }
  }
}
