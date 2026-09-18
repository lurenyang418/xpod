package app.xpod.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
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

sealed interface VideoDeleteResult {
  data object Completed : VideoDeleteResult

  data class NeedsUserConfirmation(val intentSender: IntentSender) : VideoDeleteResult
}

@Singleton
class LocalVideoRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: XpodDatabase,
    private val settings: SettingsRepository,
) {
  val videos: Flow<List<LocalVideoEntity>> = database.localVideos().observeAll()
  val treeUri: Flow<String?> = settings.localVideoTreeUri

  fun hasVideoPermission(): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
          PackageManager.PERMISSION_GRANTED

  suspend fun sourceValue(): String? = settings.localVideoTreeUriValue()

  suspend fun hasIndexedVideos(): Boolean = database.localVideos().count() > 0

  suspend fun selectTree(uri: Uri): Int =
      withContext(Dispatchers.IO) {
        val previousTree = settings.localVideoTreeUriValue()
        val selectedTree = uri.toString()
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val videos =
            try {
              scan(uri)
            } catch (error: Throwable) {
              if (previousTree != selectedTree) releaseTreePermission(uri)
              throw error
            }
        try {
          settings.setLocalVideoTreeUri(selectedTree)
          replaceVideos(videos)
        } catch (error: Throwable) {
          withContext(NonCancellable) { settings.setLocalVideoTreeUri(previousTree) }
          if (previousTree != selectedTree) releaseTreePermission(uri)
          throw error
        }
        if (previousTree != null && previousTree != selectedTree) {
          releaseTreePermission(previousTree.toUri())
        }
        videos.size
      }

  suspend fun refresh(): Int =
      withContext(Dispatchers.IO) {
        val source = settings.localVideoTreeUriValue() ?: error("No local video source selected")
        val videos =
            if (isGlobalVideoSource(source)) {
              check(hasVideoPermission()) { "Video permission is not granted" }
              scanMediaStore()
            } else {
              scan(source.toUri())
            }
        replaceVideos(videos)
        if (source == LOCAL_VIDEO_ALL_FILES_SOURCE) {
          // Migrate the source marker after the MediaStore-backed index is committed.
          settings.setLocalVideoTreeUri(LOCAL_VIDEO_MEDIA_SOURCE)
        }
        videos.size
      }

  suspend fun enableAutoScan(): Int =
      withContext(Dispatchers.IO) {
        check(hasVideoPermission()) { "Video permission is not granted" }
        val previousSource = settings.localVideoTreeUriValue()
        val videos = scanMediaStore()
        try {
          replaceVideos(videos)
          settings.setLocalVideoTreeUri(LOCAL_VIDEO_MEDIA_SOURCE)
        } catch (error: Throwable) {
          try {
            withContext(NonCancellable) { settings.setLocalVideoTreeUri(previousSource) }
          } catch (rollbackError: Throwable) {
            error.addSuppressed(rollbackError)
          }
          throw error
        }
        if (previousSource != null && !isGlobalVideoSource(previousSource)) {
          releaseTreePermission(previousSource.toUri())
        }
        videos.size
      }

  suspend fun video(id: String): LocalVideoEntity? = database.localVideos().find(id)

  suspend fun updateProgress(id: String, positionMs: Long, epochMs: Long) {
    database.localVideos().updateProgress(id, positionMs.coerceAtLeast(0L), epochMs)
  }

  suspend fun renameVideo(video: LocalVideoEntity, requestedTitle: String) {
    withContext(Dispatchers.IO) {
      val title = requestedTitle.trim()
      require(title.isNotBlank()) { "Video title cannot be blank" }
      val uri = video.documentUri.toUri()
      val currentName = queryDisplayName(uri) ?: video.title
      val extension = currentName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
      val displayName =
          if (extension != null && !title.substringAfterLast('/').contains('.')) {
            "$title.$extension"
          } else {
            title
          }
      if (DocumentsContract.isDocumentUri(context, uri)) {
        check(DocumentsContract.renameDocument(context.contentResolver, uri, displayName) != null) {
          "Unable to rename video"
        }
      } else {
        val updated =
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                  put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                },
                null,
                null,
            )
        check(updated > 0) { "Unable to rename video" }
      }
    }
  }

  suspend fun deleteVideos(
      videos: List<LocalVideoEntity>,
      requestUserConfirmation: Boolean = true,
  ): VideoDeleteResult =
      withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext VideoDeleteResult.Completed
        val uris = videos.map { it.documentUri.toUri() }.distinct()
        val mediaUris = uris.filterNot { DocumentsContract.isDocumentUri(context, it) }
        if (requestUserConfirmation && mediaUris.isNotEmpty()) {
          return@withContext VideoDeleteResult.NeedsUserConfirmation(
              MediaStore.createDeleteRequest(context.contentResolver, mediaUris).intentSender
          )
        }
        uris.forEach { uri ->
          val deleted =
              if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
              } else {
                context.contentResolver.delete(uri, null, null) > 0
              }
          check(deleted) { "Unable to delete video: $uri" }
        }
        VideoDeleteResult.Completed
      }

  private fun queryDisplayName(uri: Uri): String? {
    return context.contentResolver
        .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
  }

  private fun releaseTreePermission(uri: Uri) {
    runCatching {
      context.contentResolver.releasePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
  }

  private suspend fun replaceVideos(
      videos: List<LocalVideoEntity>,
      existing: Map<String, LocalVideoEntity>? = null,
  ) {
    val existingVideos = existing ?: database.localVideos().all().associateBy(LocalVideoEntity::id)
    val merged = mergeLocalVideos(videos, existingVideos)
    database.withTransaction {
      database.localVideos().clear()
      if (merged.isNotEmpty()) database.localVideos().upsertAll(merged)
    }
  }

  private data class PendingDirectory(val documentId: String, val relativePath: String)

  private suspend fun scan(treeUri: Uri): List<LocalVideoEntity> {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val visited = mutableSetOf<String>()
    val videos = mutableListOf<LocalVideoEntity>()
    val pendingDirectories =
        ArrayDeque<PendingDirectory>().apply {
          addLast(PendingDirectory(documentId = rootId, relativePath = ""))
        }
    while (pendingDirectories.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val directory = pendingDirectories.removeLast()
      if (!visited.add(directory.documentId)) continue
      scanChildren(treeUri, directory, pendingDirectories, videos)
    }
    return videos.distinctBy(LocalVideoEntity::id).sortedBy { it.title.lowercase(Locale.ROOT) }
  }

  private suspend fun scanMediaStore(): List<LocalVideoEntity> =
      withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cursor =
            context.contentResolver.query(
                contentUri,
                MEDIA_STORE_PROJECTION,
                null,
                null,
                null,
            ) ?: error("Unable to query MediaStore video")
        cursor
            .use { resultCursor ->
              val idIndex = resultCursor.getColumnIndex(MediaStore.Video.Media._ID)
              val displayNameIndex =
                  resultCursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
              val titleIndex = resultCursor.getColumnIndex(MediaStore.Video.Media.TITLE)
              val durationIndex = resultCursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
              val widthIndex = resultCursor.getColumnIndex(MediaStore.Video.VideoColumns.WIDTH)
              val heightIndex = resultCursor.getColumnIndex(MediaStore.Video.VideoColumns.HEIGHT)
              val sizeIndex = resultCursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
              val modifiedIndex = resultCursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
              val relativePathIndex =
                  resultCursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
              val videos = mutableListOf<LocalVideoEntity>()
              while (resultCursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (idIndex < 0 || resultCursor.isNull(idIndex)) continue
                val mediaId = resultCursor.getLong(idIndex)
                val displayName = resultCursor.stringOrEmpty(displayNameIndex)
                val mediaUri = ContentUris.withAppendedId(contentUri, mediaId)
                videos +=
                    LocalVideoEntity(
                        id = mediaStoreVideoId(MediaStore.VOLUME_EXTERNAL, mediaId),
                        documentUri = mediaUri.toString(),
                        treeUri = LOCAL_VIDEO_MEDIA_SOURCE,
                        title =
                            resultCursor
                                .stringOrEmpty(titleIndex)
                                .trim()
                                .takeUnless { it.isBlank() } ?: videoTitleFrom(displayName),
                        durationMs = resultCursor.longOrZero(durationIndex),
                        width = resultCursor.intOrZero(widthIndex),
                        height = resultCursor.intOrZero(heightIndex),
                        fileSizeBytes = resultCursor.longOrZero(sizeIndex),
                        modifiedEpochMs = resultCursor.longOrZero(modifiedIndex) * 1_000L,
                        relativePath =
                            resultCursor.stringOrEmpty(relativePathIndex).trim('/').trim(),
                    )
              }
              videos.distinctBy(LocalVideoEntity::id)
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
      }

  private suspend fun scanChildren(
      treeUri: Uri,
      parent: PendingDirectory,
      pendingDirectories: ArrayDeque<PendingDirectory>,
      videos: MutableList<LocalVideoEntity>,
  ) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
    queryChildren(childrenUri).use { cursor ->
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
          pendingDirectories.addLast(
              PendingDirectory(
                  documentId = documentId,
                  relativePath = appendRelativePath(parent.relativePath, displayName),
              )
          )
          continue
        }
        if (!isSupportedVideoDocument(mimeType, displayName)) continue
        val modifiedEpochMs =
            if (modifiedIndex < 0 || cursor.isNull(modifiedIndex)) 0L
            else cursor.getLong(modifiedIndex)
        val fileSizeBytes =
            if (sizeIndex < 0 || cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        runCatching {
              readVideo(
                  treeUri = treeUri,
                  documentUri = documentUri,
                  documentId = documentId,
                  displayName = displayName,
                  fileSizeBytes = fileSizeBytes,
                  modifiedEpochMs = modifiedEpochMs,
                  relativePath = parent.relativePath,
              )
            }
            .onSuccess { videos += it }
            .onFailure { Log.w(TAG, "Unable to read video metadata for $documentUri", it) }
      }
    }
  }

  private suspend fun queryChildren(childrenUri: Uri): Cursor =
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
          continuation.resume(requireVideoChildrenCursor(cursor, childrenUri.toString())) { _, rejectedCursor, _ ->
            rejectedCursor.close()
          }
        } catch (error: Throwable) {
          continuation.resumeWithException(error)
        }
      }

  private fun readVideo(
      treeUri: Uri,
      documentUri: Uri,
      documentId: String,
      displayName: String,
      fileSizeBytes: Long,
      modifiedEpochMs: Long,
      relativePath: String,
  ): LocalVideoEntity {
    var durationMs = 0L
    var width = 0
    var height = 0
    runCatching {
          MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, documentUri)
            durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L) ?: 0L
            width =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0
            height =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0
          }
        }
        .onFailure { Log.w(TAG, "Unable to read metadata for $documentUri", it) }
    return LocalVideoEntity(
        id = localVideoId(treeUri.authority.orEmpty(), documentId),
        documentUri = documentUri.toString(),
        treeUri = treeUri.toString(),
        title = videoTitleFrom(displayName),
        durationMs = durationMs,
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        modifiedEpochMs = modifiedEpochMs,
        relativePath = relativePath,
    )
  }

  private companion object {
    const val TAG = "XPOD"
    val DOCUMENT_PROJECTION =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    val MEDIA_STORE_PROJECTION =
        arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.WIDTH,
            MediaStore.Video.VideoColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
  }
}

internal fun requireVideoChildrenCursor(cursor: Cursor?, childrenUri: String): Cursor =
    cursor ?: error("Unable to query SAF video children: $childrenUri")

private fun Cursor.stringOrEmpty(index: Int): String =
    if (index < 0 || isNull(index)) "" else getString(index).orEmpty()

private fun Cursor.longOrZero(index: Int): Long =
    if (index < 0 || isNull(index)) 0L else getLong(index).coerceAtLeast(0L)

private fun Cursor.intOrZero(index: Int): Int =
    if (index < 0 || isNull(index)) 0 else getInt(index).coerceAtLeast(0)

internal fun isSupportedVideoDocument(mimeType: String, displayName: String): Boolean {
  if (mimeType.startsWith("video/", ignoreCase = true)) return true
  return displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) in
      setOf("3gp", "avi", "flv", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ts", "webm", "wmv")
}

internal fun localVideoId(authority: String, documentId: String): String =
    LOCAL_VIDEO_ID_PREFIX + FeedId.from("$authority|$documentId")

internal fun mediaStoreVideoId(volume: String, mediaId: Long): String =
    localVideoId("media.$volume", mediaId.toString())

internal fun mergeLocalVideos(
    videos: List<LocalVideoEntity>,
    existing: Map<String, LocalVideoEntity>,
): List<LocalVideoEntity> =
    videos.map { video ->
      val old = existing[video.id]
      val sourceChanged =
          old != null &&
              old.modifiedEpochMs > 0L &&
              video.modifiedEpochMs > 0L &&
              old.modifiedEpochMs != video.modifiedEpochMs
      video.copy(
          lastPositionMs = if (sourceChanged) 0L else old?.lastPositionMs ?: video.lastPositionMs,
          lastOpenedEpochMs =
              if (sourceChanged) 0L else old?.lastOpenedEpochMs ?: video.lastOpenedEpochMs,
      )
    }

internal fun videoTitleFrom(displayName: String): String =
    displayName.substringBeforeLast('.', displayName).trim().ifBlank { "Untitled video" }

const val LOCAL_VIDEO_ID_PREFIX = "local_video:"
const val LOCAL_VIDEO_MEDIA_SOURCE = "media-store://external-video"
// Kept so installations created by older versions can be migrated on refresh.
const val LOCAL_VIDEO_ALL_FILES_SOURCE = "all-files://shared-storage"

internal fun isGlobalVideoSource(source: String?): Boolean =
    source == LOCAL_VIDEO_MEDIA_SOURCE || source == LOCAL_VIDEO_ALL_FILES_SOURCE
