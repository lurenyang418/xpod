package app.xpod.ui

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal data class ArticleDocument(val blocks: List<ArticleBlock>) {
  val hasImage: Boolean
    get() = blocks.any { it is ArticleBlock.Image }
}

internal sealed interface ArticleBlock {
  data class Paragraph(val content: ArticleText) : ArticleBlock

  data class Heading(val level: Int, val content: ArticleText) : ArticleBlock

  data class Image(val url: String, val description: String?, val caption: ArticleText?) :
      ArticleBlock

  data class Quote(val content: ArticleText) : ArticleBlock

  data class Code(val content: String) : ArticleBlock

  data class ListItems(
      val ordered: Boolean,
      val items: List<ArticleText>,
  ) : ArticleBlock

  data class Table(val rows: List<List<ArticleText>>) : ArticleBlock

  data class Embed(val url: String, val title: String?) : ArticleBlock

  data object Divider : ArticleBlock
}

internal data class ArticleText(val runs: List<ArticleRun>) {
  val isBlank: Boolean
    get() = runs.all { it.text.isBlank() }
}

internal data class ArticleRun(
    val text: String,
    val styles: Set<ArticleTextStyle> = emptySet(),
    val url: String? = null,
)

internal enum class ArticleTextStyle {
  Bold,
  Italic,
  Code,
  StrikeThrough,
}

internal class ArticleContentParser {
  fun parse(html: String, baseUrl: String?): ArticleDocument {
    if (html.isBlank()) return ArticleDocument(emptyList())
    val document = Jsoup.parseBodyFragment(html, baseUrl.orEmpty())
    document
        .select("script, style, noscript, template, object, canvas, svg, form, button, input")
        .remove()
    return ArticleDocument(buildList { appendContainer(document.body(), this) })
  }

  fun plainText(html: String): String =
      Jsoup.parseBodyFragment(html).text().replace(repeatedWhitespace, " ").trim()

  private fun appendContainer(container: Element, blocks: MutableList<ArticleBlock>) {
    val inlineNodes = mutableListOf<Node>()

    fun flushInline() {
      val content = inlineContent(inlineNodes)
      if (!content.isBlank) blocks += ArticleBlock.Paragraph(content)
      inlineNodes.clear()
    }

    container.childNodes().forEach { node ->
      when {
        node is Element && node.tagName() in blockTags -> {
          flushInline()
          appendBlock(node, blocks)
        }
        node is Element && isStandaloneImage(node) -> {
          flushInline()
          appendImages(node, blocks)
        }
        else -> inlineNodes += node
      }
    }
    flushInline()
  }

  private fun appendBlock(element: Element, blocks: MutableList<ArticleBlock>) {
    when (element.tagName()) {
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" -> {
        val content = inlineContent(element.childNodes())
        if (!content.isBlank)
            blocks += ArticleBlock.Heading(element.tagName().drop(1).toInt(), content)
      }
      "p" -> {
        val content = inlineContent(element.childNodes())
        if (!content.isBlank) blocks += ArticleBlock.Paragraph(content)
        appendImages(element, blocks)
      }
      "blockquote" -> {
        val content = inlineContent(element.childNodes(), separateBlocks = true)
        if (!content.isBlank) blocks += ArticleBlock.Quote(content)
      }
      "pre" ->
          element
              .wholeText()
              .trim()
              .takeIf { it.isNotEmpty() }
              ?.let { blocks += ArticleBlock.Code(it) }
      "ul",
      "ol" -> appendList(element, blocks)
      "hr" -> blocks += ArticleBlock.Divider
      "img" -> appendImages(element, blocks)
      "figure" -> appendFigure(element, blocks)
      "table" -> appendTable(element, blocks)
      "iframe",
      "video",
      "audio" -> appendEmbed(element, blocks)
      else -> appendContainer(element, blocks)
    }
  }

  private fun appendList(element: Element, blocks: MutableList<ArticleBlock>) {
    val items =
        element
            .children()
            .filter { it.tagName() == "li" }
            .mapNotNull { item ->
              inlineContent(item.childNodes(), skipTags = setOf("ul", "ol")).takeUnless {
                it.isBlank
              }
            }
    if (items.isNotEmpty()) blocks += ArticleBlock.ListItems(element.tagName() == "ol", items)
    element
        .children()
        .filter { it.tagName() == "li" }
        .forEach { item ->
          item
              .children()
              .filter { it.tagName() in setOf("ul", "ol") }
              .forEach { appendList(it, blocks) }
        }
  }

  private fun appendFigure(element: Element, blocks: MutableList<ArticleBlock>) {
    val caption =
        element
            .selectFirst("figcaption")
            ?.let { inlineContent(it.childNodes()) }
            ?.takeUnless { it.isBlank }
    val images = element.select("img")
    images.forEach { image -> imageBlock(image, caption)?.let(blocks::add) }
    if (images.isEmpty()) appendContainer(element, blocks)
  }

  private fun appendImages(element: Element, blocks: MutableList<ArticleBlock>) {
    val images = if (element.tagName() == "img") listOf(element) else element.select("img")
    images.forEach { image -> imageBlock(image, null)?.let(blocks::add) }
  }

  private fun imageBlock(image: Element, caption: ArticleText?): ArticleBlock.Image? {
    val url =
        listOf("src", "data-src", "data-original").firstNotNullOfOrNull { attribute ->
          image.absUrl(attribute).ifBlank { image.attr(attribute) }.takeIf { it.isNotBlank() }
        }
            ?: image
                .attr("srcset")
                .substringBefore(',')
                .trim()
                .substringBefore(' ')
                .takeIf { it.isNotBlank() }
                ?.let { resolveUrl(image.baseUri(), it) }
            ?: return null
    if (!isWebUrl(url)) return null
    val description = image.attr("alt").trim().ifBlank { null }
    return ArticleBlock.Image(url, description, caption)
  }

  private fun appendTable(element: Element, blocks: MutableList<ArticleBlock>) {
    val rows =
        element.select("tr").mapNotNull { row ->
          row.children()
              .filter { it.tagName() in setOf("th", "td") }
              .map { inlineContent(it.childNodes()) }
              .takeIf { it.isNotEmpty() }
        }
    if (rows.isNotEmpty()) blocks += ArticleBlock.Table(rows)
  }

  private fun appendEmbed(element: Element, blocks: MutableList<ArticleBlock>) {
    val source =
        sequenceOf(element, element.selectFirst("source")).filterNotNull().firstNotNullOfOrNull {
            candidate ->
          candidate.absUrl("src").ifBlank { candidate.attr("src") }.takeIf { it.isNotBlank() }
        } ?: return
    if (isWebUrl(source))
        blocks +=
            ArticleBlock.Embed(
                source,
                element.attr("title").trim().ifBlank { null },
            )
  }

  private fun inlineContent(
      nodes: List<Node>,
      separateBlocks: Boolean = false,
      skipTags: Set<String> = emptySet(),
  ): ArticleText {
    val runs = mutableListOf<ArticleRun>()
    nodes.forEach { appendInline(it, emptySet(), null, runs, separateBlocks, skipTags) }
    val merged =
        runs
            .filter { it.text.isNotEmpty() }
            .fold(mutableListOf<ArticleRun>()) { result, run ->
              val previous = result.lastOrNull()
              if (previous != null && previous.styles == run.styles && previous.url == run.url) {
                result[result.lastIndex] = previous.copy(text = previous.text + run.text)
              } else {
                result += run
              }
              result
            }
    if (merged.isNotEmpty()) {
      merged[0] = merged.first().copy(text = merged.first().text.trimStart())
      merged[merged.lastIndex] = merged.last().copy(text = merged.last().text.trimEnd())
    }
    return ArticleText(merged.filter { it.text.isNotEmpty() })
  }

  private fun appendInline(
      node: Node,
      styles: Set<ArticleTextStyle>,
      url: String?,
      runs: MutableList<ArticleRun>,
      separateBlocks: Boolean,
      skipTags: Set<String>,
  ) {
    when (node) {
      is TextNode -> {
        val text = node.wholeText.replace(repeatedWhitespace, " ")
        if (text.isNotEmpty()) runs += ArticleRun(text, styles, url)
      }
      is Element -> {
        val tag = node.tagName()
        if (tag in skipTags || tag in setOf("img", "script", "style", "noscript")) return
        if (tag == "br") {
          runs += ArticleRun("\n", styles, url)
          return
        }
        val childStyles =
            when (tag) {
              "b",
              "strong" -> styles + ArticleTextStyle.Bold
              "i",
              "em" -> styles + ArticleTextStyle.Italic
              "code",
              "kbd",
              "samp" -> styles + ArticleTextStyle.Code
              "s",
              "strike",
              "del" -> styles + ArticleTextStyle.StrikeThrough
              else -> styles
            }
        val childUrl = if (tag == "a") resolvedLink(node) ?: url else url
        if (separateBlocks && tag in textBlockTags && runs.lastOrNull()?.text != "\n")
            runs += ArticleRun("\n")
        node.childNodes().forEach {
          appendInline(it, childStyles, childUrl, runs, separateBlocks, skipTags)
        }
        if (separateBlocks && tag in textBlockTags) runs += ArticleRun("\n")
      }
    }
  }

  private fun resolvedLink(element: Element): String? {
    val url = element.absUrl("href").ifBlank { element.attr("href") }
    return url.takeIf(::isSupportedLink)
  }

  private fun isStandaloneImage(element: Element) =
      element.tagName() == "img" ||
          element.tagName() == "a" &&
              element.children().isNotEmpty() &&
              element.children().all { it.tagName() == "img" }

  private fun resolveUrl(baseUrl: String, value: String): String =
      runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)

  private fun isWebUrl(url: String) =
      runCatching { URI(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

  private fun isSupportedLink(url: String) =
      runCatching { URI(url).scheme?.lowercase() in setOf("http", "https", "mailto") }
          .getOrDefault(false)

  private companion object {
    val repeatedWhitespace = Regex("[\\t\\x0B\\f\\r ]+")
    val blockTags =
        setOf(
            "address",
            "article",
            "aside",
            "blockquote",
            "details",
            "div",
            "dl",
            "dt",
            "dd",
            "figure",
            "footer",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "header",
            "hr",
            "iframe",
            "main",
            "nav",
            "ol",
            "p",
            "pre",
            "section",
            "table",
            "ul",
            "video",
            "audio",
        )
    val textBlockTags = setOf("div", "p", "li", "section", "article")
  }
}
