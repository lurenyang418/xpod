package app.xpod.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.CancellationSignal
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

  fun hasAudioPermission(): Boolean =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
          PackageManager.PERMISSION_GRANTED

  suspend fun sourceValue(): String? = settings.localMusicTreeUriValue()

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
        val source = settings.localMusicTreeUriValue() ?: error("No local music source selected")
        val tracks =
            if (source == LOCAL_MUSIC_MEDIA_SOURCE) {
              check(hasAudioPermission()) { "Audio permission is not granted" }
              scanMediaStore()
            } else {
              scan(source.toUri())
            }
        replaceTracks(tracks)
        tracks.size
      }

  suspend fun enableGlobalScan(): Int =
      withContext(Dispatchers.IO) {
        check(hasAudioPermission()) { "Audio permission is not granted" }
        val previousSource = settings.localMusicTreeUriValue()
        val tracks = scanMediaStore()
        try {
          settings.setLocalMusicTreeUri(LOCAL_MUSIC_MEDIA_SOURCE)
          replaceTracks(tracks)
        } catch (error: Throwable) {
          try {
            withContext(NonCancellable) { settings.setLocalMusicTreeUri(previousSource) }
          } catch (rollbackError: Throwable) {
            error.addSuppressed(rollbackError)
          }
          throw error
        }
        if (previousSource != null && previousSource != LOCAL_MUSIC_MEDIA_SOURCE) {
          releaseTreePermission(previousSource.toUri())
        }
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

  private suspend fun scanMediaStore(): List<LocalTrackEntity> =
      withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cursor =
            context.contentResolver.query(
                contentUri,
                MEDIA_STORE_PROJECTION,
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            ) ?: error("Unable to query MediaStore audio")
        cursor
            .use { resultCursor ->
              val idIndex = resultCursor.getColumnIndex(MediaStore.Audio.Media._ID)
              val displayNameIndex =
                  resultCursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
              val titleIndex = resultCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
              val artistIndex = resultCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
              val albumIndex = resultCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
              val durationIndex = resultCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
              val modifiedIndex = resultCursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
              val relativePathIndex =
                  resultCursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
              val tracks = mutableListOf<LocalTrackEntity>()
              while (resultCursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (idIndex < 0 || resultCursor.isNull(idIndex)) continue
                val mediaId = resultCursor.getLong(idIndex)
                val displayName = resultCursor.stringOrEmpty(displayNameIndex)
                val mediaUri = ContentUris.withAppendedId(contentUri, mediaId)
                tracks +=
                    LocalTrackEntity(
                        id = mediaStoreTrackId(MediaStore.VOLUME_EXTERNAL, mediaId),
                        documentUri = mediaUri.toString(),
                        treeUri = LOCAL_MUSIC_MEDIA_SOURCE,
                        title =
                            resultCursor.stringOrEmpty(titleIndex).trim().takeUnless {
                              it.isBlank()
                            } ?: titleFrom(displayName),
                        artist = resultCursor.stringOrEmpty(artistIndex).trim(),
                        album = resultCursor.stringOrEmpty(albumIndex).trim(),
                        durationMs = resultCursor.longOrZero(durationIndex),
                        modifiedEpochMs = resultCursor.longOrZero(modifiedIndex) * 1_000L,
                        relativePath =
                            resultCursor.stringOrEmpty(relativePathIndex).trim('/').trim(),
                    )
              }
              tracks.distinctBy(LocalTrackEntity::id)
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
      }

  private data class PendingDirectory(val documentId: String, val relativePath: String)

  private suspend fun scan(treeUri: Uri): List<LocalTrackEntity> {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val visited = mutableSetOf<String>()
    val tracks = mutableListOf<LocalTrackEntity>()
    val pendingDirectories =
        ArrayDeque<PendingDirectory>().apply {
          addLast(PendingDirectory(documentId = rootId, relativePath = ""))
        }
    while (pendingDirectories.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val directory = pendingDirectories.removeLast()
      if (!visited.add(directory.documentId)) continue
      scanChildren(treeUri, directory, pendingDirectories, tracks)
    }
    return tracks.distinctBy(LocalTrackEntity::id).sortedBy { it.title.lowercase() }
  }

  private suspend fun scanChildren(
      treeUri: Uri,
      parent: PendingDirectory,
      pendingDirectories: ArrayDeque<PendingDirectory>,
      tracks: MutableList<LocalTrackEntity>,
  ) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
    queryChildren(childrenUri).use { cursor ->
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
          pendingDirectories.addLast(
              PendingDirectory(
                  documentId = documentId,
                  relativePath = appendRelativePath(parent.relativePath, name),
              )
          )
        } else if (isSupportedAudioDocument(mimeType, name)) {
          val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
          tracks +=
              readTrack(
                  treeUri = treeUri,
                  documentUri = documentUri,
                  documentId = documentId,
                  displayName = name,
                  modifiedEpochMs = modifiedEpochMs,
                  relativePath = parent.relativePath,
              )
        }
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
          continuation.resume(requireMusicChildrenCursor(cursor, childrenUri.toString())) {
              _,
              rejectedCursor,
              _ ->
            rejectedCursor.close()
          }
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
      relativePath: String,
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
        )
    val MEDIA_STORE_PROJECTION =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
  }
}

internal fun requireMusicChildrenCursor(cursor: Cursor?, childrenUri: String): Cursor =
    cursor ?: error("Unable to query SAF music children: $childrenUri")

private fun Cursor.stringOrEmpty(index: Int): String =
    if (index < 0 || isNull(index)) "" else getString(index).orEmpty()

private fun Cursor.longOrZero(index: Int): Long =
    if (index < 0 || isNull(index)) 0L else getLong(index).coerceAtLeast(0L)

internal fun isSupportedAudioDocument(mimeType: String, displayName: String): Boolean {
  if (mimeType.startsWith("audio/", ignoreCase = true)) return true
  return displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) in
      setOf("aac", "amr", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav", "wma")
}

internal fun localTrackId(authority: String, documentId: String): String =
    LOCAL_TRACK_ID_PREFIX + FeedId.from("$authority|$documentId")

internal fun mediaStoreTrackId(volume: String, mediaId: Long): String =
    localTrackId("media.$volume", mediaId.toString())

internal fun titleFrom(displayName: String): String =
    displayName.substringBeforeLast('.', displayName).trim().ifBlank { "Untitled track" }

internal fun appendRelativePath(parent: String, child: String): String =
    listOf(parent.trim('/'), child.trim('/')).filter(String::isNotBlank).joinToString("/")

const val LOCAL_MUSIC_MEDIA_SOURCE = "media-store://shared-audio"
