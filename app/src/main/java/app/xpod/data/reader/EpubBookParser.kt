package app.xpod.data.reader

import android.content.Context
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Singleton
class EpubBookParser(
    private val archiveDirectory: File = File(System.getProperty("java.io.tmpdir") ?: "."),
) {
  @Inject constructor(@ApplicationContext context: Context) : this(context.cacheDir)

  init {
    archiveDirectory
        .listFiles { file ->
          file.isFile && file.name.startsWith(ARCHIVE_FILE_PREFIX) && file.name.endsWith(".zip")
        }
        ?.forEach { it.delete() }
  }

  suspend fun openArchive(openStream: suspend () -> InputStream): EpubArchive {
    archiveDirectory.mkdirs()
    val archiveFile = File.createTempFile(ARCHIVE_FILE_PREFIX, ".zip", archiveDirectory)
    try {
      openStream().use { input ->
        FileOutputStream(archiveFile).use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var total = 0L
          while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            total += count
            if (total > MAX_ARCHIVE_BYTES) error("EPUB archive exceeds resource limit")
            output.write(buffer, 0, count)
          }
        }
      }
      return EpubArchive(archiveFile)
    } catch (error: Throwable) {
      archiveFile.delete()
      throw error
    }
  }

  suspend fun parseMetadata(openStream: suspend () -> InputStream): EpubBook =
      withTemporaryArchive(openStream) { archive -> parseMetadata(archive) }

  suspend fun parseMetadata(archive: EpubArchive): EpubBook {
    val container = archive.readRequiredEntry(CONTAINER_PATH)
    val packagePath = parsePackagePath(container)
    val packageBytes = archive.readRequiredEntry(packagePath)
    val packageDocument = XmlDocument(packageBytes)
    val packageDir = packagePath.substringBeforeLast('/', "")
    val manifest = linkedMapOf<String, String>()
    packageDocument.elements("item").forEach { item ->
      val id = item.attr("id").trim()
      val href = item.attr("href").trim()
      if (id.isNotBlank() && href.isNotBlank()) {
        manifest[id] = resolvePath(packageDir, href.substringBefore('#'))
      }
    }
    val title =
        packageDocument.elements("title").firstOrNull()?.textContent?.trim().orEmpty().ifBlank {
          "Untitled book"
        }
    val author = packageDocument.elements("creator").firstOrNull()?.textContent?.trim().orEmpty()
    val language = packageDocument.elements("language").firstOrNull()?.textContent?.trim().orEmpty()
    val coverId =
        packageDocument
            .elements("meta")
            .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
            ?.attr("content")
    val coverResource =
        packageDocument
            .elements("item")
            .firstOrNull { item ->
              item.attr("properties").split(Regex("\\s+")).any { it == "cover-image" }
            }
            ?.attr("id")
            ?.let(manifest::get) ?: coverId?.let(manifest::get)
    val spine =
        packageDocument.elements("itemref").mapIndexedNotNull { index, itemref ->
          val id = itemref.attr("idref").trim()
          manifest[id]?.let { href ->
            EpubSpineItem(
                id = id,
                href = href,
                title =
                    href.substringAfterLast('/').substringBeforeLast('.').ifBlank {
                      "Chapter ${index + 1}"
                    },
            )
          }
        }
    if (spine.isEmpty()) error("EPUB has no readable spine")
    val chapterIndexes =
        spine.mapIndexed { index, item -> normalizePath(item.href) to index }.toMap()
    val navigationItem =
        packageDocument.elements("item").firstOrNull { item ->
          item.attr("properties").split(Regex("\\s+")).any { it.equals("nav", true) } ||
              item.attr("media-type").equals("application/x-dtbncx+xml", true)
        }
    val toc =
        navigationItem?.let { item ->
          val navigationHref = manifest[item.attr("id").trim()] ?: return@let emptyList()
          runCatchingCancellable {
                if (item.attr("media-type").equals("application/x-dtbncx+xml", true)) {
                  parseNcx(
                      archive.readRequiredEntry(navigationHref),
                      navigationHref,
                      chapterIndexes,
                  )
                } else {
                  parseHtmlNavigation(
                      archive.readRequiredEntry(navigationHref),
                      navigationHref,
                      chapterIndexes,
                  )
                }
              }
              .getOrDefault(emptyList())
        } ?: emptyList()
    return EpubBook(title, author, language, coverResource, spine, toc)
  }

  suspend fun readChapter(
      openStream: suspend () -> InputStream,
      spineItem: EpubSpineItem,
      spineIndex: Int,
  ): ReaderChapter =
      withTemporaryArchive(openStream) { archive -> readChapter(archive, spineItem, spineIndex) }

  suspend fun readChapter(
      archive: EpubArchive,
      spineItem: EpubSpineItem,
      spineIndex: Int,
  ): ReaderChapter {
    val bytes = archive.readRequiredEntry(spineItem.href)
    val document = Jsoup.parse(bytes.toString(StandardCharsets.UTF_8), spineItem.href)
    val blocks = buildBlocks(document.body(), spineItem.href)
    val title =
        document
            .title()
            .trim()
            .ifBlank {
              blocks.filterIsInstance<ReaderBlock.Heading>().firstOrNull()?.text?.trim().orEmpty()
            }
            .ifBlank { spineItem.title }
    return ReaderChapter(spineIndex, title, blocks)
  }

  suspend fun readResource(openStream: suspend () -> InputStream, resourceKey: String): ByteArray? =
      withTemporaryArchive(openStream) { archive -> archive.readEntry(resourceKey) }

  suspend fun readResource(archive: EpubArchive, resourceKey: String): ByteArray? =
      archive.readEntry(resourceKey)

  private suspend fun <T> withTemporaryArchive(
      openStream: suspend () -> InputStream,
      block: suspend (EpubArchive) -> T,
  ): T {
    val archive = openArchive(openStream)
    return try {
      block(archive)
    } finally {
      archive.close()
    }
  }

  private fun buildBlocks(root: Element, chapterHref: String): List<ReaderBlock> {
    val blocks = mutableListOf<ReaderBlock>()
    root.children().forEach { appendBlock(it, chapterHref, blocks) }
    if (blocks.isEmpty()) {
      root.text().trim().takeIf(String::isNotBlank)?.let { blocks += ReaderBlock.Text(it) }
    }
    return blocks
  }

  private fun appendBlock(
      element: Element,
      chapterHref: String,
      blocks: MutableList<ReaderBlock>,
  ) {
    when (element.tagName().lowercase(Locale.ROOT)) {
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" ->
          element.text().trim().takeIf(String::isNotBlank)?.let {
            blocks += ReaderBlock.Heading(element.tagName().substring(1).toInt(), it)
          }
      "p" -> {
        element.text().trim().takeIf(String::isNotBlank)?.let {
          blocks += ReaderBlock.Text(it, paragraphStyle(element))
        }
        element.select("img, image").forEach { image ->
          appendImageBlock(image, chapterHref, blocks)
        }
      }
      "pre" ->
          element.wholeText().trimEnd().trimStart('\n', '\r').takeIf(String::isNotBlank)?.let {
            blocks += ReaderBlock.Code(it)
          }
      "img",
      "image" -> appendImageBlock(element, chapterHref, blocks)
      "ul",
      "ol" -> {
        val items =
            element
                .children()
                .filter { it.tagName().equals("li", ignoreCase = true) }
                .map { it.text().trim() }
                .filter(String::isNotBlank)
        if (items.isNotEmpty()) blocks += ReaderBlock.ListBlock(items, element.tagName() == "ol")
      }
      "blockquote" ->
          element.text().trim().takeIf(String::isNotBlank)?.let { blocks += ReaderBlock.Quote(it) }
      "hr" -> blocks += ReaderBlock.Break
      "script",
      "style",
      "noscript" -> Unit
      else -> {
        val children = element.children()
        if (children.isEmpty()) {
          element.text().trim().takeIf(String::isNotBlank)?.let { blocks += ReaderBlock.Text(it) }
        } else {
          children.forEach { appendBlock(it, chapterHref, blocks) }
        }
      }
    }
  }

  // Style a paragraph only when all of its text sits inside emphasis tags; a partially
  // emphasized paragraph stays plain instead of emphasizing everything.
  private fun paragraphStyle(element: Element): ReaderTextStyle {
    var bold = false
    var italic = false
    var current = element
    while (current.ownText().isBlank() && current.children().size == 1) {
      current = current.child(0)
      when (current.tagName().lowercase(Locale.ROOT)) {
        "strong",
        "b" -> bold = true
        "em",
        "i" -> italic = true
        "span",
        "a" -> Unit
        else -> return ReaderTextStyle(bold = bold, italic = italic)
      }
    }
    return ReaderTextStyle(bold = bold, italic = italic)
  }

  private fun appendImageBlock(
      element: Element,
      chapterHref: String,
      blocks: MutableList<ReaderBlock>,
  ) {
    element
        .attr("src")
        .trim()
        .ifBlank { element.attr("xlink:href").trim() }
        .ifBlank { element.attr("href").trim() }
        .takeIf(String::isNotBlank)
        ?.let { src ->
          blocks +=
              ReaderBlock.Image(
                  resolvePath(chapterHref.substringBeforeLast('/', ""), src),
                  element.attr("alt").trim().takeIf(String::isNotBlank),
              )
        }
  }

  private fun parsePackagePath(container: ByteArray): String {
    val document = XmlDocument(container)
    return document
        .elements("rootfile")
        .firstOrNull()
        ?.attr("full-path")
        ?.trim()
        ?.takeIf(String::isNotBlank) ?: error("EPUB container has no package document")
  }

  private fun parseNcx(
      bytes: ByteArray,
      navigationHref: String,
      chapterIndexes: Map<String, Int>,
  ): List<ReaderTocEntry> {
    val document = XmlDocument(bytes)
    val navMap = document.elements("navMap").firstOrNull() ?: return emptyList()
    return navMap.childElements("navPoint").map {
      parseNcxEntry(it, navigationHref, chapterIndexes, 0)
    }
  }

  private fun parseNcxEntry(
      element: org.w3c.dom.Element,
      navigationHref: String,
      chapterIndexes: Map<String, Int>,
      depth: Int,
  ): ReaderTocEntry {
    val label =
        element
            .childElements("navLabel")
            .firstOrNull()
            ?.childElements("text")
            ?.firstOrNull()
            ?.textContent
            ?.trim()
            .orEmpty()
    val source = element.childElements("content").firstOrNull()?.attr("src").orEmpty()
    return ReaderTocEntry(
        title = label.ifBlank { source.substringAfterLast('/').substringBefore('#') },
        spineIndex = chapterIndex(source, navigationHref, chapterIndexes),
        children =
            if (depth >= MAX_TOC_DEPTH) emptyList()
            else {
              element.childElements("navPoint").map {
                parseNcxEntry(it, navigationHref, chapterIndexes, depth + 1)
              }
            },
    )
  }

  private fun parseHtmlNavigation(
      bytes: ByteArray,
      navigationHref: String,
      chapterIndexes: Map<String, Int>,
  ): List<ReaderTocEntry> {
    val document = Jsoup.parse(bytes.toString(StandardCharsets.UTF_8), navigationHref)
    val nav =
        document.select("nav").firstOrNull { element ->
          element.attr("epub:type").split(Regex("\\s+")).any { it.equals("toc", true) } ||
              element.attr("type").equals("toc", true)
        } ?: document.select("nav").firstOrNull() ?: return emptyList()
    val list = nav.children().firstOrNull { it.tagName() == "ol" || it.tagName() == "ul" } ?: nav
    return list
        .children()
        .filter { it.tagName().equals("li", true) }
        .map {
          parseHtmlTocEntry(it, navigationHref, chapterIndexes, 0)
        }
  }

  private fun parseHtmlTocEntry(
      element: Element,
      navigationHref: String,
      chapterIndexes: Map<String, Int>,
      depth: Int,
  ): ReaderTocEntry {
    val link = element.children().firstOrNull { it.tagName().equals("a", true) }
    val source = link?.attr("href").orEmpty()
    val nested = element.children().firstOrNull { it.tagName() == "ol" || it.tagName() == "ul" }
    return ReaderTocEntry(
        title = link?.text()?.trim().orEmpty().ifBlank { element.ownText().trim() },
        spineIndex = chapterIndex(source, navigationHref, chapterIndexes),
        children =
            if (depth >= MAX_TOC_DEPTH) emptyList()
            else {
              nested
                  ?.children()
                  ?.filter { it.tagName().equals("li", true) }
                  ?.map {
                    parseHtmlTocEntry(it, navigationHref, chapterIndexes, depth + 1)
                  } ?: emptyList()
            },
    )
  }

  private fun chapterIndex(
      source: String,
      navigationHref: String,
      chapterIndexes: Map<String, Int>,
  ): Int? {
    if (source.isBlank()) return null
    val base = navigationHref.substringBeforeLast('/', "")
    return chapterIndexes[resolvePath(base, source.substringBefore('#'))]
  }

  private class XmlDocument(bytes: ByteArray) {
    private val document = createDocument(bytes)

    private fun createDocument(bytes: ByteArray): org.w3c.dom.Document {
      val xml = bytes.toString(StandardCharsets.UTF_8)
      require(!xml.contains("<!DOCTYPE", ignoreCase = true)) {
        "EPUB XML must not contain a DOCTYPE declaration"
      }
      return javax.xml.parsers.DocumentBuilderFactory.newInstance()
          .apply {
            isNamespaceAware = true
            setFeatureIfSupported(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true,
            )
            setFeatureIfSupported(
                "http://xml.org/sax/features/external-general-entities",
                false,
            )
            setFeatureIfSupported(
                "http://xml.org/sax/features/external-parameter-entities",
                false,
            )
            setFeatureIfSupported(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
          }
          .newDocumentBuilder()
          .parse(ByteArrayInputStream(bytes))
    }

    private fun javax.xml.parsers.DocumentBuilderFactory.setFeatureIfSupported(
        name: String,
        value: Boolean,
    ) {
      runCatching { setFeature(name, value) }
    }

    fun elements(vararg names: String): List<org.w3c.dom.Element> {
      val result = mutableListOf<org.w3c.dom.Element>()
      val nodes = document.getElementsByTagName("*")
      for (index in 0 until nodes.length) {
        val node = nodes.item(index) as? org.w3c.dom.Element ?: continue
        val localName = node.localName ?: node.tagName.substringAfterLast(':')
        if (names.any { it.equals(localName, ignoreCase = true) }) result += node
      }
      return result
    }
  }

  private companion object {
    const val CONTAINER_PATH = "META-INF/container.xml"
    const val ARCHIVE_FILE_PREFIX = "xpod-epub-"
    const val MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L
    const val MAX_TOC_DEPTH = 32

    fun normalizePath(path: String): String {
      val decoded = decodeEpubPath(path.substringBefore('#'))
      val parts = decoded.replace('\\', '/').split('/')
      val stack = ArrayDeque<String>()
      parts.forEach { part ->
        when (part) {
          "",
          "." -> Unit
          ".." -> if (stack.isNotEmpty()) stack.removeLast()
          else -> stack.addLast(part)
        }
      }
      return stack.joinToString("/")
    }

    fun resolvePath(baseDir: String, href: String): String =
        normalizePath(listOf(baseDir.trim('/'), href).filter(String::isNotBlank).joinToString("/"))
  }
}

class EpubArchive internal constructor(private val file: File) : Closeable {
  private val zipFile = ZipFile(file)

  fun readRequiredEntry(path: String): ByteArray =
      readEntry(path) ?: error("EPUB entry not found: $path")

  fun readEntry(path: String): ByteArray? {
    val entry = zipFile.getEntry(normalizePath(path)) ?: return null
    if (entry.isDirectory) return null
    if (entry.size > MAX_ENTRY_BYTES) error("EPUB entry exceeds resource limit")
    zipFile.getInputStream(entry).use { input ->
      val output = ByteArrayOutputStream()
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var total = 0L
      while (true) {
        val count = input.read(buffer)
        if (count <= 0) break
        total += count
        if (total > MAX_ENTRY_BYTES) error("EPUB entry exceeds resource limit")
        output.write(buffer, 0, count)
      }
      return output.toByteArray()
    }
  }

  override fun close() {
    runCatching { zipFile.close() }
    file.delete()
  }

  private companion object {
    const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L

    fun normalizePath(path: String): String {
      val decoded = decodeEpubPath(path.substringBefore('#'))
      val parts = decoded.replace('\\', '/').split('/')
      val stack = ArrayDeque<String>()
      parts.forEach { part ->
        when (part) {
          "",
          "." -> Unit
          ".." -> if (stack.isNotEmpty()) stack.removeLast()
          else -> stack.addLast(part)
        }
      }
      return stack.joinToString("/")
    }
  }
}

// Tolerate malformed percent-encoding: a single bad href should degrade to its literal
// path instead of failing the whole book.
private fun decodeEpubPath(path: String): String =
    runCatching { URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }
        .getOrDefault(path)

private fun org.w3c.dom.Element.attr(name: String): String = getAttribute(name).orEmpty()

private fun org.w3c.dom.Element.childElements(vararg names: String): List<org.w3c.dom.Element> {
  val result = mutableListOf<org.w3c.dom.Element>()
  for (index in 0 until childNodes.length) {
    val child = childNodes.item(index) as? org.w3c.dom.Element ?: continue
    val localName = child.localName ?: child.tagName.substringAfterLast(':')
    if (names.any { it.equals(localName, ignoreCase = true) }) result += child
  }
  return result
}
