package app.xpod.data.reader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookParserTest {
  @Test
  fun parsesMetadataSpineAndLocalResources() = runBlocking {
    val book = parser().parseMetadata { ByteArrayInputStream(fixture()) }

    assertEquals("Fixture Book", book.title)
    assertEquals("A. Author", book.author)
    assertEquals("en", book.language)
    assertEquals("OEBPS/images/cover+1.png", book.coverResourceKey)
    assertEquals(listOf("OEBPS/chapter+one.xhtml"), book.chapters.map(EpubSpineItem::href))
    assertEquals(0, book.toc.single().spineIndex)
    assertEquals("One", book.toc.single().title)

    val chapter =
        parser()
            .readChapter(
                openStream = { ByteArrayInputStream(fixture()) },
                spineItem = book.chapters.single(),
                spineIndex = 0,
            )
    assertEquals("One", chapter.title)
    assertTrue(chapter.blocks.any { it is ReaderBlock.Heading && it.text == "One" })
    assertTrue(chapter.blocks.any { it is ReaderBlock.Text && it.text.contains("Hello") })
    assertTrue(
        chapter.blocks.any {
          it is ReaderBlock.Image && it.resourceKey == "OEBPS/images/cover+1.png"
        }
    )
    assertTrue(
        chapter.blocks.any {
          it is ReaderBlock.Image && it.caption == "Paragraph image"
        }
    )
    assertTrue(chapter.blocks.count { it is ReaderBlock.Image } >= 3)
    assertNotNull(
        parser()
            .readResource(
                openStream = { ByteArrayInputStream(fixture()) },
                resourceKey = "OEBPS/images/cover+1.png",
            )
    )
  }

  @Test
  fun emphasisStylesOnlyFullyEmphasizedParagraphsAndPreKeepsLineBreaks() = runBlocking {
    val fixture =
        miniEpub(
            "<p><em>All italic.</em></p>" +
                "<p>Partly <strong>bold</strong> text.</p>" +
                "<pre>line one\nline two</pre>"
        )
    val book = parser().parseMetadata { ByteArrayInputStream(fixture) }

    val chapter =
        parser()
            .readChapter(
                openStream = { ByteArrayInputStream(fixture) },
                spineItem = book.chapters.single(),
                spineIndex = 0,
            )

    val texts = chapter.blocks.filterIsInstance<ReaderBlock.Text>()
    assertEquals(ReaderTextStyle(italic = true), texts.single { it.text == "All italic." }.style)
    assertEquals(
        ReaderTextStyle(),
        texts.single { it.text == "Partly bold text." }.style,
    )
    assertEquals(
        "line one\nline two",
        chapter.blocks.filterIsInstance<ReaderBlock.Code>().single().text,
    )
  }

  @Test
  fun malformedPercentEncodingInHrefDegradesToLiteralPath() = runBlocking {
    val fixture = miniEpub("<p>Hello.</p>", chapterName = "chapter%zz.xhtml")

    val book = parser().parseMetadata { ByteArrayInputStream(fixture) }

    assertEquals(listOf("OEBPS/chapter%zz.xhtml"), book.chapters.map(EpubSpineItem::href))
    val chapter =
        parser()
            .readChapter(
                openStream = { ByteArrayInputStream(fixture) },
                spineItem = book.chapters.single(),
                spineIndex = 0,
            )
    assertTrue(chapter.blocks.any { it is ReaderBlock.Text && it.text == "Hello." })
  }

  @Test
  fun positionCodecRoundTripsBothFormats() {
    val epub = EpubPosition(2, 4, 18, 0.75f)
    val pdf = PdfPosition(8, 0.25f)

    assertEquals(epub, ReaderPositionCodec.decodeEpub(ReaderPositionCodec.encode(epub)))
    assertEquals(pdf, ReaderPositionCodec.decodePdf(ReaderPositionCodec.encode(pdf)))
  }

  @Test
  fun positionCodecDoesNotCrossDecodeFormats() {
    val epub = ReaderPositionCodec.encode(EpubPosition())
    val pdf = ReaderPositionCodec.encode(PdfPosition())

    assertEquals(null, ReaderPositionCodec.decodePdf(epub))
    assertEquals(null, ReaderPositionCodec.decodeEpub(pdf))
  }

  @Test
  fun positionsClampToAvailableDocumentBounds() {
    assertEquals(
        EpubPosition(spineIndex = 2, blockIndex = 0, offsetPx = 0, percent = 1f),
        EpubPosition(spineIndex = 99, blockIndex = -4, offsetPx = -1, percent = 2f).clampTo(3),
    )
    assertEquals(
        PdfPosition(pageIndex = 4, percent = 0f),
        PdfPosition(pageIndex = 99, percent = -1f).clampTo(5),
    )
  }

  @Test
  fun pdfProgressUsesTheClampedPageAsDocumentProgress() {
    assertEquals(0.5f, PdfPosition(pageIndex = 2).withProgress(5).percent, 0.0001f)
    assertEquals(1f, PdfPosition(pageIndex = 99).withProgress(5).percent, 0.0001f)
    assertEquals(0f, PdfPosition().withProgress(1).percent, 0.0001f)
    assertEquals(
        1f,
        PdfPosition().withProgress(1, isAtDocumentEnd = true).percent,
        0.0001f,
    )
    assertEquals(0f, PdfPosition(pageIndex = 4).withProgress(0).percent, 0.0001f)
  }

  @Test
  fun epubProgressIncludesSpinePosition() {
    assertEquals(
        0f,
        EpubPosition(spineIndex = 0, blockIndex = 0).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        1f / 3f,
        EpubPosition(spineIndex = 0, blockIndex = 9).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        1f / 3f,
        EpubPosition(spineIndex = 1, blockIndex = 0).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        2f / 3f,
        EpubPosition(spineIndex = 1, blockIndex = 9).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        2f / 3f,
        EpubPosition(spineIndex = 2, blockIndex = 0).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        1f,
        EpubPosition(spineIndex = 2, blockIndex = 9).withBookProgress(10, 3).percent,
        0.0001f,
    )
    assertEquals(
        0f,
        EpubPosition(blockIndex = 0).withBookProgress(3, 1).percent,
        0.0001f,
    )
    assertEquals(
        1f,
        EpubPosition(blockIndex = 0)
            .withBookProgress(3, 1, isAtChapterEnd = true)
            .percent,
        0.0001f,
    )
  }

  @Test
  fun singleBlockChapterOnlyReachesCompleteProgressAtChapterEnd() {
    assertEquals(
        0f,
        EpubPosition().withBookProgress(chapterBlockCount = 1, chapterCount = 1).percent,
        0.0001f,
    )
    assertEquals(
        1f,
        EpubPosition()
            .withBookProgress(
                chapterBlockCount = 1,
                chapterCount = 1,
                isAtChapterEnd = true,
            )
            .percent,
        0.0001f,
    )
  }

  private fun parser() = EpubBookParser()

  private fun fixture(): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      put(
          zip,
          "META-INF/container.xml",
          """
          <?xml version="1.0"?>
          <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
          </container>
          """
              .trimIndent(),
      )
      put(
          zip,
          "OEBPS/content.opf",
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
              <dc:title>Fixture Book</dc:title>
              <dc:creator>A. Author</dc:creator>
              <dc:language>en</dc:language>
              <meta name="cover" content="cover"/>
            </metadata>
            <manifest>
              <item id="chapter" href="chapter+one.xhtml" media-type="application/xhtml+xml"/>
              <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
              <item id="cover" href="images/cover+1.png" media-type="image/png"/>
            </manifest>
            <spine><itemref idref="chapter"/></spine>
          </package>
          """
              .trimIndent(),
      )
      put(
          zip,
          "OEBPS/chapter+one.xhtml",
          """
          <html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head>
            <body><h1>One</h1><p>Hello reader.</p><p><img src="images/cover+1.png" alt="Paragraph image"/></p><img src="images/cover+1.png" alt="Cover"/>
              <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                <image xlink:href="images/cover+1.png"/>
              </svg>
            </body>
          </html>
          """
              .trimIndent(),
      )
      put(
          zip,
          "OEBPS/toc.xhtml",
          """
          <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body><nav epub:type="toc"><ol><li><a href="chapter+one.xhtml">One</a></li></ol></nav></body>
          </html>
          """
              .trimIndent(),
      )
      put(zip, "OEBPS/images/cover+1.png", "png".toByteArray())
    }
    return output.toByteArray()
  }

  private fun miniEpub(chapterBody: String, chapterName: String = "chapter.xhtml"): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      put(
          zip,
          "META-INF/container.xml",
          """
          <?xml version="1.0"?>
          <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
          </container>
          """
              .trimIndent(),
      )
      put(
          zip,
          "OEBPS/content.opf",
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Mini Book</dc:title></metadata>
            <manifest>
              <item id="chapter" href="$chapterName" media-type="application/xhtml+xml"/>
            </manifest>
            <spine><itemref idref="chapter"/></spine>
          </package>
          """
              .trimIndent(),
      )
      put(
          zip,
          "OEBPS/$chapterName",
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter</title></head>" +
              "<body>$chapterBody</body></html>",
      )
    }
    return output.toByteArray()
  }

  private fun put(zip: ZipOutputStream, name: String, text: String) =
      put(zip, name, text.toByteArray())

  private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(bytes)
    zip.closeEntry()
  }
}
