package app.xpod.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentParserTest {
  private val parser = ArticleContentParser()

  @Test
  fun parsesStructuredArticleContent() {
    val document =
        parser.parse(
            """
            <h2>A useful heading</h2>
            <p>Hello <strong>native</strong> <a href="/more">reader</a>.</p>
            <blockquote><p>A quoted thought.</p></blockquote>
            <ol><li>First</li><li>Second</li></ol>
            <pre><code>val answer = 42</code></pre>
            """
                .trimIndent(),
            "https://example.com/posts/1",
        )

    assertTrue(document.blocks[0] is ArticleBlock.Heading)
    val paragraph = document.blocks[1] as ArticleBlock.Paragraph
    assertTrue(paragraph.content.runs.any { ArticleTextStyle.Bold in it.styles })
    assertEquals(
        "https://example.com/more",
        paragraph.content.runs.first { it.text == "reader" }.url,
    )
    assertTrue(document.blocks.any { it is ArticleBlock.Quote })
    assertEquals(listOf("First", "Second"), document.listTexts())
    assertTrue(document.blocks.any { it is ArticleBlock.Code })
  }

  @Test
  fun resolvesLazyImagesAndCaptions() {
    val document =
        parser.parse(
            """
            <figure>
              <img data-src="../image.jpg" alt="A mountain">
              <figcaption>Morning light</figcaption>
            </figure>
            """
                .trimIndent(),
            "https://example.com/posts/article/",
        )

    val image = document.blocks.single() as ArticleBlock.Image
    assertEquals("https://example.com/posts/image.jpg", image.url)
    assertEquals("A mountain", image.description)
    assertEquals("Morning light", image.caption?.plainText())
  }

  @Test
  fun removesExecutableAndFormContent() {
    val document =
        parser.parse(
            "<p>Visible</p><script>bad()</script><form><input value='secret'></form>",
            "https://example.com",
        )

    assertEquals(
        "Visible",
        (document.blocks.single() as ArticleBlock.Paragraph).content.plainText(),
    )
    assertFalse(document.blocks.any { it is ArticleBlock.Embed })
  }

  @Test
  fun createsPlainTextPreviewFromHtml() {
    assertEquals(
        "A short summary with emphasis.",
        parser.plainText("<p>A short <b>summary</b> with emphasis.</p>"),
    )
  }

  private fun ArticleDocument.listTexts() =
      blocks.filterIsInstance<ArticleBlock.ListItems>().flatMap { list ->
        list.items.map { it.plainText() }
      }

  private fun ArticleText.plainText() = runs.joinToString("") { it.text }.trim()
}
