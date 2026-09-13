package app.xpod.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.room.withTransaction
import app.xpod.data.reader.EpubBook
import app.xpod.data.reader.EpubBookParser
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Clock
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class BookScanResult(
    val bookCount: Int,
    val failureCount: Int,
)

@Singleton
class LocalBooksRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: XpodDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val epubParser: EpubBookParser,
) {
  val books: Flow<List<BookWithProgress>> = database.localBooks().observeAllWithProgress()
  val treeUri: Flow<String?> = settings.localBooksTreeUri

  suspend fun selectTree(uri: Uri): BookScanResult =
      withContext(Dispatchers.IO) {
        val previousTree = settings.localBooksTreeUriValue()
        val selectedTree = uri.toString()
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val scanned =
            try {
              scan(uri)
            } catch (error: Throwable) {
              if (previousTree != selectedTree) releaseTreePermission(uri)
              throw error
            }
        try {
          settings.setLocalBooksTreeUri(selectedTree)
        } catch (error: Throwable) {
          if (previousTree != selectedTree) releaseTreePermission(uri)
          throw error
        }
        try {
          replaceBooks(scanned.books)
        } catch (error: Throwable) {
          val settingsRolledBack =
              try {
                withContext(NonCancellable) { settings.setLocalBooksTreeUri(previousTree) }
                true
              } catch (rollbackError: Throwable) {
                error.addSuppressed(rollbackError)
                false
              }
          if (settingsRolledBack && previousTree != selectedTree) releaseTreePermission(uri)
          throw error
        }
        if (previousTree != null && previousTree != selectedTree) {
          releaseTreePermission(previousTree.toUri())
        }
        scanned.result
      }

  suspend fun refresh(): BookScanResult =
      withContext(Dispatchers.IO) {
        val selectedTree =
            settings.localBooksTreeUriValue() ?: error("No local books folder selected")
        val scanned = scan(selectedTree.toUri())
        replaceBooks(scanned.books)
        scanned.result
      }

  suspend fun book(id: String): LocalBookEntity? = database.localBooks().find(id)

  suspend fun progress(bookId: String): BookProgressEntity? = database.bookProgress().find(bookId)

  suspend fun openInputStream(bookId: String): InputStream {
    val book = database.localBooks().find(bookId) ?: error("Book not found: $bookId")
    return context.contentResolver.openInputStream(book.documentUri.toUri())
        ?: error("Unable to open book: $bookId")
  }

  suspend fun openFileDescriptor(bookId: String): android.os.ParcelFileDescriptor {
    val book = database.localBooks().find(bookId) ?: error("Book not found: $bookId")
    return context.contentResolver.openFileDescriptor(book.documentUri.toUri(), "r")
        ?: error("Unable to open book descriptor: $bookId")
  }

  suspend fun toggleFavorite(bookId: String) {
    database.localBooks().toggleFavorite(bookId)
  }

  suspend fun recordProgress(
      bookId: String,
      positionVersion: Int,
      positionJson: String,
      sourceModifiedEpochMs: Long,
      readingSeconds: Long = 0,
      updatedEpochMs: Long = clock.millis(),
  ) {
    database.withTransaction {
      database
          .bookProgress()
          .upsert(
              BookProgressEntity(
                  bookId = bookId,
                  positionVersion = positionVersion,
                  positionJson = positionJson,
                  sourceModifiedEpochMs = sourceModifiedEpochMs,
                  updatedEpochMs = updatedEpochMs,
                  readingSeconds = readingSeconds.coerceAtLeast(0),
              )
          )
      database.localBooks().updateLastOpened(bookId, updatedEpochMs)
    }
  }

  private suspend fun replaceBooks(scanned: List<LocalBookEntity>) {
    val existing = database.localBooks().all().associateBy(LocalBookEntity::id)
    val merged = scanned.map { book ->
      val old = existing[book.id]
      book.copy(
          addedEpochMs = old?.addedEpochMs ?: book.addedEpochMs,
          lastOpenedEpochMs = old?.lastOpenedEpochMs ?: book.lastOpenedEpochMs,
          isFavorite = old?.isFavorite ?: book.isFavorite,
          coverCachePath =
              mergeCoverCachePath(book.coverCachePath, old?.coverCachePath) {
                java.io.File(it).exists()
              },
      )
    }
    val retainedCoverPaths = merged.mapNotNull(LocalBookEntity::coverCachePath).toSet()
    val staleCoverPaths =
        existing.values
            .mapNotNull(LocalBookEntity::coverCachePath)
            .filterNot(retainedCoverPaths::contains)
    database.withTransaction {
      database.localBooks().clear()
      if (merged.isNotEmpty()) database.localBooks().upsertAll(merged)
      database.bookProgress().deleteOrphans()
    }
    staleCoverPaths.forEach { path -> File(path).delete() }
  }

  private data class ScannedBooks(
      val books: List<LocalBookEntity>,
      val result: BookScanResult,
  )

  private suspend fun scan(treeUri: Uri): ScannedBooks {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val visited = mutableSetOf<String>()
    val books = mutableListOf<LocalBookEntity>()
    var failureCount = 0
    val pendingDirectories =
        ArrayDeque<PendingDirectory>().apply {
          addLast(PendingDirectory(rootId, "", 0))
        }
    while (pendingDirectories.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      if (visited.size >= MAX_SCAN_DIRECTORIES) {
        failureCount += pendingDirectories.size
        break
      }
      val directory = pendingDirectories.removeLast()
      if (!visited.add(directory.documentId)) continue
      failureCount +=
          if (directory.depth == 0) {
            // The selected root being unreadable fails the scan; a subdirectory that
            // cannot be read is only counted so the rest of the tree still scans.
            scanChildren(treeUri, directory, pendingDirectories, books)
          } else {
            runCatchingCancellable { scanChildren(treeUri, directory, pendingDirectories, books) }
                .getOrElse { 1 }
          }
    }
    val uniqueBooks =
        books.distinctBy(LocalBookEntity::id).sortedBy { it.title.lowercase(Locale.ROOT) }
    return ScannedBooks(uniqueBooks, BookScanResult(uniqueBooks.size, failureCount))
  }

  private suspend fun scanChildren(
      treeUri: Uri,
      parent: PendingDirectory,
      pendingDirectories: ArrayDeque<PendingDirectory>,
      books: MutableList<LocalBookEntity>,
  ): Int {
    var failures = 0
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
    val cursor = queryChildren(childrenUri) ?: error("Unable to query books folder: $childrenUri")
    cursor.use {
      val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
      val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
      while (cursor.moveToNext()) {
        currentCoroutineContext().ensureActive()
        val documentId = cursor.getString(idIndex)
        val displayName = cursor.getString(nameIndex).orEmpty()
        val mimeType = cursor.getString(mimeIndex).orEmpty()
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
          if (parent.depth >= MAX_SCAN_DEPTH || pendingDirectories.size >= MAX_SCAN_DIRECTORIES) {
            failures++
          } else {
            pendingDirectories.addLast(
                PendingDirectory(
                    documentId,
                    appendRelativePath(parent.relativePath, displayName),
                    parent.depth + 1,
                )
            )
          }
          continue
        }
        val format = bookFormatFor(mimeType, displayName) ?: continue
        val modifiedEpochMs =
            if (modifiedIndex < 0 || cursor.isNull(modifiedIndex)) 0L
            else cursor.getLong(modifiedIndex)
        val fileSizeBytes =
            if (sizeIndex < 0 || cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        runCatchingCancellable {
              readBook(
                  treeUri = treeUri,
                  documentUri = documentUri,
                  documentId = documentId,
                  displayName = displayName,
                  format = format,
                  fileSizeBytes = fileSizeBytes,
                  modifiedEpochMs = modifiedEpochMs,
                  relativePath = parent.relativePath,
              )
            }
            .onSuccess { books += it }
            .onFailure { failures++ }
      }
    }
    return failures
  }

  private suspend fun readBook(
      treeUri: Uri,
      documentUri: Uri,
      documentId: String,
      displayName: String,
      format: BookFormat,
      fileSizeBytes: Long,
      modifiedEpochMs: Long,
      relativePath: String,
  ): LocalBookEntity {
    val bookId = localBookId(treeUri.authority.orEmpty(), documentId)
    var metadata: EpubBook? = null
    var coverCachePath: String? = null
    if (format == BookFormat.EPUB) {
      // Copy the EPUB into one temporary archive and read metadata and cover from it,
      // instead of copying the whole file once per read. The stream is closed inside
      // openArchive, which lint's Recycle check cannot see through the lambda.
      @Suppress("Recycle")
      val archive = epubParser.openArchive {
        context.contentResolver.openInputStream(documentUri) ?: error("Unable to open EPUB")
      }
      try {
        val parsed = epubParser.parseMetadata(archive)
        metadata = parsed
        coverCachePath =
            parsed.coverResourceKey?.let { resourceKey ->
              runCatchingCancellable {
                    epubParser.readResource(archive, resourceKey)?.let { cacheCover(bookId, it) }
                  }
                  .getOrNull()
            }
      } finally {
        archive.close()
      }
    }
    return LocalBookEntity(
        id = bookId,
        documentUri = documentUri.toString(),
        treeUri = treeUri.toString(),
        title =
            metadata?.title?.takeIf { it.isNotBlank() && it != "Untitled book" }
                ?: bookTitleFrom(displayName),
        author = metadata?.author.orEmpty(),
        language = metadata?.language.orEmpty(),
        format = format.name,
        fileSizeBytes = fileSizeBytes,
        modifiedEpochMs = modifiedEpochMs,
        relativePath = relativePath,
        coverCachePath = coverCachePath,
        addedEpochMs = clock.millis(),
    )
  }

  private fun cacheCover(bookId: String, bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / MAX_COVER_EDGE_PX)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val scaled =
        if (maxOf(bitmap.width, bitmap.height) > MAX_COVER_EDGE_PX) {
          val scale = MAX_COVER_EDGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
          bitmap
              .scale(
                  (bitmap.width * scale).toInt().coerceAtLeast(1),
                  (bitmap.height * scale).toInt().coerceAtLeast(1),
              )
              .also { bitmap.recycle() }
        } else {
          bitmap
        }
    val file = File(context.filesDir, "book-covers/$bookId.jpg")
    file.parentFile?.mkdirs()
    return runCatching {
          FileOutputStream(file).use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, output))
          }
          file.absolutePath
        }
        .getOrNull()
        .also { scaled.recycle() }
  }

  private suspend fun queryChildren(childrenUri: Uri): Cursor? =
      suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        try {
          val cursor =
              context.contentResolver.query(
                  childrenUri,
                  DOCUMENT_PROJECTION,
                  null,
                  cancellationSignal,
              )
          continuation.resume(cursor) { _, rejectedCursor, _ -> rejectedCursor?.close() }
        } catch (error: Throwable) {
          continuation.resumeWithException(error)
        }
      }

  private fun releaseTreePermission(uri: Uri) {
    runCatching {
      context.contentResolver.releasePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }
  }

  private data class PendingDirectory(
      val documentId: String,
      val relativePath: String,
      val depth: Int,
  )

  private companion object {
    const val MAX_COVER_EDGE_PX = 512
    const val MAX_SCAN_DEPTH = 64
    const val MAX_SCAN_DIRECTORIES = 10_000
    val DOCUMENT_PROJECTION =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
  }
}

internal fun mergeCoverCachePath(
    scannedPath: String?,
    existingPath: String?,
    existingPathExists: (String) -> Boolean,
): String? =
    if (scannedPath == null) null else existingPath?.takeIf(existingPathExists) ?: scannedPath

internal fun localBookId(authority: String, documentId: String): String =
    "book:" + FeedId.from("$authority|$documentId")

internal fun bookTitleFrom(displayName: String): String =
    displayName.substringBeforeLast('.', displayName).trim().ifBlank { "Untitled book" }

internal fun bookFormatFor(mimeType: String, displayName: String): BookFormat? {
  val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
  return when {
    mimeType.equals("application/epub+zip", ignoreCase = true) || extension == "epub" ->
        BookFormat.EPUB
    mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf" -> BookFormat.PDF
    else -> null
  }
}
