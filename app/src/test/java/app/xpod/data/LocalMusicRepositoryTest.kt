package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicRepositoryTest {
  @Test
  fun audioDocumentsAreRecognizedByMimeTypeOrExtension() {
    assertTrue(isSupportedAudioDocument("audio/mpeg", "track.bin"))
    assertTrue(isSupportedAudioDocument("application/octet-stream", "track.FLAC"))
    assertFalse(isSupportedAudioDocument("application/pdf", "notes.pdf"))
  }

  @Test
  fun localTrackIdsAreStableAndProviderScoped() {
    assertEquals(
        localTrackId("com.android.externalstorage.documents", "primary:Music/track.mp3"),
        localTrackId("com.android.externalstorage.documents", "primary:Music/track.mp3"),
    )
    assertNotEquals(
        localTrackId("provider-a", "track"),
        localTrackId("provider-b", "track"),
    )
  }

  @Test
  fun displayNameProvidesFallbackTitle() {
    assertEquals("A song", titleFrom("A song.mp3"))
    assertEquals("archive.tar", titleFrom("archive.tar.ogg"))
    assertEquals("Untitled track", titleFrom(".mp3"))
  }
}
