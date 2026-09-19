package app.xpod.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.xpod.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

@Entity(indices = [Index("modifiedEpochMs")])
data class LocalMarkdownNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val createdEpochMs: Long,
    val modifiedEpochMs: Long,
    @ColumnInfo(defaultValue = "'FollowApp'") val theme: String = MarkdownThemeMode.FollowApp.name,
)

@Entity(tableName = "LocalMarkdownNoteSearch")
@Fts4(contentEntity = LocalMarkdownNoteEntity::class)
data class LocalMarkdownNoteSearchEntity(
    val title: String,
    val content: String,
)

enum class MarkdownThemeMode {
  FollowApp,
  GitHub,
  Newsprint,
  Night,
  Custom,
}

enum class MarkdownFontFamily {
  SansSerif,
  Serif,
}

data class MarkdownThemeSpec(
    val mode: MarkdownThemeMode,
    val backgroundArgb: Long,
    val textArgb: Long,
    val codeBackgroundArgb: Long,
    val codeTextArgb: Long,
    val linkArgb: Long,
    val quoteBackgroundArgb: Long,
    val tableBorderArgb: Long,
    val fontFamily: MarkdownFontFamily,
    val bodyFontSizeSp: Float = 16f,
    val headingFontSizeSp: Float = 30f,
    val paragraphSpacingDp: Int = 12,
    val contentWidthDp: Int = 800,
    val lineHeightMultiplier: Float = 1.65f,
)

internal fun markdownThemeSpec(mode: MarkdownThemeMode): MarkdownThemeSpec =
    when (mode) {
      MarkdownThemeMode.Newsprint ->
          MarkdownThemeSpec(
              mode = mode,
              backgroundArgb = 0xFFF4ECD8,
              textArgb = 0xFF3F372E,
              codeBackgroundArgb = 0xFFE9DFC9,
              codeTextArgb = 0xFF3F372E,
              linkArgb = 0xFF8A5A2B,
              quoteBackgroundArgb = 0xFFEDE2C8,
              tableBorderArgb = 0xFFC9BFA9,
              fontFamily = MarkdownFontFamily.Serif,
              bodyFontSizeSp = 17f,
              headingFontSizeSp = 32f,
              paragraphSpacingDp = 14,
              lineHeightMultiplier = 1.75f,
          )
      MarkdownThemeMode.Night ->
          MarkdownThemeSpec(
              mode = mode,
              backgroundArgb = 0xFF17181C,
              textArgb = 0xFFE5E1E8,
              codeBackgroundArgb = 0xFF252832,
              codeTextArgb = 0xFFE5E1E8,
              linkArgb = 0xFF9ECBFF,
              quoteBackgroundArgb = 0xFF20232A,
              tableBorderArgb = 0xFF454A57,
              fontFamily = MarkdownFontFamily.SansSerif,
          )
      MarkdownThemeMode.FollowApp,
      MarkdownThemeMode.GitHub ->
          MarkdownThemeSpec(
              mode = MarkdownThemeMode.GitHub,
              backgroundArgb = 0xFFFFFFFF,
              textArgb = 0xFF24292F,
              codeBackgroundArgb = 0xFFF6F8FA,
              codeTextArgb = 0xFF24292F,
              linkArgb = 0xFF0969DA,
              quoteBackgroundArgb = 0xFFF6F8FA,
              tableBorderArgb = 0xFFD0D7DE,
              fontFamily = MarkdownFontFamily.SansSerif,
          )
      MarkdownThemeMode.Custom -> markdownThemeSpec(MarkdownThemeMode.GitHub)
    }

internal fun parseMarkdownThemeMode(value: String?): MarkdownThemeMode =
    MarkdownThemeMode.entries.firstOrNull {
      it != MarkdownThemeMode.Custom && it.name == value
    } ?: MarkdownThemeMode.FollowApp

@Dao
interface LocalMarkdownNoteDao {
  @Query("SELECT * FROM LocalMarkdownNoteEntity ORDER BY modifiedEpochMs DESC, id DESC")
  fun observeAll(): Flow<List<LocalMarkdownNoteEntity>>

  @Query("SELECT * FROM LocalMarkdownNoteEntity WHERE id = :id")
  fun observe(id: Long): Flow<LocalMarkdownNoteEntity?>

  @Query(
      """
      SELECT n.*
      FROM LocalMarkdownNoteEntity n
      INNER JOIN LocalMarkdownNoteSearch s ON n.id = s.rowid
      WHERE LocalMarkdownNoteSearch MATCH :query
      ORDER BY n.modifiedEpochMs DESC, n.id DESC
      """
  )
  fun search(query: String): Flow<List<LocalMarkdownNoteEntity>>

  @Query("SELECT * FROM LocalMarkdownNoteEntity WHERE id = :id")
  suspend fun find(id: Long): LocalMarkdownNoteEntity?

  @Insert suspend fun insert(note: LocalMarkdownNoteEntity): Long

  @Upsert suspend fun upsert(note: LocalMarkdownNoteEntity)

  @Query("DELETE FROM LocalMarkdownNoteEntity WHERE id = :id") suspend fun delete(id: Long)
}

@Singleton
class LocalMarkdownNotesRepository
@Inject
constructor(
    private val database: XpodDatabase,
    @param:ApplicationContext private val context: Context,
) {
  private val notes = database.localMarkdownNotes()
  private val attachments = MarkdownNoteAttachmentStore(context)
  private val json = Json { prettyPrint = true }

  fun observe(query: String): Flow<List<LocalMarkdownNoteEntity>> {
    val normalized = query.trim()
    return if (normalized.isEmpty()) notes.observeAll()
    else notes.search(markdownFtsQuery(normalized))
  }

  fun observe(id: Long): Flow<LocalMarkdownNoteEntity?> = notes.observe(id)

  suspend fun find(id: Long): LocalMarkdownNoteEntity? = notes.find(id)

  suspend fun create(
      nowEpochMs: Long,
      theme: MarkdownThemeMode = MarkdownThemeMode.FollowApp,
      themeSelection: MarkdownThemeSelection? = null,
  ): Long =
      notes.insert(
          LocalMarkdownNoteEntity(
              createdEpochMs = nowEpochMs,
              modifiedEpochMs = nowEpochMs,
              theme = themeSelection?.toStorageValue() ?: theme.name,
          )
      )

  suspend fun save(
      id: Long,
      title: String,
      content: String,
      nowEpochMs: Long,
      theme: MarkdownThemeMode? = null,
      themeSelection: MarkdownThemeSelection? = null,
  ): Boolean {
    val current = notes.find(id) ?: return false
    val themeValue = themeSelection?.toStorageValue() ?: theme?.name ?: current.theme
    if (current.title == title && current.content == content && current.theme == themeValue) {
      return true
    }
    notes.upsert(
        current.copy(
            title = title,
            content = content,
            modifiedEpochMs = maxOf(nowEpochMs, current.modifiedEpochMs + 1L),
            theme = themeValue,
        )
    )
    return true
  }

  suspend fun delete(id: Long) {
    notes.delete(id)
    withContext(Dispatchers.IO) { attachments.deleteNoteAttachments(id) }
  }

  internal suspend fun attachImage(noteId: Long, source: Uri): MarkdownImageAttachment {
    check(notes.find(noteId) != null) { "Note no longer exists" }
    return attachments.importImage(noteId, source)
  }

  internal suspend fun imageAttachments(noteId: Long): Map<String, Uri> =
      withContext(Dispatchers.IO) { attachments.attachmentsForNote(noteId) }

  internal suspend fun removeImageAttachment(noteId: Long, reference: String) {
    withContext(Dispatchers.IO) { attachments.remove(noteId, reference) }
  }

  suspend fun importMarkdown(uri: Uri, nowEpochMs: Long, untitledLabel: String): Long =
      withContext(Dispatchers.IO) {
        val displayName =
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                  val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                  if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: throw IllegalArgumentException("The selected file has no name")
        val input =
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected file could not be opened")
        val content = input.use(::readMarkdownImportContent)
        notes.insert(markdownNoteFromImport(displayName, content, nowEpochMs, untitledLabel))
      }

  suspend fun exportMarkdown(note: LocalMarkdownNoteEntity, target: Uri) {
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(target)?.use { output ->
        OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
          writeMarkdown(writer, note)
        }
      } ?: error("Could not open the Markdown export target")
    }
  }

  suspend fun writeMarkdownToFile(note: LocalMarkdownNoteEntity, target: File) {
    withContext(Dispatchers.IO) {
      target.parentFile?.mkdirs()
      FileOutputStream(target).use { output ->
        OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
          writeMarkdown(writer, note)
        }
      }
    }
  }

  suspend fun exportHtml(note: LocalMarkdownNoteEntity, target: Uri, theme: MarkdownThemeMode) =
      exportHtml(note, target, markdownThemeSpec(theme))

  suspend fun exportHtml(note: LocalMarkdownNoteEntity, target: Uri, theme: MarkdownThemeSpec) {
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(target)?.use { output ->
        OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
          writer.write(markdownHtmlDocument(note, theme, context.getString(R.string.untitled_note)))
        }
      } ?: error("Could not open the HTML export target")
    }
  }

  suspend fun exportPdf(note: LocalMarkdownNoteEntity, target: Uri, theme: MarkdownThemeMode) =
      exportPdf(note, target, markdownThemeSpec(theme))

  suspend fun exportPdf(note: LocalMarkdownNoteEntity, target: Uri, theme: MarkdownThemeSpec) {
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(target)?.use { output ->
        MarkdownPdfExporter(context)
            .write(
                note = note,
                theme = theme,
                untitledLabel = context.getString(R.string.untitled_note),
                output = output,
            )
      } ?: error("Could not open the PDF export target")
    }
  }

  suspend fun exportZip(
      allNotes: List<LocalMarkdownNoteEntity>,
      target: Uri,
      exportedAtEpochMs: Long,
      appVersion: String,
      customThemes: List<MarkdownCustomTheme> = emptyList(),
  ) {
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(target)?.use { output ->
        ZipOutputStream(output).use { zip ->
          val usedNames = mutableSetOf<String>()
          val exportedNotes = mutableListOf<JsonObject>()
          var attachmentCount = 0
          allNotes.forEachIndexed { index, note ->
            val uniqueName =
                uniqueArchiveBaseName(
                    note.displayTitle(context.getString(R.string.untitled_note)),
                    index + 1,
                    usedNames,
                )
            val attachmentFolder = "attachments/note-${note.id}"
            val replacements = attachments.addToZip(zip, note.id, attachmentFolder)
            attachmentCount += replacements.size
            val exportedContent = rewriteMarkdownAttachmentReferences(note.content, replacements)
            zip.putNextEntry(ZipEntry("$uniqueName.md"))
            zip.write(
                normalizeMarkdownForExport(exportedContent).toByteArray(StandardCharsets.UTF_8)
            )
            zip.closeEntry()
            exportedNotes += buildJsonObject {
              put("fileName", "$uniqueName.md")
              put("theme", note.theme)
            }
          }
          zip.putNextEntry(ZipEntry("manifest.json"))
          val manifest =
              json.encodeToString(
                  JsonObject.serializer(),
                  buildJsonObject {
                    put("formatVersion", 1)
                    put("exportedAtEpochMs", exportedAtEpochMs)
                    put("noteCount", allNotes.size)
                    put("attachmentCount", attachmentCount)
                    put("appVersion", appVersion)
                    put("notes", buildJsonArray { exportedNotes.forEach { add(it) } })
                    put(
                        "customThemes",
                        buildJsonArray {
                          customThemes.forEach { add(markdownThemeJsonObject(it)) }
                        },
                    )
                  },
              )
          zip.write(manifest.toByteArray(StandardCharsets.UTF_8))
          zip.closeEntry()
        }
      } ?: error("Could not open the ZIP export target")
    }
  }

  suspend fun all(): List<LocalMarkdownNoteEntity> = notes.observeAll().first()
}

internal fun LocalMarkdownNoteEntity.displayTitle(untitledLabel: String): String =
    title.trim().takeIf(String::isNotEmpty) ?: firstMarkdownHeading(content) ?: untitledLabel

internal fun firstMarkdownHeading(content: String): String? =
    content
        .lineSequence()
        .map { it.trim() }
        .firstNotNullOfOrNull { line ->
          line
              .removePrefix("# ")
              .takeIf { line.startsWith("# ") }
              ?.trim()
              ?.takeIf(String::isNotEmpty)
        }

internal fun markdownNoteFromImport(
    displayName: String,
    content: String,
    nowEpochMs: Long,
    untitledLabel: String,
): LocalMarkdownNoteEntity {
  val trimmedDisplayName = displayName.trim()
  if (!trimmedDisplayName.endsWith(".md", ignoreCase = true)) {
    throw UnsupportedMarkdownImportException()
  }
  if (content.length > MAX_MARKDOWN_NOTE_CONTENT_LENGTH) {
    throw MarkdownImportTooLargeException()
  }
  val filenameTitle = trimmedDisplayName.dropLast(3).trim().ifBlank { untitledLabel }
  val title = (firstMarkdownHeading(content) ?: filenameTitle).take(MAX_MARKDOWN_NOTE_TITLE_LENGTH)
  return LocalMarkdownNoteEntity(
      title = title,
      content = content,
      createdEpochMs = nowEpochMs,
      modifiedEpochMs = nowEpochMs,
  )
}

internal fun readMarkdownImportContent(input: InputStream): String {
  val content = StringBuilder()
  input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
    val buffer = CharArray(8_192)
    val limitIncludingBom = MAX_MARKDOWN_NOTE_CONTENT_LENGTH + 2
    while (content.length < limitIncludingBom) {
      val count = reader.read(buffer, 0, minOf(buffer.size, limitIncludingBom - content.length))
      if (count < 0) break
      content.append(buffer, 0, count)
    }
  }
  val result = content.toString().removePrefix("\uFEFF")
  if (result.length > MAX_MARKDOWN_NOTE_CONTENT_LENGTH) throw MarkdownImportTooLargeException()
  return result
}

internal class UnsupportedMarkdownImportException : IllegalArgumentException()

internal class MarkdownImportTooLargeException : IllegalArgumentException()

internal const val MAX_MARKDOWN_NOTE_CONTENT_LENGTH = 500_000
internal const val MAX_MARKDOWN_NOTE_TITLE_LENGTH = 200

internal fun normalizeMarkdownForExport(content: String): String =
    content.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"

internal fun safeExportBaseName(title: String, fallbackIndex: Int): String {
  val sanitized =
      title
          .trim()
          .replace(Regex("[\\\\/:*?\"<>|]"), "_")
          .replace(Regex("\\s+"), " ")
          .trim('.')
          .take(80)
  return sanitized.ifEmpty { "note-$fallbackIndex" }
}

internal fun uniqueArchiveBaseName(
    title: String,
    fallbackIndex: Int,
    usedNames: MutableSet<String>,
): String {
  val baseName = safeExportBaseName(title, fallbackIndex)
  var candidate = baseName
  var occurrence = 2
  while (!usedNames.add(candidate.lowercase(Locale.ROOT))) {
    candidate = "$baseName ($occurrence)"
    occurrence++
  }
  return candidate
}

internal fun markdownFtsQuery(value: String): String =
    value.trim().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" AND ") { token ->
      "\"${token.replace("\"", "\"\"")}\"*"
    }

internal fun markdownHtmlDocument(
    note: LocalMarkdownNoteEntity,
    theme: MarkdownThemeMode,
    untitledLabel: String,
): String = markdownHtmlDocument(note, markdownThemeSpec(theme), untitledLabel)

internal fun markdownHtmlDocument(
    note: LocalMarkdownNoteEntity,
    theme: MarkdownThemeSpec,
    untitledLabel: String,
): String {
  val markdown = normalizeMarkdownForExport(note.content)
  val flavour = GFMFlavourDescriptor()
  val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
  val body = HtmlGenerator(markdown, tree, flavour, false).generateHtml().sanitizeMarkdownHtml()
  return """
      <!doctype html>
      <html lang="zh-CN">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escapeHtml(note.displayTitle(untitledLabel))}</title>
        <style>${markdownThemeCss(theme)}</style>
      </head>
      <body><main>$body</main></body>
      </html>
  """
      .trimIndent()
}

private fun String.sanitizeMarkdownHtml(): String =
    replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
        .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
        .replace(
            Regex("(?i)(<(?:img|a)\\b[^>]*\\b(?:src|href)\\s*=\\s*)([\\\"])([^\\\"]+)(\\\")")
        ) { match ->
          val url = match.groupValues[3]
          if (url.startsWith("https://", ignoreCase = true)) match.value
          else "${match.groupValues[1]}${match.groupValues[2]}#${match.groupValues[4]}"
        }
        .replace(Regex("(?i)(<(?:img|a)\\b[^>]*\\b(?:src|href)\\s*=\\s*)(['])([^']+)(['])")) { match
          ->
          val url = match.groupValues[3]
          if (url.startsWith("https://", ignoreCase = true)) match.value
          else "${match.groupValues[1]}${match.groupValues[2]}#${match.groupValues[4]}"
        }
        .replace(Regex("(?i)(<(?:img|a)\\b[^>]*\\b(?:src|href)\\s*=\\s*)([^\\s>\"']+)")) { match ->
          val url = match.groupValues[2].trimEnd('/')
          if (url.startsWith("https://", ignoreCase = true)) match.value
          else "${match.groupValues[1]}#"
        }
        .replace(
            Regex(
                "(?i)\\s+(?:on[a-z]+|style|srcdoc|formaction)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)"
            ),
            "",
        )
        .replace(Regex("(?is)<[^>]+>")) { match ->
          if (SAFE_HTML_TAG.matches(match.value)) match.value else ""
        }

private val SAFE_HTML_TAG =
    Regex(
        "(?i)</?(?:a|blockquote|br|code|del|div|em|h[1-6]|hr|img|input|li|ol|p|pre|span|strong|table|tbody|td|tfoot|th|thead|tr|ul)(?:\\s[^>]*)?/?>"
    )

private fun markdownThemeCss(spec: MarkdownThemeSpec): String {
  val font =
      if (spec.fontFamily == MarkdownFontFamily.Serif) "Georgia,serif" else "system-ui,sans-serif"
  return "body{background:${spec.backgroundArgb.toCssHex()};color:${spec.textArgb.toCssHex()};font-family:$font;font-size:${spec.bodyFontSizeSp}px;line-height:${spec.lineHeightMultiplier}}main{max-width:${spec.contentWidthDp}px;margin:0 auto;padding:32px}p{margin:0 0 ${spec.paragraphSpacingDp}px}pre{background:${spec.codeBackgroundArgb.toCssHex()};color:${spec.codeTextArgb.toCssHex()};padding:16px;overflow:auto}code{background:${spec.codeBackgroundArgb.toCssHex()};color:${spec.codeTextArgb.toCssHex()};padding:2px 4px}blockquote{background:${spec.quoteBackgroundArgb.toCssHex()};border-left:4px solid ${spec.tableBorderArgb.toCssHex()};margin:16px 0;padding:8px 16px}table{border-collapse:collapse}th,td{border:1px solid ${spec.tableBorderArgb.toCssHex()};padding:6px 10px}a{color:${spec.linkArgb.toCssHex()}}"
}

private fun Long.toCssHex(): String = "#%06x".format(this and 0xFFFFFF)

private fun writeMarkdown(writer: java.io.Writer, note: LocalMarkdownNoteEntity) {
  writer.write(normalizeMarkdownForExport(note.content))
}

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
