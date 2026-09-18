package app.xpod.ui.player

import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun buildPictureInPictureParams(autoEnter: Boolean, width: Int, height: Int) =
    PictureInPictureParams.Builder()
        .setAspectRatio(safePictureInPictureRatio(width, height))
        .setAutoEnterEnabled(autoEnter)
        .build()

private fun safePictureInPictureRatio(width: Int, height: Int): Rational {
  val safeWidth = width.takeIf { it > 0 } ?: 16
  val safeHeight = height.takeIf { it > 0 } ?: 9
  val ratio = safeWidth.toFloat() / safeHeight.toFloat()
  return if (ratio in 0.42f..2.39f) Rational(safeWidth, safeHeight) else Rational(16, 9)
}
