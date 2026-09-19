package app.xpod.data

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import kotlin.math.min

internal sealed interface MarkdownPdfBlock {
  data class Heading(val level: Int, val text: String) : MarkdownPdfBlock

  data class Paragraph(val text: String) : MarkdownPdfBlock

  data class Code(val text: String) : MarkdownPdfBlock

  data class Quote(val text: String) : MarkdownPdfBlock

  data class ListItem(val depth: Int, val marker: String, val text: String) : MarkdownPdfBlock

  data class Table(val rows: List<List<String>>) : MarkdownPdfBlock

  data object Divider : MarkdownPdfBlock
}

internal fun parseMarkdownPdfBlocks(markdown: String): List<MarkdownPdfBlock> {
  val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
  val blocks = mutableListOf<MarkdownPdfBlock>()
  var index = 0

  while (index < lines.size) {
    val line = lines[index]
    val trimmed = line.trim()
    if (trimmed.isEmpty()) {
      index++
      continue
    }

    val fence = CODE_FENCE.find(line)
    if (fence != null) {
      val delimiter = fence.groupValues[1].take(3)
      index++
      val codeLines = mutableListOf<String>()
      while (index < lines.size && !lines[index].trimStart().startsWith(delimiter)) {
        codeLines += lines[index]
        index++
      }
      if (index < lines.size) index++
      blocks += MarkdownPdfBlock.Code(codeLines.joinToString("\n"))
      continue
    }

    val heading = HEADING.find(trimmed)
    if (heading != null) {
      blocks +=
          MarkdownPdfBlock.Heading(
              level = heading.groupValues[1].length,
              text = plainMarkdownText(heading.groupValues[2]),
          )
      index++
      continue
    }

    if (HORIZONTAL_RULE.matches(trimmed)) {
      blocks += MarkdownPdfBlock.Divider
      index++
      continue
    }

    if (isTableStart(lines, index)) {
      val rows = mutableListOf<List<String>>()
      rows += tableCells(lines[index])
      index += 2 // Skip the Markdown separator row.
      while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
        rows += tableCells(lines[index])
        index++
      }
      blocks += MarkdownPdfBlock.Table(rows)
      continue
    }

    if (trimmed.startsWith(">")) {
      val quoteLines = mutableListOf<String>()
      while (index < lines.size && lines[index].trimStart().startsWith(">")) {
        quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
        index++
      }
      blocks += MarkdownPdfBlock.Quote(plainMarkdownText(quoteLines.joinToString(" ")))
      continue
    }

    val listItem = LIST_ITEM.find(line)
    if (listItem != null) {
      while (index < lines.size) {
        val item = LIST_ITEM.find(lines[index]) ?: break
        val task = TASK_ITEM.find(item.groupValues[3])
        val marker =
            if (task == null) item.groupValues[2]
            else if (task.groupValues[1].isBlank()) "☐" else "☑"
        val text = if (task == null) item.groupValues[3] else task.groupValues[2]
        blocks +=
            MarkdownPdfBlock.ListItem(
                depth = item.groupValues[1].length / 2,
                marker = marker,
                text = plainMarkdownText(text),
            )
        index++
      }
      continue
    }

    val paragraphLines = mutableListOf<String>()
    while (index < lines.size && lines[index].isNotBlank() && !startsMarkdownBlock(lines, index)) {
      paragraphLines += lines[index].trim()
      index++
    }
    if (paragraphLines.isEmpty()) {
      paragraphLines += lines[index].trim()
      index++
    }
    blocks += MarkdownPdfBlock.Paragraph(plainMarkdownText(paragraphLines.joinToString(" ")))
  }

  return blocks
}

private fun startsMarkdownBlock(lines: List<String>, index: Int): Boolean {
  val trimmed = lines[index].trim()
  return CODE_FENCE.containsMatchIn(lines[index]) ||
      HEADING.matches(trimmed) ||
      HORIZONTAL_RULE.matches(trimmed) ||
      trimmed.startsWith(">") ||
      LIST_ITEM.containsMatchIn(lines[index]) ||
      isTableStart(lines, index)
}

private fun isTableStart(lines: List<String>, index: Int): Boolean =
    index + 1 < lines.size &&
        lines[index].contains('|') &&
        lines[index + 1].contains('|') &&
        tableCells(lines[index + 1]).isNotEmpty() &&
        tableCells(lines[index + 1]).all(TABLE_SEPARATOR::matches)

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { plainMarkdownText(it.trim()) }

private fun plainMarkdownText(source: String): String =
    source
        .replace(IMAGE_OR_LINK) { match -> match.groupValues[1] }
        .replace(RAW_HTML, "")
        .replace(MARKDOWN_DECORATION, "")
        .replace(ESCAPED_MARKDOWN) { match -> match.groupValues[1] }

internal class MarkdownPdfExporter(private val context: Context) {
  fun write(
      note: LocalMarkdownNoteEntity,
      theme: MarkdownThemeMode,
      untitledLabel: String,
      output: OutputStream,
  ) = write(note, markdownThemeSpec(theme), untitledLabel, output)

  fun write(
      note: LocalMarkdownNoteEntity,
      theme: MarkdownThemeSpec,
      untitledLabel: String,
      output: OutputStream,
  ) {
    val printAttributes =
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("xpod_pdf", "XPOD PDF", PDF_DPI, PDF_DPI))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    val document = PrintedPdfDocument(context, printAttributes)
    try {
      render(document, note, theme, untitledLabel)
      document.writeTo(output)
    } finally {
      document.close()
    }
  }

  private fun render(
      document: PrintedPdfDocument,
      note: LocalMarkdownNoteEntity,
      spec: MarkdownThemeSpec,
      untitledLabel: String,
  ) {
    val bodyBlocks = parseMarkdownPdfBlocks(note.content)
    val title = note.displayTitle(untitledLabel)
    val blocks = buildList {
      if (
          bodyBlocks.firstOrNull() !is MarkdownPdfBlock.Heading ||
              (bodyBlocks.firstOrNull() as? MarkdownPdfBlock.Heading)?.text != title
      ) {
        add(MarkdownPdfBlock.Heading(level = 1, text = title))
      }
      addAll(bodyBlocks)
    }

    val marginPx = (36f * PDF_DPI / POINTS_PER_INCH).toInt()
    val footerHeightPx = (28f * PDF_DPI / POINTS_PER_INCH)
    val pageCountRef = intArrayOf(0)
    var page: PdfDocument.Page? = null
    var pageWidth = 0
    var pageHeight = 0
    var contentLeft = 0f
    var contentRight = 0f
    var contentBottom = 0f
    var cursorY = 0f

    fun startPage() {
      pageCountRef[0]++
      page = document.startPage(pageCountRef[0])
      val canvas = requireNotNull(page).canvas
      pageWidth = canvas.width
      pageHeight = canvas.height
      canvas.drawColor(spec.backgroundArgb.toInt())
      val maxContentWidth = spec.contentWidthDp * PDF_DPI / 160f
      val availableWidth = (pageWidth - marginPx * 2).toFloat()
      val contentWidth = min(availableWidth, maxContentWidth)
      contentLeft = (pageWidth - contentWidth) / 2f
      contentRight = contentLeft + contentWidth
      contentBottom = pageHeight - marginPx - footerHeightPx
      cursorY = marginPx.toFloat()
    }

    fun finishPage() {
      val activePage = page ?: return
      val canvas = activePage.canvas
      val footerY = pageHeight - marginPx * 0.62f
      val footerPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.tableBorderArgb.toInt()
            strokeWidth = maxOf(1f, PDF_DPI / 300f)
          }
      canvas.drawLine(
          contentLeft,
          footerY - footerHeightPx * 0.55f,
          contentRight,
          footerY - footerHeightPx * 0.55f,
          footerPaint,
      )
      footerPaint.style = Paint.Style.FILL
      footerPaint.textSize = spec.bodyFontSizeSp * PDF_DPI / 160f * 0.65f
      footerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      canvas.drawText(pageCountRef[0].toString(), contentRight, footerY, footerPaint)
      document.finishPage(activePage)
      page = null
    }

    fun nextPage() {
      finishPage()
      startPage()
    }

    fun drawBlockText(
        text: String,
        paint: TextPaint,
        indentPx: Float = 0f,
        backgroundColor: Int? = null,
        stripeColor: Int? = null,
        spaceBefore: Float = 0f,
        spaceAfter: Float = spec.paragraphSpacingDp * PDF_DPI / 160f * 0.55f,
    ) {
      if (text.isEmpty()) return
      cursorY += spaceBefore
      val x = contentLeft + indentPx
      val textWidth = maxOf(1, (contentRight - x).toInt())
      val layout =
          StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
              .setAlignment(Layout.Alignment.ALIGN_NORMAL)
              .setIncludePad(false)
              .setLineSpacing(0f, 1.2f)
              .build()
      var lineIndex = 0
      while (lineIndex < layout.lineCount) {
        val firstLineHeight = layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)
        if (cursorY + firstLineHeight > contentBottom) nextPage()
        val firstLineTop = layout.getLineTop(lineIndex)
        var endLine = lineIndex
        while (endLine + 1 < layout.lineCount) {
          val blockHeight = layout.getLineBottom(endLine + 1) - firstLineTop
          if (cursorY + blockHeight > contentBottom) break
          endLine++
        }
        val chunkHeight = (layout.getLineBottom(endLine) - firstLineTop).toFloat()
        val chunkTop = cursorY
        val blockLeft = x - PDF_DPI * 0.025f
        if (backgroundColor != null) {
          val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
          requireNotNull(page)
              .canvas
              .drawRoundRect(
                  RectF(
                      blockLeft,
                      chunkTop - PDF_DPI * 0.025f,
                      contentRight,
                      chunkTop + chunkHeight + PDF_DPI * 0.025f,
                  ),
                  PDF_DPI * 0.025f,
                  PDF_DPI * 0.025f,
                  backgroundPaint,
              )
        }
        if (stripeColor != null) {
          val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stripeColor }
          requireNotNull(page)
              .canvas
              .drawRect(
                  blockLeft,
                  chunkTop,
                  blockLeft + PDF_DPI * 0.012f,
                  chunkTop + chunkHeight,
                  stripePaint,
              )
        }
        val canvas = requireNotNull(page).canvas
        canvas.save()
        canvas.clipRect(x, chunkTop, contentRight, chunkTop + chunkHeight)
        canvas.translate(x, chunkTop - firstLineTop)
        layout.draw(canvas)
        canvas.restore()
        cursorY += chunkHeight
        lineIndex = endLine + 1
        if (lineIndex < layout.lineCount) nextPage()
      }
      cursorY += spaceAfter
    }

    startPage()
    val bodyPaint = textPaint(spec, spec.bodyFontSizeSp)
    blocks.forEach { block ->
      when (block) {
        is MarkdownPdfBlock.Heading -> {
          val scale =
              when (block.level) {
                1 -> 1.8f
                2 -> 1.5f
                3 -> 1.25f
                else -> 1.1f
              }
          drawBlockText(
              text = block.text,
              paint = textPaint(spec, spec.headingFontSizeSp * scale / 1.8f, bold = true),
              spaceBefore = PDF_DPI / 160f * if (block.level == 1) 8f else 5f,
              spaceAfter = PDF_DPI / 160f * 5f,
          )
        }
        is MarkdownPdfBlock.Paragraph -> drawBlockText(block.text, bodyPaint)
        is MarkdownPdfBlock.Code ->
            drawBlockText(
                text = block.text,
                paint =
                    textPaint(
                        spec,
                        spec.bodyFontSizeSp * 0.88f,
                        mono = true,
                        color = spec.codeTextArgb.toInt(),
                    ),
                indentPx = PDF_DPI / 160f * 4f,
                backgroundColor = spec.codeBackgroundArgb.toInt(),
                spaceBefore = PDF_DPI / 160f * 3f,
                spaceAfter = PDF_DPI / 160f * 7f,
            )
        is MarkdownPdfBlock.Quote ->
            drawBlockText(
                text = block.text,
                paint = textPaint(spec, spec.bodyFontSizeSp, italic = true),
                indentPx = PDF_DPI / 160f * 8f,
                backgroundColor = spec.quoteBackgroundArgb.toInt(),
                stripeColor = spec.tableBorderArgb.toInt(),
            )
        is MarkdownPdfBlock.ListItem -> {
          val indent = block.depth * PDF_DPI / 160f * 12f
          drawBlockText(
              text = "${block.marker}  ${block.text}",
              paint = bodyPaint,
              indentPx = indent,
              spaceAfter = PDF_DPI / 160f * 2f,
          )
        }
        is MarkdownPdfBlock.Table -> {
          block.rows.forEachIndexed { rowIndex, row ->
            drawBlockText(
                text = row.joinToString("   |   "),
                paint = textPaint(spec, spec.bodyFontSizeSp * 0.9f, bold = rowIndex == 0),
                backgroundColor = if (rowIndex == 0) spec.quoteBackgroundArgb.toInt() else null,
                spaceAfter = PDF_DPI / 160f * 2f,
            )
          }
          cursorY += PDF_DPI / 160f * 4f
        }
        MarkdownPdfBlock.Divider -> {
          if (cursorY + PDF_DPI / 160f * 8f > contentBottom) nextPage()
          val rulePaint =
              Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.tableBorderArgb.toInt()
                strokeWidth = PDF_DPI / 300f
              }
          requireNotNull(page)
              .canvas
              .drawLine(contentLeft, cursorY, contentRight, cursorY, rulePaint)
          cursorY += PDF_DPI / 160f * 8f
        }
      }
    }
    finishPage()
  }

  private fun textPaint(
      spec: MarkdownThemeSpec,
      sizeSp: Float,
      bold: Boolean = false,
      italic: Boolean = false,
      mono: Boolean = false,
      color: Int = spec.textArgb.toInt(),
  ): TextPaint {
    val style =
        when {
          bold && italic -> Typeface.BOLD_ITALIC
          bold -> Typeface.BOLD
          italic -> Typeface.ITALIC
          else -> Typeface.NORMAL
        }
    val resolvedTypeface =
        when {
          mono -> Typeface.create(Typeface.MONOSPACE, style)
          spec.fontFamily == MarkdownFontFamily.Serif -> Typeface.create("serif", style)
          else -> Typeface.create("sans-serif", style)
        }
    return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      textSize = sizeSp * PDF_DPI / 160f
      typeface = resolvedTypeface
    }
  }
}

private const val PDF_DPI = 300
private const val POINTS_PER_INCH = 72f
private val CODE_FENCE = Regex("^\\s*(`{3,}|~{3,})(.*)$")
private val HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private val HORIZONTAL_RULE = Regex("^(?:\\*\\s*){3,}$|^(?:-\\s*){3,}$|^(?:_\\s*){3,}$")
private val LIST_ITEM = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.*)$")
private val TASK_ITEM = Regex("^\\[([ xX])](?:\\s+|$)(.*)$")
private val TABLE_SEPARATOR = Regex(":?-{3,}:?")
private val IMAGE_OR_LINK = Regex("!?\\[([^]]*)]\\([^)]*\\)")
private val RAW_HTML = Regex("<[^>]*>")
private val MARKDOWN_DECORATION = Regex("(?:\\*{1,3}|_{1,3}|~~|`+)")
private val ESCAPED_MARKDOWN = Regex("\\\\([\\\\`*_{}\\[\\]()#+.!|>-])")
