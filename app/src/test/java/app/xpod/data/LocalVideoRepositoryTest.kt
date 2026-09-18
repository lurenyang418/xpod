package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalVideoRepositoryTest {
  @Test
  fun videoDocumentsAreRecognizedByMimeTypeOrExtension() {
    assertTrue(isSupportedVideoDocument("video/mp4", "clip.bin"))
    assertTrue(isSupportedVideoDocument("application/octet-stream", "clip.MKV"))
    assertFalse(isSupportedVideoDocument("audio/mpeg", "song.mp3"))
  }

  @Test
  fun localVideoIdsAreStableAndProviderScoped() {
    assertEquals(
        localVideoId("com.android.externalstorage.documents", "primary:Movies/clip.mp4"),
        localVideoId("com.android.externalstorage.documents", "primary:Movies/clip.mp4"),
    )
    assertNotEquals(localVideoId("provider-a", "clip"), localVideoId("provider-b", "clip"))
  }

  @Test
  fun mediaStoreVideoIdsAreStableAndVolumeScoped() {
    assertEquals(
        mediaStoreVideoId("external", 42L),
        mediaStoreVideoId("external", 42L),
    )
    assertNotEquals(mediaStoreVideoId("external", 42L), mediaStoreVideoId("sd", 42L))
  }

  @Test
  fun globalVideoSourceRecognizesCurrentAndLegacyMarkers() {
    assertTrue(isGlobalVideoSource(LOCAL_VIDEO_MEDIA_SOURCE))
    assertTrue(isGlobalVideoSource(LOCAL_VIDEO_ALL_FILES_SOURCE))
    assertFalse(isGlobalVideoSource("content://tree/videos"))
  }

  @Test
  fun displayNameProvidesFallbackVideoTitle() {
    assertEquals("A clip", videoTitleFrom("A clip.mp4"))
    assertEquals("archive.tar", videoTitleFrom("archive.tar.mkv"))
    assertEquals("Untitled video", videoTitleFrom(".mp4"))
  }

  @Test
  fun mergeLocalVideosPreservesProgressWhenTheSourceIsUnchanged() {
    val current = video(modifiedEpochMs = 20L)
    val existing = current.copy(lastPositionMs = 12_000L, lastOpenedEpochMs = 34L)

    val merged = mergeLocalVideos(listOf(current), mapOf(current.id to existing)).single()

    assertEquals(12_000L, merged.lastPositionMs)
    assertEquals(34L, merged.lastOpenedEpochMs)
  }

  @Test
  fun mergeLocalVideosClearsProgressWhenTheSourceChanges() {
    val existing =
        video(modifiedEpochMs = 20L).copy(lastPositionMs = 12_000L, lastOpenedEpochMs = 34L)
    val current = existing.copy(modifiedEpochMs = 21L)

    val merged = mergeLocalVideos(listOf(current), mapOf(existing.id to existing)).single()

    assertEquals(0L, merged.lastPositionMs)
    assertEquals(0L, merged.lastOpenedEpochMs)
  }

  @Test
  fun mergeLocalVideosStartsNewVideosWithoutResumeProgress() {
    val merged = mergeLocalVideos(listOf(video(modifiedEpochMs = 20L)), emptyMap()).single()

    assertEquals(0L, merged.lastPositionMs)
    assertEquals(0L, merged.lastOpenedEpochMs)
  }

  @Test
  fun nullSafCursorFailsFast() {
    assertThrows(IllegalStateException::class.java) {
      requireVideoChildrenCursor(null, "content://children")
    }
  }

  private fun video(modifiedEpochMs: Long) =
      LocalVideoEntity(
          id = "local-video",
          documentUri = "content://video/local-video",
          treeUri = "content://tree/videos",
          title = "Local video",
          durationMs = 60_000L,
          width = 1_920,
          height = 1_080,
          fileSizeBytes = 42L,
          modifiedEpochMs = modifiedEpochMs,
          relativePath = "Movies",
      )
}
