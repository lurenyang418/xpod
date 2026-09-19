package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPdfExporterTest {
  @Test
  fun parsesCommonMarkdownBlocksIntoPaginatedExportModel() {
    val blocks =
        parseMarkdownPdfBlocks(
            """
            # Heading

            A **bold** [link](https://example.com).

            > quoted text

            - [x] checked
            - ordinary item

            | Name | Value |
            | --- | --- |
            | alpha | beta |

            ```kotlin
            val answer = 42
            ```

            ---
            """
                .trimIndent()
        )

    assertEquals(MarkdownPdfBlock.Heading(1, "Heading"), blocks[0])
    assertEquals(MarkdownPdfBlock.Paragraph("A bold link."), blocks[1])
    assertEquals(MarkdownPdfBlock.Quote("quoted text"), blocks[2])
    assertEquals(MarkdownPdfBlock.ListItem(0, "☑", "checked"), blocks[3])
    assertEquals(MarkdownPdfBlock.ListItem(0, "-", "ordinary item"), blocks[4])
    assertEquals(
        MarkdownPdfBlock.Table(listOf(listOf("Name", "Value"), listOf("alpha", "beta"))),
        blocks[5],
    )
    assertEquals(MarkdownPdfBlock.Code("val answer = 42"), blocks[6])
    assertTrue(blocks.last() is MarkdownPdfBlock.Divider)
  }
}
