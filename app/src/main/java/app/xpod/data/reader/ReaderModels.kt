package app.xpod.data.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ReaderChapter(
    val spineIndex: Int,
    val title: String,
    val blocks: List<ReaderBlock>,
)

sealed interface ReaderBlock {
  data class Text(val text: String, val style: ReaderTextStyle = ReaderTextStyle()) : ReaderBlock

  data class Heading(val level: Int, val text: String) : ReaderBlock

  data class Image(val resourceKey: String, val caption: String? = null) : ReaderBlock

  data class Code(val text: String) : ReaderBlock

  data class ListBlock(val items: List<String>, val ordered: Boolean) : ReaderBlock

  data class Quote(val text: String, val attribution: String? = null) : ReaderBlock

  data object Break : ReaderBlock
}

data class ReaderTextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
)

data class EpubBook(
    val title: String,
    val author: String,
    val language: String,
    val coverResourceKey: String?,
    val chapters: List<EpubSpineItem>,
    val toc: List<ReaderTocEntry> = emptyList(),
)

data class EpubSpineItem(
    val id: String,
    val href: String,
    val title: String,
)

data class ReaderTocEntry(
    val title: String,
    val spineIndex: Int?,
    val children: List<ReaderTocEntry> = emptyList(),
)

data class EpubPosition(
    val spineIndex: Int = 0,
    val blockIndex: Int = 0,
    val offsetPx: Int = 0,
    val percent: Float = 0f,
)

data class PdfPosition(
    val pageIndex: Int = 0,
    val percent: Float = 0f,
)

const val READER_POSITION_VERSION = 1

internal fun EpubPosition.clampTo(chapterCount: Int): EpubPosition {
  val lastChapter = (chapterCount - 1).coerceAtLeast(0)
  return copy(
      spineIndex = spineIndex.coerceIn(0, lastChapter),
      blockIndex = blockIndex.coerceAtLeast(0),
      offsetPx = offsetPx.coerceAtLeast(0),
      percent = percent.coerceIn(0f, 1f),
  )
}

internal fun EpubPosition.withBookProgress(
    chapterBlockCount: Int,
    chapterCount: Int,
    isAtChapterEnd: Boolean = false,
): EpubPosition {
  val localProgress =
      when {
        isAtChapterEnd -> 1f
        chapterBlockCount <= 1 -> 0f
        else ->
            blockIndex.coerceIn(0, chapterBlockCount - 1).toFloat() /
                (chapterBlockCount - 1).toFloat()
      }
  val bookProgress =
      if (chapterCount <= 1) {
        localProgress
      } else {
        (spineIndex.coerceIn(0, chapterCount - 1) + localProgress) / chapterCount.toFloat()
      }
  return copy(percent = bookProgress.coerceIn(0f, 1f))
}

internal fun PdfPosition.clampTo(pageCount: Int): PdfPosition =
    copy(
        pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        percent = percent.coerceIn(0f, 1f),
    )

internal fun PdfPosition.withProgress(
    pageCount: Int,
    isAtDocumentEnd: Boolean = false,
): PdfPosition {
  val clamped = clampTo(pageCount)
  return clamped.copy(
      percent =
          when {
            pageCount <= 0 -> 0f
            isAtDocumentEnd -> 1f
            pageCount <= 1 -> 0f
            else -> clamped.pageIndex.toFloat() / (pageCount - 1).toFloat()
          },
  )
}

object ReaderPositionCodec {
  private val json = Json { ignoreUnknownKeys = true }

  fun encode(position: EpubPosition): String =
      buildJsonObject {
            put("kind", "epub")
            put("spineIndex", position.spineIndex)
            put("blockIndex", position.blockIndex)
            put("offsetPx", position.offsetPx)
            put("percent", position.percent.toDouble())
          }
          .toString()

  fun encode(position: PdfPosition): String =
      buildJsonObject {
            put("kind", "pdf")
            put("pageIndex", position.pageIndex)
            put("percent", position.percent.toDouble())
          }
          .toString()

  fun decodeEpub(value: String?): EpubPosition? =
      runCatching {
            val objectValue = json.parseToJsonElement(value ?: return null).jsonObject
            if (objectValue["kind"]?.jsonPrimitive?.content != "epub") return null
            EpubPosition(
                spineIndex = objectValue["spineIndex"]?.jsonPrimitive?.intOrNull ?: return null,
                blockIndex = objectValue["blockIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                offsetPx = objectValue["offsetPx"]?.jsonPrimitive?.intOrNull ?: 0,
                percent = objectValue["percent"]?.jsonPrimitive?.floatOrNull ?: 0f,
            )
          }
          .getOrNull()

  fun decodePdf(value: String?): PdfPosition? =
      runCatching {
            val objectValue = json.parseToJsonElement(value ?: return null).jsonObject
            if (objectValue["kind"]?.jsonPrimitive?.content != "pdf") return null
            PdfPosition(
                pageIndex = objectValue["pageIndex"]?.jsonPrimitive?.intOrNull ?: return null,
                percent = objectValue["percent"]?.jsonPrimitive?.floatOrNull ?: 0f,
            )
          }
          .getOrNull()
}
