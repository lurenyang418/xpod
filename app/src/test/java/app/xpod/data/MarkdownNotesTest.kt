package app.xpod.data

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownNotesTest {
  @Test
  fun displayTitleUsesExplicitTitleBeforeFirstH1() {
    val note = note(title = "", content = "Intro\n# Heading\nBody")
    assertEquals("Heading", note.displayTitle("Untitled note"))
    assertEquals("Explicit", note.copy(title = "Explicit").displayTitle("Untitled note"))
  }

  @Test
  fun displayTitleFallsBackToUnnamed() {
    assertEquals(
        "Untitled note",
        note(title = " ", content = "## Not H1").displayTitle("Untitled note"),
    )
  }

  @Test
  fun firstMarkdownHeadingOnlyUsesTopLevelHeading() {
    assertEquals("Heading", firstMarkdownHeading("## Section\n# Heading\nBody"))
    assertNull(firstMarkdownHeading("## Section\n### Detail"))
  }

  @Test
  fun markdownImportPrefersH1AndPreservesSourceContent() {
    val source = "\uFEFFIntro\r\n# Imported title\r\nBody"
    val content =
        readMarkdownImportContent(ByteArrayInputStream(source.toByteArray(StandardCharsets.UTF_8)))
    val imported = markdownNoteFromImport("filename.md", content, 123L, "Untitled note")

    assertEquals("Imported title", imported.title)
    assertEquals("Intro\r\n# Imported title\r\nBody", imported.content)
    assertEquals(123L, imported.createdEpochMs)
    assertEquals(123L, imported.modifiedEpochMs)
  }

  @Test
  fun markdownImportUsesFilenameWhenThereIsNoH1() {
    val imported = markdownNoteFromImport("  My notes.md  ", "## Section", 456L, "Untitled note")

    assertEquals("My notes", imported.title)
    assertEquals("## Section", imported.content)
  }

  @Test
  fun markdownImportRejectsUnsupportedFilesAndOversizedContent() {
    try {
      markdownNoteFromImport("notes.txt", "content", 1L, "Untitled note")
      throw AssertionError("Expected a non-Markdown file to be rejected")
    } catch (_: UnsupportedMarkdownImportException) {
      // Expected.
    }

    try {
      readMarkdownImportContent(
          ByteArrayInputStream(
              "x".repeat(MAX_MARKDOWN_NOTE_CONTENT_LENGTH + 1).toByteArray(StandardCharsets.UTF_8)
          )
      )
      throw AssertionError("Expected oversized Markdown content to be rejected")
    } catch (_: MarkdownImportTooLargeException) {
      // Expected.
    }
  }

  @Test
  fun followAppThemeFallsBackToGithubAndUnknownStoredThemeIsSafe() {
    assertEquals(MarkdownThemeMode.GitHub, markdownThemeSpec(MarkdownThemeMode.FollowApp).mode)
    assertEquals(MarkdownThemeMode.Night, parseMarkdownThemeMode("Night"))
    assertEquals(MarkdownThemeMode.FollowApp, parseMarkdownThemeMode("not-a-theme"))
  }

  @Test
  fun markdownExportNormalizesLineEndingsAndAddsOneTrailingNewline() {
    assertEquals("a\nb\n", normalizeMarkdownForExport("a\r\nb\r\n\n"))
  }

  @Test
  fun exportBaseNameRemovesUnsafePathCharacters() {
    assertEquals("a_b_c_", safeExportBaseName("a/b:c?", 1))
    assertEquals("note-2", safeExportBaseName("...", 2))
  }

  @Test
  fun archiveBaseNamesAvoidSanitizedAndCaseInsensitiveCollisions() {
    val usedNames = mutableSetOf<String>()
    assertEquals("a_b", uniqueArchiveBaseName("a/b", 1, usedNames))
    assertEquals("a_b (2)", uniqueArchiveBaseName("a_b", 2, usedNames))
    assertEquals("a_b (2) (2)", uniqueArchiveBaseName("a_b (2)", 3, usedNames))
    assertEquals("A_B (3)", uniqueArchiveBaseName("A/B", 4, usedNames))
  }

  @Test
  fun localAttachmentReferencesAreStrictAndCanBeRewrittenForZipExport() {
    val fileName = "01234567-89ab-cdef-0123-456789abcdef.png"
    val reference = requireNotNull(markdownAttachmentReference(fileName))
    assertEquals(fileName, markdownAttachmentFileName(reference))
    assertNull(markdownAttachmentFileName("xpod-attachment://../$fileName"))
    assertNull(markdownAttachmentReference("../$fileName"))

    assertEquals(
        "![image](attachments/note/image.png)",
        rewriteMarkdownAttachmentReferences(
            "![image]($reference)",
            mapOf(reference to "attachments/note/image.png"),
        ),
    )
  }

  @Test
  fun ftsQueryQuotesEachTokenAndCombinesWithAnd() {
    assertEquals("\"hello\"* AND \"world\"*", markdownFtsQuery("hello world"))
    assertTrue(markdownFtsQuery("a\"b").contains("\"\""))
  }

  @Test
  fun htmlExportUsesThemeAndEscapesDocumentTitle() {
    val html =
        markdownHtmlDocument(
            note(title = "<Title>", content = "# Hello\n\n![x](http://unsafe.example/image.png)"),
            MarkdownThemeMode.Newsprint,
            "Untitled note",
        )
    assertTrue(html.contains("#f4ecd8"))
    assertTrue(html.contains("&lt;Title&gt;"))
    assertTrue(!html.contains("http://unsafe.example"))

    val unsafeHtml =
        markdownHtmlDocument(
            note(
                title = "x",
                content =
                    "<script>alert(1)</script><iframe src='x'>bad</iframe>\n<img src=\"javascript:alert(1)\"><a href='javascript:alert(1)'>bad</a><img src=javascript:alert(1) onerror=alert(1)>",
            ),
            MarkdownThemeMode.GitHub,
            "Untitled note",
        )
    assertTrue(!unsafeHtml.contains("<script"))
    assertTrue(!unsafeHtml.contains("<iframe"))
    assertTrue(!unsafeHtml.contains("javascript:"))
    assertTrue(!unsafeHtml.contains("<a href='javascript:"))

    val safeHtml =
        markdownHtmlDocument(
            note(title = "safe", content = "![x](https://example.com/image.png)"),
            MarkdownThemeMode.GitHub,
            "Untitled note",
        )
    assertTrue(safeHtml.contains("https://example.com/image.png"))
  }

  @Test
  fun htmlExportUsesCustomThemeSpecColorsAndTypography() {
    val spec =
        markdownThemeSpec(MarkdownThemeMode.Night)
            .copy(
                mode = MarkdownThemeMode.Custom,
                backgroundArgb = 0xFF102030,
                textArgb = 0xFFE8EDF2,
                linkArgb = 0xFF9BD1FF,
                bodyFontSizeSp = 19f,
            )

    val html = markdownHtmlDocument(note(title = "Theme", content = "Body"), spec, "Untitled")

    assertTrue(html.contains("background:#102030"))
    assertTrue(html.contains("color:#e8edf2"))
    assertTrue(html.contains("font-size:19.0px"))
    assertTrue(html.contains("a{color:#9bd1ff}"))
  }

  private fun note(title: String, content: String) =
      LocalMarkdownNoteEntity(
          title = title,
          content = content,
          createdEpochMs = 1L,
          modifiedEpochMs = 2L,
      )
}
