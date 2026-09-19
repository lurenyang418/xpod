package app.xpod.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCodeHighlighterTest {
  @Test
  fun highlightsKotlinKeywordsStringsNumbersAndComments() {
    val source =
        fenced(
            "kotlin",
            """
            fun answer(): String {
              val count = 42
              val url = "https://example.test"
              // keep this note
              /* block
                 comment */
              return url
            }
            """,
        )
    val spans = MarkdownCodeHighlighter.spans(source)

    assertHasToken(source, spans, "fun", MarkdownCodeToken.Keyword)
    assertHasToken(source, spans, "val", MarkdownCodeToken.Keyword)
    assertHasToken(source, spans, "42", MarkdownCodeToken.Number)
    assertHasToken(source, spans, "\"https://example.test\"", MarkdownCodeToken.String)
    assertHasToken(source, spans, "// keep this note", MarkdownCodeToken.Comment)
    assertHasToken(source, spans, "/* block", MarkdownCodeToken.Comment)
    assertTrue(
        spans.any {
          it.token == MarkdownCodeToken.Comment &&
              source.substring(it.start, it.end).trimStart() == "comment */"
        }
    )
    assertFalse(
        "The URL inside a string must not be mistaken for a line comment",
        spans.any { source.substring(it.start, it.end) == "//example.test\"" },
    )
  }

  @Test
  fun recognizesJavaAndPythonCodeFences() {
    val java = fenced("java", "public class Demo { return 1; } // note")
    val python = fenced("python", "def answer():\n    return True  # note")
    val javaSpans = MarkdownCodeHighlighter.spans(java)
    val pythonSpans = MarkdownCodeHighlighter.spans(python)

    assertHasToken(java, javaSpans, "public", MarkdownCodeToken.Keyword)
    assertHasToken(java, javaSpans, "class", MarkdownCodeToken.Keyword)
    assertHasToken(java, javaSpans, "// note", MarkdownCodeToken.Comment)
    assertHasToken(python, pythonSpans, "def", MarkdownCodeToken.Keyword)
    assertHasToken(python, pythonSpans, "return", MarkdownCodeToken.Keyword)
    assertHasToken(python, pythonSpans, "# note", MarkdownCodeToken.Comment)
  }

  @Test
  fun leavesMarkdownAndUnsupportedCodeLanguagesUnchanged() {
    val plainMarkdown = "# Heading\nval count = 1"
    val unsupported = fenced("javascript", "const count = 1;")

    assertTrue(MarkdownCodeHighlighter.spans(plainMarkdown).isEmpty())
    assertTrue(MarkdownCodeHighlighter.spans(unsupported).isEmpty())
  }

  @Test
  fun visualTransformationPreservesSourceAndEveryCaretOffset() {
    val source = fenced("kotlin", "val answer = 42")
    val colors =
        MarkdownSyntaxColors(
            keyword = Color.Blue,
            string = Color.Green,
            comment = Color.Gray,
            number = Color.Red,
        )
    val transformed = MarkdownCodeVisualTransformation(colors).filter(AnnotatedString(source))

    assertEquals(source, transformed.text.text)
    for (offset in 0..source.length) {
      assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
      assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
    }
    assertTrue(transformed.text.spanStyles.any { it.item.color == Color.Blue })
  }

  @Test
  fun skipsHighlightingOneMegabyteDocumentsWithoutChangingTextOrOffsets() {
    val source = fenced("kotlin", "val answer = 42\n".repeat(65_536))
    val transformed =
        MarkdownCodeVisualTransformation(
                MarkdownSyntaxColors(
                    keyword = Color.Blue,
                    string = Color.Green,
                    comment = Color.Gray,
                    number = Color.Red,
                )
            )
            .filter(AnnotatedString(source))

    assertTrue(source.length > 1_000_000)
    assertTrue(MarkdownCodeHighlighter.spans(source).isEmpty())
    assertEquals(source, transformed.text.text)
    assertTrue(transformed.text.spanStyles.isEmpty())
    for (offset in listOf(0, source.length / 2, source.length)) {
      assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
      assertEquals(offset, transformed.offsetMapping.transformedToOriginal(offset))
    }
  }

  private fun fenced(language: String, code: String): String {
    val marker = 96.toChar().toString().repeat(3)
    return "$marker$language\n$code\n$marker"
  }

  private fun assertHasToken(
      source: String,
      spans: List<MarkdownCodeSpan>,
      text: String,
      token: MarkdownCodeToken,
  ) {
    assertTrue(
        "Expected '$text' to be highlighted as $token",
        spans.any { it.token == token && source.substring(it.start, it.end) == text },
    )
  }
}
