package app.xpod.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.data.ThemeMode
import app.xpod.ui.settings.SettingsViewModel
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
