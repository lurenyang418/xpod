package app.xpod.playback

import androidx.media3.common.Player
import app.xpod.data.MusicRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPlaybackModeTest {
  @Test
  fun musicRepeatModesMapToMedia3Modes() {
    assertEquals(Player.REPEAT_MODE_OFF, MusicRepeatMode.Off.toPlayerRepeatMode())
    assertEquals(Player.REPEAT_MODE_ALL, MusicRepeatMode.All.toPlayerRepeatMode())
    assertEquals(Player.REPEAT_MODE_ONE, MusicRepeatMode.One.toPlayerRepeatMode())
    assertEquals(Player.REPEAT_MODE_OFF, null.toPlayerRepeatMode())
  }

  @Test
  fun media3RepeatModesMapBackToMusicModes() {
    assertEquals(MusicRepeatMode.Off, Player.REPEAT_MODE_OFF.toMusicRepeatMode())
    assertEquals(MusicRepeatMode.All, Player.REPEAT_MODE_ALL.toMusicRepeatMode())
    assertEquals(MusicRepeatMode.One, Player.REPEAT_MODE_ONE.toMusicRepeatMode())
    assertEquals(MusicRepeatMode.Off, Int.MIN_VALUE.toMusicRepeatMode())
  }
}
