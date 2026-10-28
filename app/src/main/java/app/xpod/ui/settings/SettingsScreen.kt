package app.xpod.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.xpod.BuildConfig
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ReadingPreferences
import app.xpod.data.ReadingTheme
import app.xpod.data.ThemeMode
import app.xpod.ui.coordination.CloudMemosUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    theme: ThemeMode,
    dynamicColor: Boolean,
    wifiOnlyDownloads: Boolean,
    showVideoPlayerClock: Boolean = false,
    showVideoPlayerBattery: Boolean = false,
    allowVideoBackgroundPlayback: Boolean = false,
    readingPreferences: ReadingPreferences,
    cloudMemos: CloudMemosUiState,
    setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
    setWifiOnlyDownloads: (Boolean) -> Unit,
    setShowVideoPlayerClock: (Boolean) -> Unit = {},
    setShowVideoPlayerBattery: (Boolean) -> Unit = {},
    setAllowVideoBackgroundPlayback: (Boolean) -> Unit = {},
    setReadingFontSize: (Float) -> Unit,
    setReadingLineHeight: (Float) -> Unit,
    setReadingTheme: (ReadingTheme) -> Unit,
    showQueue: () -> Unit,
    add: (String, (Boolean) -> Unit) -> Unit,
    importOpml: (Uri) -> Unit,
    exportOpml: (Uri) -> Unit,
    exportNotesZip: (Uri) -> Unit,
    configureCloudMemos: (String, String, () -> Unit) -> Unit,
    disconnectCloudMemos: () -> Unit,
    openReleases: () -> Unit,
    tabOrder: List<AppTab>,
    enabledTabs: Set<AppTab>,
    moveTab: (AppTab, Int) -> Unit,
    setTabEnabled: (AppTab, Boolean) -> Unit,
) {
  var showTabOrder by rememberSaveable { mutableStateOf(false) }
  var showAddSubscription by rememberSaveable { mutableStateOf(false) }
  var showCloudMemos by rememberSaveable { mutableStateOf(false) }
  var draftFontSizeSp by
      remember(readingPreferences.fontSizeSp) {
        mutableFloatStateOf(readingPreferences.fontSizeSp)
      }
  var draftLineHeight by
      remember(readingPreferences.lineHeightMultiplier) {
        mutableFloatStateOf(readingPreferences.lineHeightMultiplier)
      }
  val importer =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(importOpml)
      }
  val exporter =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/x-opml")
      ) {
        it?.let(exportOpml)
      }
  val notesZipExporter =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let(exportNotesZip)
      }

  LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("settings_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) }
    item {
      SettingsCard(stringResource(R.string.appearance), Icons.Filled.Palette) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ThemeMode.entries.forEach { option ->
            val label = themeModeShortLabel(option)
            val accessibleLabel = themeModeLabel(option)
            FilterChip(
                selected = theme == option,
                onClick = { setTheme(option) },
                modifier =
                    Modifier.weight(1f).semantics {
                      contentDescription = accessibleLabel
                    },
                label = {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                        themeModeIcon(option),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(label, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
                  }
                },
            )
          }
        }
        HorizontalDivider()
        SettingsToggleRow(
            title = stringResource(R.string.dynamic_color),
            summary = stringResource(R.string.dynamic_color_summary),
            checked = dynamicColor,
            onCheckedChange = setDynamicColor,
        )
      }
    }
    item {
      SettingsCard(stringResource(R.string.reading_settings), Icons.Filled.FormatSize) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val fontSizeInteraction = remember { MutableInteractionSource() }
          Text(
              stringResource(R.string.reading_font_size),
              modifier = Modifier.weight(0.8f),
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
          )
          Slider(
              modifier = Modifier.weight(1.5f),
              value = draftFontSizeSp,
              onValueChange = { draftFontSizeSp = it },
              onValueChangeFinished = { setReadingFontSize(draftFontSizeSp) },
              valueRange = 12f..32f,
              interactionSource = fontSizeInteraction,
              track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.graphicsLayer { scaleY = 0.55f },
                )
              },
              thumb = {
                SliderDefaults.Thumb(
                    interactionSource = fontSizeInteraction,
                    thumbSize = DpSize(16.dp, 16.dp),
                )
              },
          )
          Text(
              "${draftFontSizeSp.toInt()}sp",
              modifier = Modifier.weight(0.4f),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          val lineHeightInteraction = remember { MutableInteractionSource() }
          Text(
              stringResource(R.string.reading_line_height),
              modifier = Modifier.weight(0.8f),
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
          )
          Slider(
              modifier = Modifier.weight(1.5f),
              value = draftLineHeight,
              onValueChange = { draftLineHeight = it },
              onValueChangeFinished = { setReadingLineHeight(draftLineHeight) },
              valueRange = 1.1f..2.4f,
              interactionSource = lineHeightInteraction,
              track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.graphicsLayer { scaleY = 0.55f },
                )
              },
              thumb = {
                SliderDefaults.Thumb(
                    interactionSource = lineHeightInteraction,
                    thumbSize = DpSize(16.dp, 16.dp),
                )
              },
          )
          Text(
              "${String.format(LocalLocale.current.platformLocale, "%.1f", draftLineHeight)}×",
              modifier = Modifier.weight(0.4f),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
          )
        }
        Text(stringResource(R.string.reading_theme), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          ReadingTheme.entries.forEach { option ->
            val label = readingThemeLabel(option)
            FilterChip(
                selected = readingPreferences.theme == option,
                onClick = { setReadingTheme(option) },
                modifier = Modifier.semantics { contentDescription = label },
                label = {
                  Row(
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                        readingThemeIcon(option),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(readingThemeShortLabel(option), maxLines = 1)
                  }
                },
            )
          }
        }
      }
    }
    item {
      SettingsCard(stringResource(R.string.navigation), Icons.Filled.SwapVert) {
        SettingsActionRow(
            title = stringResource(R.string.manage_tab_order),
            icon = Icons.Filled.Edit,
            onClick = { showTabOrder = true },
        )
      }
    }
    item {
      SettingsCard(stringResource(R.string.downloads), Icons.Filled.Download) {
        SettingsToggleRow(
            title = stringResource(R.string.wifi_only_downloads),
            summary = stringResource(R.string.wifi_only_downloads_summary),
            checked = wifiOnlyDownloads,
            onCheckedChange = setWifiOnlyDownloads,
        )
        HorizontalDivider()
        SettingsActionRow(
            title = stringResource(R.string.queue),
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            onClick = showQueue,
        )
      }
    }
    item {
      SettingsCard(
          stringResource(R.string.video_settings),
          Icons.Filled.VideoLibrary,
          contentSpacing = 4.dp,
      ) {
        SettingsToggleRow(
            title = stringResource(R.string.video_player_clock),
            checked = showVideoPlayerClock,
            onCheckedChange = setShowVideoPlayerClock,
            verticalPadding = 0.dp,
        )
        SettingsToggleRow(
            title = stringResource(R.string.video_player_battery),
            checked = showVideoPlayerBattery,
            onCheckedChange = setShowVideoPlayerBattery,
            verticalPadding = 0.dp,
        )
        SettingsToggleRow(
            title = stringResource(R.string.video_background_playback),
            checked = allowVideoBackgroundPlayback,
            onCheckedChange = setAllowVideoBackgroundPlayback,
            verticalPadding = 0.dp,
        )
      }
    }
    item {
      SettingsCard(stringResource(R.string.notes_backup), Icons.Filled.Description) {
        FilledTonalButton(onClick = { notesZipExporter.launch("xpod-notes.zip") }) {
          Text(stringResource(R.string.export_notes_zip))
        }
      }
    }
    item {
      SettingsCard(stringResource(R.string.subscriptions), Icons.Filled.AddLink) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilledTonalButton(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(horizontal = 8.dp),
              onClick = { showAddSubscription = true },
          ) {
            Icon(
                Icons.Filled.AddLink,
                contentDescription = stringResource(R.string.add_subscription),
            )
          }
          FilledTonalButton(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(horizontal = 8.dp),
              onClick = {
                importer.launch(
                    arrayOf("application/x-opml", "text/x-opml", "text/xml", "application/xml")
                )
              },
          ) {
            Icon(
                Icons.Filled.FileUpload,
                contentDescription = stringResource(R.string.import_opml),
            )
          }
          FilledTonalButton(
              modifier = Modifier.weight(1f),
              contentPadding = PaddingValues(horizontal = 8.dp),
              onClick = { exporter.launch("xpod-subscriptions.opml") },
          ) {
            Icon(
                Icons.Filled.FileDownload,
                contentDescription = stringResource(R.string.export_opml),
            )
          }
        }
      }
    }
    item {
      SettingsCard(stringResource(R.string.cloud_memos), Icons.Filled.Cloud) {
        if (cloudMemos.isConfigured) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                cloudMemos.baseUrl,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          Text(
              stringResource(R.string.cloud_memos_not_connected),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
          )
        }
        val connectionAction =
            stringResource(
                if (cloudMemos.isConfigured) R.string.manage_connection
                else R.string.cloud_memos_connect
            )
        IconButton(
            onClick = { showCloudMemos = true },
            modifier = Modifier.semantics { contentDescription = connectionAction },
        ) {
          Icon(Icons.Filled.Cloud, contentDescription = null)
        }
      }
    }
    item {
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = openReleases) {
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
          Text(
              stringResource(R.string.view_releases),
              modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
    }
  }

  SettingsDialogs(
      showTabOrder = showTabOrder,
      tabOrder = tabOrder,
      enabledTabs = enabledTabs,
      moveTab = moveTab,
      setTabEnabled = setTabEnabled,
      onDismissTabOrder = { showTabOrder = false },
      showAddSubscription = showAddSubscription,
      add = add,
      onDismissAddSubscription = { showAddSubscription = false },
      showCloudMemos = showCloudMemos,
      cloudMemos = cloudMemos,
      configureCloudMemos = configureCloudMemos,
      disconnectCloudMemos = disconnectCloudMemos,
      onDismissCloudMemos = { showCloudMemos = false },
  )
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    contentSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
      }
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    verticalPadding: Dp = 5.dp,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .semantics(mergeDescendants = true) {}
              .toggleable(
                  value = checked,
                  role = Role.Switch,
                  onValueChange = onCheckedChange,
              )
              .padding(vertical = verticalPadding),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      summary?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun SettingsActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(title, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
internal fun tabLabel(tab: AppTab): String =
    stringResource(
        when (tab) {
          AppTab.Podcasts -> R.string.podcasts
          AppTab.Reader -> R.string.reader
          AppTab.Music -> R.string.local_music
          AppTab.Video -> R.string.local_video
          AppTab.Memos -> R.string.memos
          AppTab.Notes -> R.string.notes
          AppTab.Books -> R.string.books
          AppTab.Settings -> R.string.settings
        }
    )

@Composable
private fun themeModeShortLabel(theme: ThemeMode): String =
    stringResource(
        when (theme) {
          ThemeMode.System -> R.string.theme_system_short
          ThemeMode.Light -> R.string.theme_light_short
          ThemeMode.Dark -> R.string.theme_dark_short
        }
    )

@Composable
private fun themeModeLabel(theme: ThemeMode): String =
    stringResource(
        when (theme) {
          ThemeMode.System -> R.string.theme_system
          ThemeMode.Light -> R.string.theme_light
          ThemeMode.Dark -> R.string.theme_dark
        }
    )

@Composable
private fun readingThemeLabel(theme: ReadingTheme): String =
    stringResource(
        when (theme) {
          ReadingTheme.FollowApp -> R.string.reading_theme_follow_app
          ReadingTheme.Light -> R.string.reading_theme_light
          ReadingTheme.Sepia -> R.string.reading_theme_sepia
          ReadingTheme.Dark -> R.string.reading_theme_dark
        }
    )

@Composable
private fun readingThemeShortLabel(theme: ReadingTheme): String =
    stringResource(
        when (theme) {
          ReadingTheme.FollowApp -> R.string.reading_theme_follow_app_short
          ReadingTheme.Light -> R.string.reading_theme_light_short
          ReadingTheme.Sepia -> R.string.reading_theme_sepia_short
          ReadingTheme.Dark -> R.string.reading_theme_dark_short
        }
    )

private fun readingThemeIcon(theme: ReadingTheme): ImageVector =
    when (theme) {
      ReadingTheme.FollowApp -> Icons.Filled.BrightnessAuto
      ReadingTheme.Light -> Icons.Filled.LightMode
      ReadingTheme.Sepia -> Icons.Filled.AutoStories
      ReadingTheme.Dark -> Icons.Filled.DarkMode
    }

private fun themeModeIcon(theme: ThemeMode): ImageVector =
    when (theme) {
      ThemeMode.System -> Icons.Filled.BrightnessAuto
      ThemeMode.Light -> Icons.Filled.LightMode
      ThemeMode.Dark -> Icons.Filled.DarkMode
    }
