package app.xpod.ui

import app.xpod.data.LOCAL_MUSIC_MEDIA_SOURCE
import app.xpod.data.LocalTrackEntity
import app.xpod.ui.music.MusicFolder
import app.xpod.ui.music.musicFolderContents
import app.xpod.ui.music.shouldStartAutomaticMusicScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFolderContentsTest {
  @Test
  fun emptyLibraryShowsAnEmptyRoot() {
    val contents = musicFolderContents(emptyList(), "")

    assertEquals("", contents.currentFolderPath)
    assertEquals(emptyList<MusicFolder>(), contents.folders)
    assertEquals(emptyList<LocalTrackEntity>(), contents.directTracks)
    assertEquals(emptyList<LocalTrackEntity>(), contents.playbackTracks)
  }

  @Test
  fun rootShowsFoldersAndOnlyDirectTracksButPlaysRecursively() {
    val rootTrack = track("root", "Root", "")
    val jazzTrack = track("jazz", "Jazz", "Jazz")
    val liveTrack = track("live", "Live", "Jazz/Live")

    val contents = musicFolderContents(listOf(rootTrack, jazzTrack, liveTrack), "")

    assertEquals(listOf("Jazz"), contents.folders.map(MusicFolder::path))
    assertEquals(listOf("root"), contents.directTracks.map(LocalTrackEntity::id))
    assertEquals(listOf("root", "jazz", "live"), contents.playbackTracks.map(LocalTrackEntity::id))
  }

  @Test
  fun nestedFolderShowsDirectTracksAndItsChildFolders() {
    val jazzTrack = track("jazz", "Jazz", "Jazz")
    val liveTrack = track("live", "Live", "Jazz/Live")
    val studioTrack = track("studio", "Studio", "Jazz/Studio")

    val contents = musicFolderContents(listOf(jazzTrack, liveTrack, studioTrack), "Jazz")

    assertEquals(listOf("Jazz/Live", "Jazz/Studio"), contents.folders.map(MusicFolder::path))
    assertEquals(listOf("jazz"), contents.directTracks.map(LocalTrackEntity::id))
    assertEquals(
        listOf("jazz", "live", "studio"),
        contents.playbackTracks.map(LocalTrackEntity::id),
    )
  }

  @Test
  fun missingFolderFallsBackToRoot() {
    val track = track("track", "Track", "Jazz")

    val contents = musicFolderContents(listOf(track), "Missing")

    assertEquals("", contents.currentFolderPath)
    assertEquals(listOf("Jazz"), contents.folders.map(MusicFolder::path))
  }

  @Test
  fun folderPathsAreNormalizedBeforeLookup() {
    val track = track("live", "Live", "Jazz//Live/")

    val contents = musicFolderContents(listOf(track), "Jazz/")

    assertEquals("Jazz", contents.currentFolderPath)
    assertEquals(listOf("Jazz/Live"), contents.folders.map(MusicFolder::path))
    assertEquals(listOf("live"), contents.playbackTracks.map(LocalTrackEntity::id))
  }

  @Test
  fun automaticScanUsesGlobalSourceWhenThereIsNoIndex() {
    assertTrue(shouldStartAutomaticMusicScan(source = null))
    assertFalse(shouldStartAutomaticMusicScan(LOCAL_MUSIC_MEDIA_SOURCE))
    assertFalse(shouldStartAutomaticMusicScan("content://tree"))
  }

  private fun track(id: String, title: String, relativePath: String) =
      LocalTrackEntity(
          id = id,
          documentUri = "content://provider/document/$id",
          treeUri = "content://provider/tree/music",
          title = title,
          artist = "Artist",
          album = "Album",
          durationMs = 1_000L,
          modifiedEpochMs = 2L,
          relativePath = relativePath,
      )
}
