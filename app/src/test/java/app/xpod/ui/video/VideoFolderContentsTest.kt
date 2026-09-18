package app.xpod.ui.video

import app.xpod.data.LOCAL_VIDEO_ALL_FILES_SOURCE
import app.xpod.data.LOCAL_VIDEO_MEDIA_SOURCE
import app.xpod.data.LocalVideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFolderContentsTest {
  @Test
  fun rootShowsFoldersAndOnlyDirectVideosButPlaysRecursively() {
    val rootVideo = video("root", "Root", "")
    val moviesVideo = video("movies", "Movies", "Movies")
    val clipsVideo = video("clips", "Clips", "Movies/Clips")

    val contents = videoFolderContents(listOf(rootVideo, moviesVideo, clipsVideo), "")

    assertEquals(listOf("Movies"), contents.folders.map(VideoFolder::path))
    assertEquals(listOf("root"), contents.directVideos.map(LocalVideoEntity::id))
    assertEquals(
        listOf("root", "movies", "clips"),
        contents.playbackVideos.map(LocalVideoEntity::id),
    )
  }

  @Test
  fun missingFolderFallsBackToRoot() {
    val contents = videoFolderContents(listOf(video("clip", "Clip", "Movies")), "Missing")

    assertEquals("", contents.currentFolderPath)
    assertEquals(listOf("Movies"), contents.folders.map(VideoFolder::path))
  }

  @Test
  fun deepNestedPathsAggregateCounts() {
    val actionVideo = video("a", "A", "Movies/Action/2024")
    val actionFolderVideo = video("b", "B", "Movies/Action")
    val rootVideo = video("c", "C", "")
    val videos = listOf(actionFolderVideo, actionVideo, rootVideo)

    val rootContents = videoFolderContents(videos, "")

    assertEquals(listOf("Movies"), rootContents.folders.map(VideoFolder::path))
    assertEquals(listOf("c"), rootContents.directVideos.map(LocalVideoEntity::id))

    val actionContents = videoFolderContents(videos, "Movies/Action")

    assertEquals("Movies/Action", actionContents.currentFolderPath)
    assertEquals(listOf("b"), actionContents.directVideos.map(LocalVideoEntity::id))
    assertEquals(listOf("b", "a"), actionContents.playbackVideos.map(LocalVideoEntity::id))
  }

  @Test
  fun untouchedVideosAreMarkedUnplayed() {
    assertTrue(isVideoUnplayed(video("new", "New", "")))
    assertFalse(isVideoUnplayed(video("opened", "Opened", "").copy(lastOpenedEpochMs = 1L)))
  }

  @Test
  fun videoProgressResumesWhenDurationIsNotAvailableYet() {
    assertEquals(12_000L, videoResumePosition(12_000L, 0L))
    assertEquals(12_000L, videoResumePosition(12_000L, 60_000L))
    assertEquals(0L, videoResumePosition(58_000L, 60_000L))
    assertEquals(0L, videoResumePosition(59_999L, 60_000L))
  }

  @Test
  fun videoProgressOnlyPersistsForTheCurrentMediaItem() {
    assertTrue(shouldPersistVideoPosition("video-a", "video-a"))
    assertFalse(shouldPersistVideoPosition("video-a", "video-b"))
    assertFalse(shouldPersistVideoPosition("video-a", null))
  }

  @Test
  fun playbackQueueUsesFolderOrderAndStartsAtTheSelectedVideo() {
    val first = video("first", "First", "Movies")
    val second = video("second", "Second", "Movies")
    val third = video("third", "Third", "Movies")

    val queue =
        buildVideoPlaybackQueue(
            listOf(first, second, third),
            listOf(first, second, third),
            "second",
        )

    assertEquals(listOf("first", "second", "third"), queue.map(LocalVideoEntity::id))
    assertEquals(1, queue.indexOfFirst { it.id == "second" })
  }

  @Test
  fun automaticScanMigratesLegacyAndSkipsExistingMediaStoreIndex() {
    assertTrue(shouldStartAutomaticVideoScan(source = null, hasIndexedVideos = true))
    assertTrue(shouldStartAutomaticVideoScan(LOCAL_VIDEO_MEDIA_SOURCE, false))
    assertFalse(shouldStartAutomaticVideoScan(LOCAL_VIDEO_MEDIA_SOURCE, true))
    assertTrue(shouldStartAutomaticVideoScan(LOCAL_VIDEO_ALL_FILES_SOURCE, false))
    assertTrue(shouldStartAutomaticVideoScan(LOCAL_VIDEO_ALL_FILES_SOURCE, true))
    assertFalse(shouldStartAutomaticVideoScan("content://tree", false))
  }

  private fun video(id: String, title: String, relativePath: String) =
      LocalVideoEntity(
          id = id,
          documentUri = "content://provider/document/$id",
          treeUri = "content://provider/tree/videos",
          title = title,
          durationMs = 60_000L,
          width = 1_920,
          height = 1_080,
          fileSizeBytes = 2_000L,
          modifiedEpochMs = 2L,
          relativePath = relativePath,
      )
}
