package app.xpod.data

import java.util.UUID
import kotlin.math.pow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class MarkdownThemeSelection(
    val mode: MarkdownThemeMode,
    val customThemeId: String? = null,
) {
  init {
    require((mode == MarkdownThemeMode.Custom) == (customThemeId != null))
  }

  fun toStorageValue(): String =
      if (mode == MarkdownThemeMode.Custom) "$CUSTOM_THEME_PREFIX$customThemeId" else mode.name
}

data class MarkdownCustomTheme(
    val id: String,
    val name: String,
    val spec: MarkdownThemeSpec,
)

class InvalidMarkdownThemeException : IllegalArgumentException("Invalid Markdown theme JSON")

class MarkdownCustomThemeLimitException : IllegalStateException("Custom theme limit reached")

internal const val MAX_MARKDOWN_CUSTOM_THEMES = 24
internal const val MAX_MARKDOWN_THEME_JSON_LENGTH = 32_768
private const val CUSTOM_THEME_PREFIX = "Custom:"
private const val THEME_FORMAT = "xpod-markdown-theme"
private val THEME_ID =
    Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
    )
private val THEME_JSON = Json { prettyPrint = true }

internal fun parseMarkdownThemeSelection(value: String?): MarkdownThemeSelection {
  if (value?.startsWith(CUSTOM_THEME_PREFIX) == true) {
    val id = value.removePrefix(CUSTOM_THEME_PREFIX)
    if (THEME_ID.matches(id)) return MarkdownThemeSelection(MarkdownThemeMode.Custom, id)
  }
  return MarkdownThemeSelection(parseMarkdownThemeMode(value))
}

internal fun parseMarkdownThemeFile(source: String): MarkdownCustomTheme {
  if (source.length > MAX_MARKDOWN_THEME_JSON_LENGTH) throw InvalidMarkdownThemeException()
  val root =
      runCatching { Json.parseToJsonElement(source).jsonObject }
          .getOrElse { throw InvalidMarkdownThemeException() }
  return parseMarkdownThemeObject(root, readThemeId(root) ?: UUID.randomUUID().toString())
}

internal fun encodeMarkdownThemeFile(theme: MarkdownCustomTheme): String =
    THEME_JSON.encodeToString(
        JsonObject.serializer(),
        markdownThemeJsonObject(theme, includeId = true),
    )

internal fun decodeMarkdownCustomThemes(source: String?): List<MarkdownCustomTheme> {
  if (
      source.isNullOrBlank() ||
          source.length > MAX_MARKDOWN_CUSTOM_THEMES * MAX_MARKDOWN_THEME_JSON_LENGTH
  ) {
    return emptyList()
  }
  val elements =
      runCatching { Json.parseToJsonElement(source).jsonArray }.getOrNull() ?: return emptyList()
  return elements
      .take(MAX_MARKDOWN_CUSTOM_THEMES)
      .mapNotNull { element ->
        runCatching {
              val root = element.jsonObject
              parseMarkdownThemeObject(root, readThemeId(root) ?: return@runCatching null)
            }
            .getOrNull()
      }
      .distinctBy(MarkdownCustomTheme::id)
}

internal fun encodeMarkdownCustomThemes(themes: List<MarkdownCustomTheme>): String =
    buildJsonArray {
          themes.take(MAX_MARKDOWN_CUSTOM_THEMES).forEach { add(markdownThemeJsonObject(it)) }
        }
        .toString()

internal fun markdownThemeJsonObject(
    theme: MarkdownCustomTheme,
    includeId: Boolean = true,
): JsonObject = buildJsonObject {
  put("format", THEME_FORMAT)
  put("version", 1)
  if (includeId) put("id", theme.id)
  put("name", theme.name)
  put("background", theme.spec.backgroundArgb.toThemeHex())
  put("text", theme.spec.textArgb.toThemeHex())
  put("codeBackground", theme.spec.codeBackgroundArgb.toThemeHex())
  put("codeText", theme.spec.codeTextArgb.toThemeHex())
  put("link", theme.spec.linkArgb.toThemeHex())
  put("quoteBackground", theme.spec.quoteBackgroundArgb.toThemeHex())
  put("tableBorder", theme.spec.tableBorderArgb.toThemeHex())
  put("fontFamily", theme.spec.fontFamily.name)
  put("bodyFontSizeSp", theme.spec.bodyFontSizeSp)
  put("headingFontSizeSp", theme.spec.headingFontSizeSp)
  put("paragraphSpacingDp", theme.spec.paragraphSpacingDp)
  put("contentWidthDp", theme.spec.contentWidthDp)
  put("lineHeightMultiplier", theme.spec.lineHeightMultiplier)
}

private fun parseMarkdownThemeObject(root: JsonObject, id: String): MarkdownCustomTheme {
  if (id.length > 64 || !THEME_ID.matches(id)) throw InvalidMarkdownThemeException()
  if (root.requiredString("format") != THEME_FORMAT || root.requiredInt("version") != 1) {
    throw InvalidMarkdownThemeException()
  }
  val name = root.requiredString("name").trim()
  if (name.isBlank() || name.length > 48 || name.any(Char::isISOControl)) {
    throw InvalidMarkdownThemeException()
  }
  val fontFamily =
      runCatching { MarkdownFontFamily.valueOf(root.requiredString("fontFamily")) }
          .getOrElse { throw InvalidMarkdownThemeException() }
  val bodyFontSize = root.requiredFloat("bodyFontSizeSp").validatedRange(12f, 24f)
  val headingFontSize = root.requiredFloat("headingFontSizeSp").validatedRange(20f, 48f)
  val paragraphSpacing = root.requiredInt("paragraphSpacingDp").validatedRange(0, 32)
  val contentWidth = root.requiredInt("contentWidthDp").validatedRange(320, 1_200)
  val lineHeight = root.requiredFloat("lineHeightMultiplier").validatedRange(1.2f, 2.2f)
  val background = root.requiredColor("background")
  val text = root.requiredColor("text")
  val codeBackground = root.requiredColor("codeBackground")
  val codeText = root.requiredColor("codeText")
  val link = root.requiredColor("link")
  val quoteBackground = root.requiredColor("quoteBackground")
  val tableBorder = root.requiredColor("tableBorder")
  if (
      contrastRatio(text, background) < MIN_TEXT_CONTRAST ||
          contrastRatio(codeText, codeBackground) < MIN_TEXT_CONTRAST ||
          contrastRatio(text, quoteBackground) < MIN_TEXT_CONTRAST ||
          contrastRatio(link, background) < MIN_TEXT_CONTRAST
  ) {
    throw InvalidMarkdownThemeException()
  }
  return MarkdownCustomTheme(
      id = id,
      name = name,
      spec =
          MarkdownThemeSpec(
              mode = MarkdownThemeMode.Custom,
              backgroundArgb = background,
              textArgb = text,
              codeBackgroundArgb = codeBackground,
              codeTextArgb = codeText,
              linkArgb = link,
              quoteBackgroundArgb = quoteBackground,
              tableBorderArgb = tableBorder,
              fontFamily = fontFamily,
              bodyFontSizeSp = bodyFontSize,
              headingFontSizeSp = headingFontSize,
              paragraphSpacingDp = paragraphSpacing,
              contentWidthDp = contentWidth,
              lineHeightMultiplier = lineHeight,
          ),
  )
}

private fun readThemeId(root: JsonObject): String? {
  val value = root["id"]?.jsonPrimitive?.contentOrNull ?: return null
  return value.takeIf(THEME_ID::matches) ?: throw InvalidMarkdownThemeException()
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull ?: throw InvalidMarkdownThemeException()

private fun JsonObject.requiredInt(name: String): Int =
    this[name]?.jsonPrimitive?.intOrNull ?: throw InvalidMarkdownThemeException()

private fun JsonObject.requiredFloat(name: String): Float =
    this[name]?.jsonPrimitive?.floatOrNull?.takeIf(Float::isFinite)
        ?: throw InvalidMarkdownThemeException()

private fun JsonObject.requiredColor(name: String): Long {
  val value = requiredString(name)
  if (!value.matches(Regex("#[0-9a-fA-F]{6}"))) throw InvalidMarkdownThemeException()
  return 0xFF000000L or value.drop(1).toLong(16)
}

private fun Int.validatedRange(minimum: Int, maximum: Int): Int =
    takeIf { it in minimum..maximum } ?: throw InvalidMarkdownThemeException()

private fun Float.validatedRange(minimum: Float, maximum: Float): Float =
    takeIf { it in minimum..maximum } ?: throw InvalidMarkdownThemeException()

private fun Long.toThemeHex(): String = "#%06X".format(this and 0xFFFFFF)

private fun contrastRatio(first: Long, second: Long): Double {
  val firstLuminance = relativeLuminance(first)
  val secondLuminance = relativeLuminance(second)
  return (maxOf(firstLuminance, secondLuminance) + 0.05) /
      (minOf(firstLuminance, secondLuminance) + 0.05)
}

private fun relativeLuminance(color: Long): Double {
  fun channel(shift: Int): Double {
    val normalized = ((color shr shift) and 0xFF).toDouble() / 255.0
    return if (normalized <= 0.04045) normalized / 12.92
    else ((normalized + 0.055) / 1.055).pow(2.4)
  }
  return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private const val MIN_TEXT_CONTRAST = 4.5
