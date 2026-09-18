package app.xpod.ui.shell

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
import app.xpod.ui.settings.SettingsViewModel
import app.xpod.ui.video.VideoViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun XpodApp(viewModel: MainViewModel = hiltViewModel()) {
  val settingsViewModel: SettingsViewModel = hiltViewModel()
  val dynamic by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
  val theme by settingsViewModel.appTheme.collectAsStateWithLifecycle()
  val readerPreferences by settingsViewModel.readerPreferences.collectAsStateWithLifecycle()
  val dark =
      when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
  val context = LocalContext.current
  val view = LocalView.current
  val activity = LocalActivity.current
  if (!view.isInEditMode) {
    SideEffect {
      activity?.window?.let { window ->
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
      }
    }
  }
  val notificationPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val requestNotificationPermission =
      remember(context) {
        val request: () -> Unit = {
          if (
              ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
          )
              notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        request
      }
  val scheme =
      when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
      }
  MaterialTheme(colorScheme = scheme) {
    XpodHome(
        viewModel,
        settingsViewModel,
        theme,
        dynamic,
        readerPreferences,
        requestNotificationPermission,
    )
  }
}
