package app.xpod.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.R
import app.xpod.data.LocalMusicRepository
import app.xpod.data.LocalTrackEntity
import app.xpod.playback.PlaybackController
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MusicUiState(
    val tracks: List<LocalTrackEntity> = emptyList(),
    val visibleTracks: List<LocalTrackEntity> = emptyList(),
    val selectedTreeUri: String? = null,
    val query: String = "",
    val isScanning: Boolean = false,
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
  private val musicScanning = MutableStateFlow(false)
  private var musicScanJob: Job? = null
  private val _status = MutableStateFlow<String?>(null)
  val status: StateFlow<String?> = _status

  val musicState: StateFlow<MusicUiState> =
      combine(localMusic.tracks, localMusic.treeUri, musicQuery, musicScanning) {
              tracks,
              treeUri,
              query,
              scanning ->
            val normalizedQuery = query.trim()
            MusicUiState(
                tracks = tracks,
                visibleTracks =
                    if (normalizedQuery.isBlank()) tracks
                    else
                        tracks.filter {
                          it.title.contains(normalizedQuery, ignoreCase = true) ||
                              it.artist.contains(normalizedQuery, ignoreCase = true) ||
                              it.album.contains(normalizedQuery, ignoreCase = true)
                        },
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
                runCatchingCancellable {
                      player.removeMissingLocalTracks(localMusic.trackIds())
                    }
                    .onFailure { Log.w("XPOD", "Unable to clean the local music queue", it) }
                _status.value =
                    context.resources.getQuantityString(
                        R.plurals.local_tracks_scanned,
                        count,
                        count,
                    )
              },
              {
                Log.w("XPOD", "Unable to scan the selected music folder", it)
                _status.value = context.getString(R.string.local_music_scan_failed)
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
                    context.resources.getQuantityString(
                        R.plurals.local_tracks_scanned,
                        count,
                        count,
                    )
              },
              {
                Log.w("XPOD", "Unable to refresh local music", it)
                _status.value = context.getString(R.string.local_music_scan_failed)
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
    _status.value = context.getString(R.string.local_music_scan_cancelled)
  }

  fun setMusicQuery(query: String) {
    musicQuery.value = query
  }

  fun playMusic(tracks: List<LocalTrackEntity>, startTrackId: String) = viewModelScope.launch {
    runCatchingCancellable { player.playMusic(tracks, startTrackId) }
        .onFailure { _status.value = context.getString(R.string.could_not_start_playback) }
  }

  fun playMusicNext(track: LocalTrackEntity) = viewModelScope.launch {
    runCatchingCancellable { player.playNext(track) }
        .onSuccess { _status.value = context.getString(R.string.added_next) }
        .onFailure { _status.value = context.getString(R.string.could_not_update_queue) }
  }

  fun addMusicToQueue(track: LocalTrackEntity) = viewModelScope.launch {
    runCatchingCancellable { player.addToQueue(track) }
        .onSuccess { _status.value = context.getString(R.string.added_to_queue) }
        .onFailure { _status.value = context.getString(R.string.could_not_update_queue) }
  }
}
