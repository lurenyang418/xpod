package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPlaybackSettingsTest {
  @Test
  fun repeatModeCyclesFromOffToAllToOne() {
    assertEquals(MusicRepeatMode.All, MusicRepeatMode.Off.next())
    assertEquals(MusicRepeatMode.One, MusicRepeatMode.All.next())
    assertEquals(MusicRepeatMode.Off, MusicRepeatMode.One.next())
  }

  @Test
  fun storedRepeatModeFallsBackToOff() {
    assertEquals(MusicRepeatMode.All, parseMusicRepeatMode(MusicRepeatMode.All.name))
    assertEquals(MusicRepeatMode.Off, parseMusicRepeatMode(null))
    assertEquals(MusicRepeatMode.Off, parseMusicRepeatMode("unsupported"))
  }
}
