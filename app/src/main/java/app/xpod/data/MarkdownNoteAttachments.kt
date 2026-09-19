package app.xpod.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MarkdownImageAttachment(
    val markdownReference: String,
    val fileUri: Uri,
    val altText: String,
)

internal data class MarkdownAttachmentFile(
    val markdownReference: String,
    val file: File,
    val mimeType: String,
)

internal class MarkdownNoteAttachmentStore constructor(private val context: Context) {
  suspend fun importImage(noteId: Long, source: Uri): MarkdownImageAttachment =
      withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName =
            resolver
                .query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                  val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                  if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
                .orEmpty()
        val mimeType =
            resolver.getType(source)?.lowercase(Locale.ROOT)
                ?: mimeTypeFromName(displayName)
                ?: throw UnsupportedMarkdownImageException()
        val extension =
            MARKDOWN_IMAGE_MIME_TYPES[mimeType] ?: throw UnsupportedMarkdownImageException()
        val directory = attachmentDirectory(noteId)
        if (!directory.exists() && !directory.mkdirs())
            error("Could not create note attachment directory")

        val temporaryFile = File.createTempFile("image-", ".tmp", directory)
        try {
          val input = resolver.openInputStream(source) ?: error("Could not open selected image")
          var byteCount = 0L
          input.use { stream ->
            FileOutputStream(temporaryFile).use { output ->
              val buffer = ByteArray(16 * 1024)
              while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                byteCount += count
                if (byteCount > MAX_MARKDOWN_IMAGE_BYTES) throw MarkdownImageTooLargeException()
                output.write(buffer, 0, count)
              }
            }
          }
          validateMarkdownImage(temporaryFile)

          val fileName = "${UUID.randomUUID()}.$extension"
          val attachment = File(directory, fileName)
          check(temporaryFile.renameTo(attachment)) { "Could not store selected image" }
          MarkdownImageAttachment(
              markdownReference = requireNotNull(markdownAttachmentReference(fileName)),
              fileUri = Uri.fromFile(attachment),
              altText = safeMarkdownImageAlt(displayName),
          )
        } finally {
          temporaryFile.delete()
        }
      }

  fun attachmentsForNote(noteId: Long): Map<String, Uri> =
      attachmentFiles(noteId).associate { it.markdownReference to Uri.fromFile(it.file) }

  fun attachmentFiles(noteId: Long): List<MarkdownAttachmentFile> =
      attachmentDirectory(noteId)
          .listFiles()
          .orEmpty()
          .asSequence()
          .filter(File::isFile)
          .mapNotNull { file ->
            val reference = markdownAttachmentReference(file.name) ?: return@mapNotNull null
            val mimeType =
                MARKDOWN_IMAGE_MIME_TYPES.entries.firstOrNull { it.value == file.extension }?.key
                    ?: return@mapNotNull null
            MarkdownAttachmentFile(reference, file, mimeType)
          }
          .toList()

  fun remove(noteId: Long, reference: String) {
    val fileName = markdownAttachmentFileName(reference) ?: return
    val directory = attachmentDirectory(noteId).canonicalFile
    val file = File(directory, fileName).canonicalFile
    if (file.parentFile == directory) file.delete()
  }

  fun deleteNoteAttachments(noteId: Long) {
    attachmentDirectory(noteId).deleteRecursively()
  }

  fun addToZip(
      zip: ZipOutputStream,
      noteId: Long,
      folderName: String,
  ): Map<String, String> {
    val references = mutableMapOf<String, String>()
    attachmentFiles(noteId).forEach { attachment ->
      val relativePath = "$folderName/${attachment.file.name}"
      zip.putNextEntry(ZipEntry(relativePath))
      attachment.file.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
      references[attachment.markdownReference] = relativePath
    }
    return references
  }

  private fun attachmentDirectory(noteId: Long): File =
      File(File(context.filesDir, MARKDOWN_ATTACHMENTS_DIRECTORY), noteId.toString())
}

internal fun markdownAttachmentReference(fileName: String): String? =
    fileName.takeIf(MARKDOWN_ATTACHMENT_FILE_NAME::matches)?.let { "xpod-attachment://$it" }

internal fun markdownAttachmentFileName(reference: String): String? =
    MARKDOWN_ATTACHMENT_REFERENCE.matchEntire(reference)?.groupValues?.get(1)

internal fun rewriteMarkdownAttachmentReferences(
    content: String,
    replacements: Map<String, String>,
): String =
    replacements.entries.fold(content) { markdown, (reference, replacement) ->
      markdown.replace("($reference)", "($replacement)")
    }

private fun safeMarkdownImageAlt(name: String): String =
    name
        .substringBeforeLast('.', name)
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace(Regex("[\\r\\n]+"), " ")
        .ifBlank { "image" }

private fun mimeTypeFromName(name: String): String? =
    MARKDOWN_IMAGE_MIME_TYPES.entries
        .firstOrNull { it.value == name.substringAfterLast('.', "").lowercase(Locale.ROOT) }
        ?.key

private fun validateMarkdownImage(file: File) {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.absolutePath, bounds)
  val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
  if (
      bounds.outWidth <= 0 ||
          bounds.outHeight <= 0 ||
          bounds.outWidth > MAX_MARKDOWN_IMAGE_DIMENSION ||
          bounds.outHeight > MAX_MARKDOWN_IMAGE_DIMENSION ||
          pixels > MAX_MARKDOWN_IMAGE_PIXELS
  ) {
    throw UnsupportedMarkdownImageException()
  }
}

internal class UnsupportedMarkdownImageException : IllegalArgumentException()

internal class MarkdownImageTooLargeException : IllegalArgumentException()

internal const val MAX_MARKDOWN_IMAGE_BYTES = 20L * 1024 * 1024
private const val MAX_MARKDOWN_IMAGE_DIMENSION = 24_000
private const val MAX_MARKDOWN_IMAGE_PIXELS = 100_000_000L
private const val MARKDOWN_ATTACHMENTS_DIRECTORY = "markdown-note-attachments"
private val MARKDOWN_IMAGE_MIME_TYPES =
    mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
    )
private val MARKDOWN_ATTACHMENT_FILE_NAME =
    Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp|gif)"
    )
private val MARKDOWN_ATTACHMENT_REFERENCE =
    Regex(
        "xpod-attachment://([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png|webp|gif))"
    )
