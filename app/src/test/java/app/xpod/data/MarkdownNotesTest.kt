package app.xpod.data

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
  fun followAppThemeFallsBackToGithubAndUnknownStoredThemeIsSafe() {
    assertEquals(MarkdownThemeMode.GitHub, markdownThemeSpec(MarkdownThemeMode.FollowApp).mode)
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

  private fun note(title: String, content: String) =
      LocalMarkdownNoteEntity(
          title = title,
          content = content,
          createdEpochMs = 1L,
          modifiedEpochMs = 2L,
      )
}
