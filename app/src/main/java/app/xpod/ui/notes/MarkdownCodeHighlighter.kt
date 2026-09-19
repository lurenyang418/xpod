package app.xpod.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

internal enum class MarkdownCodeToken {
  Keyword,
  String,
  Comment,
  Number,
}

internal data class MarkdownCodeSpan(
    val start: Int,
    val end: Int,
    val token: MarkdownCodeToken,
)

internal data class MarkdownSyntaxColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
)

internal object MarkdownCodeHighlighter {
  const val MAX_SOURCE_LENGTH = 100_000

  private val keywords =
      mapOf(
          Language.Kotlin to
              setOf(
                  "as",
                  "break",
                  "by",
                  "catch",
                  "class",
                  "companion",
                  "const",
                  "continue",
                  "data",
                  "do",
                  "else",
                  "enum",
                  "false",
                  "finally",
                  "for",
                  "fun",
                  "if",
                  "import",
                  "in",
                  "init",
                  "interface",
                  "internal",
                  "is",
                  "lateinit",
                  "null",
                  "object",
                  "open",
                  "override",
                  "package",
                  "private",
                  "protected",
                  "public",
                  "return",
                  "sealed",
                  "super",
                  "suspend",
                  "this",
                  "throw",
                  "true",
                  "try",
                  "typealias",
                  "val",
                  "var",
                  "when",
                  "while",
              ),
          Language.Java to
              setOf(
                  "abstract",
                  "assert",
                  "boolean",
                  "break",
                  "byte",
                  "case",
                  "catch",
                  "char",
                  "class",
                  "continue",
                  "default",
                  "do",
                  "double",
                  "else",
                  "enum",
                  "extends",
                  "false",
                  "final",
                  "finally",
                  "float",
                  "for",
                  "if",
                  "implements",
                  "import",
                  "instanceof",
                  "int",
                  "interface",
                  "long",
                  "native",
                  "new",
                  "package",
                  "private",
                  "protected",
                  "public",
                  "return",
                  "short",
                  "static",
                  "super",
                  "switch",
                  "synchronized",
                  "this",
                  "throw",
                  "throws",
                  "transient",
                  "try",
                  "void",
                  "volatile",
                  "while",
              ),
          Language.Python to
              setOf(
                  "and",
                  "as",
                  "assert",
                  "async",
                  "await",
                  "break",
                  "class",
                  "continue",
                  "def",
                  "del",
                  "elif",
                  "else",
                  "except",
                  "finally",
                  "for",
                  "from",
                  "global",
                  "if",
                  "import",
                  "in",
                  "is",
                  "lambda",
                  "nonlocal",
                  "not",
                  "or",
                  "pass",
                  "raise",
                  "return",
                  "try",
                  "while",
                  "with",
                  "yield",
                  "None",
                  "False",
                  "True",
              ),
      )

  fun spans(source: String): List<MarkdownCodeSpan> {
    if (source.length > MAX_SOURCE_LENGTH) return emptyList()

    val result = mutableListOf<MarkdownCodeSpan>()
    var lineStart = 0
    var fence: Fence? = null
    var lexerState = LexerState()

    while (lineStart <= source.length) {
      val newline = source.indexOf('\n', lineStart)
      val lineEnd = if (newline < 0) source.length else newline
      val activeFence = fence
      if (activeFence == null) {
        openingFence(source, lineStart, lineEnd)?.let {
          fence = it
          lexerState = LexerState()
        }
      } else if (isClosingFence(source, lineStart, lineEnd, activeFence)) {
        fence = null
      } else if (activeFence.language != null) {
        scanCodeLine(
            source = source,
            start = lineStart,
            end = lineEnd,
            language = activeFence.language,
            state = lexerState,
            output = result,
        )
      }

      if (newline < 0) break
      lineStart = newline + 1
    }
    return result
  }

  private fun openingFence(source: String, start: Int, end: Int): Fence? {
    var cursor = start
    while (cursor < end && source[cursor] == ' ') cursor++
    if (cursor - start > 3 || cursor >= end) return null

    val marker = source[cursor]
    if (marker.code != 96 && marker != '~') return null
    var markerEnd = cursor
    while (markerEnd < end && source[markerEnd] == marker) markerEnd++
    val markerLength = markerEnd - cursor
    if (markerLength < 3) return null

    val languageName =
        source.substring(markerEnd, end).trim().substringBefore(' ').lowercase(Locale.ROOT)
    val language =
        when (languageName) {
          "kt",
          "kts",
          "kotlin" -> Language.Kotlin
          "java" -> Language.Java
          "py",
          "python" -> Language.Python
          else -> null
        }
    return Fence(marker, markerLength, language)
  }

  private fun isClosingFence(source: String, start: Int, end: Int, fence: Fence): Boolean {
    var cursor = start
    while (cursor < end && source[cursor] == ' ') cursor++
    if (cursor - start > 3) return false
    var markerEnd = cursor
    while (markerEnd < end && source[markerEnd] == fence.marker) markerEnd++
    return markerEnd - cursor >= fence.length && source.substring(markerEnd, end).isBlank()
  }

  private fun scanCodeLine(
      source: String,
      start: Int,
      end: Int,
      language: Language,
      state: LexerState,
      output: MutableList<MarkdownCodeSpan>,
  ) {
    var cursor = start
    while (cursor < end) {
      if (state.inBlockComment) {
        val close = source.indexOf("*/", cursor).takeIf { it >= 0 && it + 2 <= end }
        if (close == null) {
          output.addSpan(cursor, end, MarkdownCodeToken.Comment)
          return
        }
        output.addSpan(cursor, close + 2, MarkdownCodeToken.Comment)
        cursor = close + 2
        state.inBlockComment = false
        continue
      }

      if (state.inTripleString) {
        val close = source.indexOf("\"\"\"", cursor).takeIf { it >= 0 && it + 3 <= end }
        if (close == null) {
          output.addSpan(cursor, end, MarkdownCodeToken.String)
          return
        }
        output.addSpan(cursor, close + 3, MarkdownCodeToken.String)
        cursor = close + 3
        state.inTripleString = false
        continue
      }

      if (language != Language.Python && source.startsWith("/*", cursor)) {
        state.inBlockComment = true
        continue
      }
      if (
          (language == Language.Python && source[cursor] == '#') ||
              (language != Language.Python && source.startsWith("//", cursor))
      ) {
        output.addSpan(cursor, end, MarkdownCodeToken.Comment)
        return
      }

      val character = source[cursor]
      if (character == '"' || character == '\'') {
        if (character == '"' && source.startsWith("\"\"\"", cursor)) {
          val close = source.indexOf("\"\"\"", cursor + 3).takeIf { it >= 0 && it + 3 <= end }
          if (close == null) {
            output.addSpan(cursor, end, MarkdownCodeToken.String)
            state.inTripleString = true
            return
          }
          output.addSpan(cursor, close + 3, MarkdownCodeToken.String)
          cursor = close + 3
        } else {
          var stringEnd = cursor + 1
          var escaped = false
          while (stringEnd < end) {
            val current = source[stringEnd]
            if (!escaped && current == character) {
              stringEnd++
              break
            }
            if (current == '\\' && !escaped) {
              escaped = true
            } else {
              escaped = false
            }
            stringEnd++
          }
          output.addSpan(cursor, stringEnd, MarkdownCodeToken.String)
          cursor = stringEnd
        }
        continue
      }

      if (character.isLetter() || character == '_') {
        var wordEnd = cursor + 1
        while (wordEnd < end && (source[wordEnd].isLetterOrDigit() || source[wordEnd] == '_')) {
          wordEnd++
        }
        if (source.substring(cursor, wordEnd) in keywords.getValue(language)) {
          output.addSpan(cursor, wordEnd, MarkdownCodeToken.Keyword)
        }
        cursor = wordEnd
        continue
      }

      if (character.isDigit()) {
        var numberEnd = cursor + 1
        while (
            numberEnd < end &&
                (source[numberEnd].isLetterOrDigit() ||
                    source[numberEnd] == '_' ||
                    source[numberEnd] == '.')
        ) {
          numberEnd++
        }
        output.addSpan(cursor, numberEnd, MarkdownCodeToken.Number)
        cursor = numberEnd
        continue
      }

      cursor++
    }
  }

  private fun MutableList<MarkdownCodeSpan>.addSpan(
      start: Int,
      end: Int,
      token: MarkdownCodeToken,
  ) {
    if (start < end) add(MarkdownCodeSpan(start, end, token))
  }

  private data class Fence(val marker: Char, val length: Int, val language: Language?)

  private data class LexerState(
      var inBlockComment: Boolean = false,
      var inTripleString: Boolean = false,
  )

  private enum class Language {
    Kotlin,
    Java,
    Python,
  }
}

internal class MarkdownCodeVisualTransformation(
    private val colors: MarkdownSyntaxColors,
) : VisualTransformation {
  private var cachedSource: String? = null
  private var cachedText: AnnotatedString? = null

  override fun filter(text: AnnotatedString): TransformedText {
    val source = text.text
    val transformed =
        if (source.length > MarkdownCodeHighlighter.MAX_SOURCE_LENGTH) {
          text
        } else {
          if (source != cachedSource) {
            val builder = AnnotatedString.Builder(text)
            MarkdownCodeHighlighter.spans(source).forEach { span ->
              val style =
                  when (span.token) {
                    MarkdownCodeToken.Keyword ->
                        SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
                    MarkdownCodeToken.String -> SpanStyle(color = colors.string)
                    MarkdownCodeToken.Comment ->
                        SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                    MarkdownCodeToken.Number -> SpanStyle(color = colors.number)
                  }
              builder.addStyle(style, span.start, span.end)
            }
            cachedSource = source
            cachedText = builder.toAnnotatedString()
          }
          cachedText ?: text
        }
    return TransformedText(transformed, IdentityOffsetMapping)
  }

  private object IdentityOffsetMapping : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset

    override fun transformedToOriginal(offset: Int): Int = offset
  }
}
