package app.xpod.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.LocalMusicRepository
import app.xpod.data.LocalTrackEntity
import app.xpod.data.appendRelativePath
import app.xpod.playback.PlaybackController
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MusicFolder(
    val path: String,
    val name: String,
    val trackCount: Int,
)

data class MusicUiState(
    val tracks: List<LocalTrackEntity> = emptyList(),
    val visibleTracks: List<LocalTrackEntity> = emptyList(),
    val visibleFolders: List<MusicFolder> = emptyList(),
    val playbackTracks: List<LocalTrackEntity> = emptyList(),
    val currentFolderPath: String = "",
    val selectedTreeUri: String? = null,
    val query: String = "",
    val isScanning: Boolean = false,
)

internal data class MusicFolderContents(
    val currentFolderPath: String,
    val folders: List<MusicFolder>,
    val directTracks: List<LocalTrackEntity>,
    val playbackTracks: List<LocalTrackEntity>,
)

@HiltViewModel
class MusicViewModel
@Inject
constructor(
    private val localMusic: LocalMusicRepository,
    private val player: PlaybackController,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val musicQuery = MutableStateFlow("")
  private val musicFolderPath = MutableStateFlow("")
  private val musicScanning = MutableStateFlow(false)
  private var musicScanJob: Job? = null
  private val _status = MutableStateFlow<UiStatus?>(null)
  val status: StateFlow<UiStatus?> = _status

  val musicState: StateFlow<MusicUiState> =
      combine(localMusic.tracks, localMusic.treeUri, musicQuery, musicScanning, musicFolderPath) {
              tracks,
              treeUri,
              query,
              scanning,
              requestedFolderPath ->
            val normalizedQuery = query.trim()
            val folderContents = musicFolderContents(tracks, requestedFolderPath)
            val searchTracks =
                if (normalizedQuery.isBlank()) {
                  emptyList()
                } else {
                  tracks.filter {
                    it.title.contains(normalizedQuery, ignoreCase = true) ||
                        it.artist.contains(normalizedQuery, ignoreCase = true) ||
                        it.album.contains(normalizedQuery, ignoreCase = true)
                  }
                }
            MusicUiState(
                tracks = tracks,
                visibleTracks =
                    if (normalizedQuery.isBlank()) folderContents.directTracks else searchTracks,
                visibleFolders =
                    if (normalizedQuery.isBlank()) folderContents.folders else emptyList(),
                playbackTracks =
                    if (normalizedQuery.isBlank()) folderContents.playbackTracks else searchTracks,
                currentFolderPath = folderContents.currentFolderPath,
                selectedTreeUri = treeUri,
                query = query,
                isScanning = scanning,
            )
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5_000),
              MusicUiState(),
          )

  fun dismissStatus() {
    _status.value = null
  }

  fun selectMusicFolder(uri: Uri) {
    if (musicScanJob?.isActive == true) return
    musicScanJob = viewModelScope.launch {
      scanSelectedMusicFolder(uri)
    }
  }

  private suspend fun scanSelectedMusicFolder(uri: Uri) {
    musicScanning.value = true
    try {
      runCatchingCancellable { localMusic.selectTree(uri) }
          .fold(
              { count ->
                musicFolderPath.value = ""
                musicQuery.value = ""
                runCatchingCancellable {
                      player.removeMissingLocalTracks(localMusic.trackIds())
                    }
                    .onFailure { Log.w("XPOD", "Unable to clean the local music queue", it) }
                _status.value =
                    UiStatus(
                        context.resources.getQuantityString(
                            R.plurals.local_tracks_scanned,
                            count,
                            count,
                        )
                    )
              },
              {
                Log.w("XPOD", "Unable to scan the selected music folder", it)
                _status.value =
                    UiStatus(
                        context.getString(R.string.local_music_scan_failed),
                        StatusSeverity.Error,
                    )
              },
          )
    } finally {
      musicScanning.value = false
      musicScanJob = null
    }
  }

  fun refreshLocalMusic() {
    if (musicScanJob?.isActive == true) return
    musicScanJob = viewModelScope.launch {
      refreshSelectedMusicFolder()
    }
  }

  private suspend fun refreshSelectedMusicFolder() {
    musicScanning.value = true
    try {
      runCatchingCancellable { localMusic.refresh() }
          .fold(
              { count ->
                runCatchingCancellable {
                      player.removeMissingLocalTracks(localMusic.trackIds())
                    }
                    .onFailure { Log.w("XPOD", "Unable to clean the local music queue", it) }
                _status.value =
                    UiStatus(
                        context.resources.getQuantityString(
                            R.plurals.local_tracks_scanned,
                            count,
                            count,
                        )
                    )
              },
              {
                Log.w("XPOD", "Unable to refresh local music", it)
                _status.value =
                    UiStatus(
                        context.getString(R.string.local_music_scan_failed),
                        StatusSeverity.Error,
                    )
              },
          )
    } finally {
      musicScanning.value = false
      musicScanJob = null
    }
  }

  fun cancelLocalMusicScan() {
    if (musicScanJob?.isActive != true) return
    musicScanJob?.cancel()
    _status.value = UiStatus(context.getString(R.string.local_music_scan_cancelled))
  }

  fun setMusicQuery(query: String) {
    musicQuery.value = query
  }

  fun openMusicFolder(path: String) {
    musicFolderPath.value = normalizeMusicPath(path)
    musicQuery.value = ""
  }

  fun playMusic(tracks: List<LocalTrackEntity>, startTrackId: String) = viewModelScope.launch {
    runCatchingCancellable { player.playMusic(tracks, startTrackId) }
        .onFailure {
          _status.value =
              UiStatus(context.getString(R.string.could_not_start_playback), StatusSeverity.Error)
        }
  }

  fun playMusicNext(track: LocalTrackEntity) = viewModelScope.launch {
    runCatchingCancellable { player.playNext(track) }
        .onSuccess { _status.value = UiStatus(context.getString(R.string.added_next)) }
        .onFailure {
          _status.value =
              UiStatus(context.getString(R.string.could_not_update_queue), StatusSeverity.Error)
        }
  }

  fun addMusicToQueue(track: LocalTrackEntity) = viewModelScope.launch {
    runCatchingCancellable { player.addToQueue(track) }
        .onSuccess { _status.value = UiStatus(context.getString(R.string.added_to_queue)) }
        .onFailure {
          _status.value =
              UiStatus(context.getString(R.string.could_not_update_queue), StatusSeverity.Error)
        }
  }
}

internal fun musicFolderContents(
    tracks: List<LocalTrackEntity>,
    requestedFolderPath: String,
): MusicFolderContents {
  val normalizedTracks = tracks.map { it to normalizeMusicPath(it.relativePath) }
  val requestedPath = normalizeMusicPath(requestedFolderPath)
  val requestedPrefix = if (requestedPath.isBlank()) "" else "$requestedPath/"
  val requestedPathExists =
      requestedPath.isBlank() ||
          normalizedTracks.any { (_, path) ->
            path == requestedPath || path.startsWith(requestedPrefix)
          }
  val currentPath = if (requestedPathExists) requestedPath else ""

  val prefix = if (currentPath.isBlank()) "" else "$currentPath/"
  val directTracks =
      normalizedTracks.filter { (_, path) -> path == currentPath }.map { (track, _) -> track }
  val playbackTracks =
      normalizedTracks
          .filter { (_, path) ->
            currentPath.isBlank() || path == currentPath || path.startsWith(prefix)
          }
          .map { (track, _) -> track }

  val childPaths = linkedSetOf<String>()
  normalizedTracks.forEach { (_, path) ->
    if (path == currentPath) return@forEach
    if (currentPath.isNotBlank() && !path.startsWith(prefix)) return@forEach
    val remainder = if (currentPath.isBlank()) path else path.removePrefix(prefix)
    if (remainder.isBlank()) return@forEach
    val childName = remainder.substringBefore('/')
    childPaths += appendRelativePath(currentPath, childName)
  }

  val folders =
      childPaths
          .sortedBy { it.substringAfterLast('/').lowercase(Locale.ROOT) }
          .map { path ->
            val folderPrefix = "$path/"
            MusicFolder(
                path = path,
                name = path.substringAfterLast('/'),
                trackCount =
                    normalizedTracks.count { (_, trackPath) ->
                      trackPath == path || trackPath.startsWith(folderPrefix)
                    },
            )
          }

  return MusicFolderContents(
      currentFolderPath = currentPath,
      folders = folders,
      directTracks = directTracks,
      playbackTracks = playbackTracks,
  )
}

internal fun normalizeMusicPath(path: String): String =
    path.trim('/').split('/').filter(String::isNotBlank).joinToString("/")
