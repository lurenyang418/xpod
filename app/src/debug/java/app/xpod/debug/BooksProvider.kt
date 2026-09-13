package app.xpod.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Debug-only SAF document tree fixture backing [app.xpod.data.LocalBooksRepositoryIntegrationTest].
 *
 * It is hosted by the app itself (debug builds only) rather than the androidTest APK: the system
 * instantiates a test-APK provider in a standalone process without the Kotlin stdlib on its
 * classpath, and a cross-APK provider would also need to be exported. Hosting it here keeps it in
 * Kotlin and unexported.
 */
class BooksProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun query(
      uri: Uri,
      projection: Array<String>?,
      selection: String?,
      selectionArgs: Array<String>?,
      sortOrder: String?,
  ): Cursor {
    val columns = projection ?: DEFAULT_COLUMNS
    val parentId = uri.pathSegments[uri.pathSegments.size - 2]
    val cursor = MatrixCursor(columns)
    when (parentId) {
      ROOT_ID -> cursor.addRow(row(columns, SHELF_ID, "Shelf", DIRECTORY_MIME, 0L))
      SHELF_ID ->
          cursor.addRow(
              row(columns, DOCUMENT_ID, "Book.pdf", "application/pdf", TEST_CONTENT.size.toLong())
          )
    }
    return cursor
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    try {
      val pipe = ParcelFileDescriptor.createPipe()
      thread {
        try {
          ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(TEST_CONTENT) }
        } catch (_: IOException) {
          // The reader may close the pipe before the producer finishes.
        }
      }
      return pipe[0]
    } catch (error: IOException) {
      throw FileNotFoundException(error.message).apply { initCause(error) }
    }
  }

  override fun getType(uri: Uri): String =
      if (uri.pathSegments.last() == DOCUMENT_ID) "application/pdf" else DIRECTORY_MIME

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<String>?,
  ): Int = 0

  private fun row(
      columns: Array<String>,
      documentId: String,
      displayName: String,
      mimeType: String,
      size: Long,
  ): Array<Any?> =
      columns
          .map { column ->
            when (column) {
              "document_id" -> documentId
              "_display_name" -> displayName
              "mime_type" -> mimeType
              "last_modified" -> 1_000L
              "_size" -> size
              else -> null
            }
          }
          .toTypedArray()

  private companion object {
    const val ROOT_ID = "root"
    const val SHELF_ID = "shelf"
    const val DOCUMENT_ID = "book"
    const val DIRECTORY_MIME = "vnd.android.document/directory"
    val TEST_CONTENT = "test pdf bytes".toByteArray()
    val DEFAULT_COLUMNS =
        arrayOf("document_id", "_display_name", "mime_type", "last_modified", "_size")
  }
}
