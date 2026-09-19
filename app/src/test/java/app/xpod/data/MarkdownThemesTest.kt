package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarkdownThemesTest {
  @Test
  fun customThemeJsonRoundTripsAndPreservesStableId() {
    val imported = parseMarkdownThemeFile(validThemeJson())

    assertEquals("Paper", imported.name)
    assertEquals(MarkdownThemeMode.Custom, imported.spec.mode)
    assertEquals("#FAF7F0", imported.spec.backgroundArgb.toThemeHexForTest())
    assertEquals(imported, parseMarkdownThemeFile(encodeMarkdownThemeFile(imported)))
  }

  @Test
  fun customThemeStoreRoundTripsThemeList() {
    val theme = parseMarkdownThemeFile(validThemeJson())

    assertEquals(
        listOf(theme),
        decodeMarkdownCustomThemes(encodeMarkdownCustomThemes(listOf(theme))),
    )
  }

  @Test
  fun customThemeSelectionRoundTripsThroughNoteStorage() {
    val selection =
        MarkdownThemeSelection(
            MarkdownThemeMode.Custom,
            customThemeId = "01234567-89ab-4def-8123-456789abcdef",
        )

    assertEquals(selection, parseMarkdownThemeSelection(selection.toStorageValue()))
    assertEquals(
        MarkdownThemeSelection(MarkdownThemeMode.FollowApp),
        parseMarkdownThemeSelection("Custom:not-a-uuid"),
    )
  }

  @Test
  fun customThemeJsonRejectsPoorContrastAndUnsupportedVersion() {
    assertThrows(InvalidMarkdownThemeException::class.java) {
      parseMarkdownThemeFile(
          validThemeJson().replace("\"text\": \"#3F372E\"", "\"text\": \"#FAF7F0\"")
      )
    }
    assertThrows(InvalidMarkdownThemeException::class.java) {
      parseMarkdownThemeFile(validThemeJson().replace("\"version\": 1", "\"version\": 2"))
    }
  }

  private fun validThemeJson() =
      """
      {
        "format": "xpod-markdown-theme",
        "version": 1,
        "id": "01234567-89ab-4def-8123-456789abcdef",
        "name": "Paper",
        "background": "#FAF7F0",
        "text": "#3F372E",
        "codeBackground": "#E9DFC9",
        "codeText": "#3F372E",
        "link": "#704719",
        "quoteBackground": "#EDE2C8",
        "tableBorder": "#C9BFA9",
        "fontFamily": "Serif",
        "bodyFontSizeSp": 17.0,
        "headingFontSizeSp": 32.0,
        "paragraphSpacingDp": 14,
        "contentWidthDp": 800,
        "lineHeightMultiplier": 1.75
      }
      """
          .trimIndent()
}

private fun Long.toThemeHexForTest(): String = "#%06X".format(this and 0xFFFFFF)
