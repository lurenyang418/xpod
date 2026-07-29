package app.xpod.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LocalMusicRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: XpodDatabase,
    private val settings: SettingsRepository,
) {
  val tracks: Flow<List<LocalTrackEntity>> = database.localTracks().observeAll()
  val treeUri: Flow<String?> = settings.localMusicTreeUri

  suspend fun selectTree(uri: Uri): Int =
      withContext(Dispatchers.IO) {
        val previousTree = settings.localMusicTreeUriValue()
        val selectedTree = uri.toString()
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val tracks =
            try {
              scan(uri)
            } catch (error: Throwable) {
              if (previousTree != selectedTree) releaseTreePermission(uri)
              throw error
            }
        try {
          settings.setLocalMusicTreeUri(selectedTree)
        } catch (error: Throwable) {
          if (previousTree != selectedTree) releaseTreePermission(uri)
          throw error
        }
        try {
          replaceTracks(tracks)
        } catch (error: Throwable) {
          val settingsRolledBack =
              try {
                withContext(NonCancellable) { settings.setLocalMusicTreeUri(previousTree) }
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
        tracks.size
      }

  suspend fun refresh(): Int =
      withContext(Dispatchers.IO) {
        val tree = settings.localMusicTreeUriValue() ?: error("No local music folder selected")
        val tracks = scan(tree.toUri())
        replaceTracks(tracks)
        tracks.size
      }

  suspend fun track(id: String): LocalTrackEntity? = database.localTracks().find(id)

  suspend fun trackIds(): Set<String> = database.localTracks().ids().toSet()

  private fun releaseTreePermission(uri: Uri) {
    runCatching {
      context.contentResolver.releasePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }
  }

  private suspend fun replaceTracks(tracks: List<LocalTrackEntity>) {
    database.withTransaction {
      database.localTracks().clear()
      if (tracks.isNotEmpty()) database.localTracks().upsertAll(tracks)
    }
  }

  private suspend fun scan(treeUri: Uri): List<LocalTrackEntity> {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val visited = mutableSetOf<String>()
    val tracks = mutableListOf<LocalTrackEntity>()
    val pendingDirectories = ArrayDeque<String>().apply { addLast(rootId) }
    while (pendingDirectories.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val parentDocumentId = pendingDirectories.removeLast()
      if (!visited.add(parentDocumentId)) continue
      scanChildren(treeUri, parentDocumentId, pendingDirectories, tracks)
    }
    return tracks.distinctBy(LocalTrackEntity::id).sortedBy { it.title.lowercase() }
  }

  private suspend fun scanChildren(
      treeUri: Uri,
      parentDocumentId: String,
      pendingDirectories: ArrayDeque<String>,
      tracks: MutableList<LocalTrackEntity>,
  ) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    queryChildren(childrenUri)?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val modifiedIndex =
          cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
      while (cursor.moveToNext()) {
        currentCoroutineContext().ensureActive()
        val documentId = cursor.getString(idIndex)
        val name = cursor.getString(nameIndex).orEmpty()
        val mimeType = cursor.getString(mimeIndex).orEmpty()
        val modifiedEpochMs =
            if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
          pendingDirectories.addLast(documentId)
        } else if (isSupportedAudioDocument(mimeType, name)) {
          val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
          tracks +=
              readTrack(
                  treeUri = treeUri,
                  documentUri = documentUri,
                  documentId = documentId,
                  displayName = name,
                  modifiedEpochMs = modifiedEpochMs,
              )
        }
      }
    }
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

  private fun readTrack(
      treeUri: Uri,
      documentUri: Uri,
      documentId: String,
      displayName: String,
      modifiedEpochMs: Long,
  ): LocalTrackEntity {
    var metadataTitle: String? = null
    var artist = ""
    var album = ""
    var durationMs = 0L
    runCatching {
          MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, documentUri)
            metadataTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L) ?: 0L
          }
        }
        .onFailure { Log.w(TAG, "Unable to read metadata for $documentUri", it) }
    return LocalTrackEntity(
        id = localTrackId(treeUri.authority.orEmpty(), documentId),
        documentUri = documentUri.toString(),
        treeUri = treeUri.toString(),
        title = metadataTitle?.trim().takeUnless { it.isNullOrBlank() } ?: titleFrom(displayName),
        artist = artist.trim(),
        album = album.trim(),
        durationMs = durationMs,
        modifiedEpochMs = modifiedEpochMs,
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
        )
  }
}

internal fun isSupportedAudioDocument(mimeType: String, displayName: String): Boolean {
  if (mimeType.startsWith("audio/", ignoreCase = true)) return true
  return displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) in
      setOf("aac", "amr", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav", "wma")
}

internal fun localTrackId(authority: String, documentId: String): String =
    LOCAL_TRACK_ID_PREFIX + FeedId.from("$authority|$documentId")

internal fun titleFrom(displayName: String): String =
    displayName.substringBeforeLast('.', displayName).trim().ifBlank { "Untitled track" }
