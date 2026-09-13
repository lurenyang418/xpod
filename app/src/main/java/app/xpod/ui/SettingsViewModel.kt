package app.xpod.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xpod.data.AppTab
import app.xpod.data.DownloadRepository
import app.xpod.data.ReadingPreferences
import app.xpod.data.ReadingPreferencesRepository
import app.xpod.data.ReadingTheme
import app.xpod.data.SettingsRepository
import app.xpod.data.ThemeMode
import app.xpod.data.defaultTabOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state and persistence actions shared by the Settings screen and app chrome. */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val readingPreferences: ReadingPreferencesRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
  val dynamicColor: StateFlow<Boolean> =
      settings.useDynamicColor.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          true,
      )

  val appTheme: StateFlow<ThemeMode> =
      settings.appTheme.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          ThemeMode.System,
      )

  val readerPreferences: StateFlow<ReadingPreferences> =
      readingPreferences.preferences.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          ReadingPreferences(),
      )

  val wifiOnlyDownloads: StateFlow<Boolean> =
      settings.useWifiOnlyDownloads.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5_000),
          true,
      )

  val tabOrder: StateFlow<List<AppTab>> =
      settings.tabOrder.stateIn(
          viewModelScope,
          SharingStarted.Eagerly,
          defaultTabOrder,
      )

  val enabledTabs: StateFlow<Set<AppTab>> =
      settings.enabledTabs.stateIn(
          viewModelScope,
          SharingStarted.Eagerly,
          defaultTabOrder.toSet(),
      )

  init {
    viewModelScope.launch {
      runCatching { settings.useWifiOnlyDownloads.first() }.getOrNull()?.let(downloads::setWifiOnly)
    }
  }

  fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
    settings.setDynamicColor(enabled)
  }

  fun setAppTheme(theme: ThemeMode) = viewModelScope.launch { settings.setAppTheme(theme) }

  fun setReadingFontSize(value: Float) = viewModelScope.launch {
    readingPreferences.setFontSizeSp(value)
  }

  fun setReadingLineHeight(value: Float) = viewModelScope.launch {
    readingPreferences.setLineHeightMultiplier(value)
  }

  fun setReadingTheme(value: ReadingTheme) = viewModelScope.launch {
    readingPreferences.setTheme(value)
  }

  fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch {
    settings.setWifiOnlyDownloads(enabled)
    downloads.setWifiOnly(enabled)
  }

  fun moveTab(tab: AppTab, offset: Int) = viewModelScope.launch {
    settings.moveTab(tab, offset)
  }

  fun setTabEnabled(tab: AppTab, enabled: Boolean) = viewModelScope.launch {
    settings.setTabEnabled(tab, enabled)
  }
}
