package app.xpod.ui.player

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.EpisodeEntity
import app.xpod.data.LocalTrackEntity
import app.xpod.data.LocalVideoEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.data.cloudMemoWebUrl
import app.xpod.ui.video.VideoViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
