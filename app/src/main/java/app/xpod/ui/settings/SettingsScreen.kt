package app.xpod.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.xpod.BuildConfig
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ReadingPreferences
import app.xpod.data.ReadingTheme
import app.xpod.data.ThemeMode
import app.xpod.ui.coordination.CloudMemosUiState

@Composable
internal fun SettingsScreen(
    theme: ThemeMode,
    dynamicColor: Boolean,
    wifiOnlyDownloads: Boolean,
    readingPreferences: ReadingPreferences,
    cloudMemos: CloudMemosUiState,
    setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
    setWifiOnlyDownloads: (Boolean) -> Unit,
    setReadingFontSize: (Float) -> Unit,
    setReadingLineHeight: (Float) -> Unit,
    setReadingTheme: (ReadingTheme) -> Unit,
    showQueue: () -> Unit,
    add: (String, (Boolean) -> Unit) -> Unit,
    importOpml: (Uri) -> Unit,
    exportOpml: (Uri) -> Unit,
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

  LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("settings_list"),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) }
    item {
      SettingsCard(stringResource(R.string.appearance), Icons.Filled.Palette) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { option ->
            FilterChip(
                selected = theme == option,
                onClick = { setTheme(option) },
                label = { Text(themeLabel(option)) },
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
        Text(
            stringResource(R.string.reading_font_size),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = draftFontSizeSp,
            onValueChange = { draftFontSizeSp = it },
            onValueChangeFinished = { setReadingFontSize(draftFontSizeSp) },
            valueRange = 12f..32f,
        )
        Text(
            stringResource(R.string.reading_line_height),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = draftLineHeight,
            onValueChange = { draftLineHeight = it },
            onValueChangeFinished = { setReadingLineHeight(draftLineHeight) },
            valueRange = 1.1f..2.4f,
        )
        Text(stringResource(R.string.reading_theme), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ReadingTheme.entries.forEach { option ->
            FilterChip(
                selected = readingPreferences.theme == option,
                onClick = { setReadingTheme(option) },
                label = { Text(readingThemeLabel(option)) },
            )
          }
        }
      }
    }
    item {
      SettingsCard(stringResource(R.string.navigation), Icons.Filled.SwapVert) {
        Text(
            stringResource(R.string.tab_order_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            tabOrderSummary(tabOrder.filter(enabledTabs::contains)),
            style = MaterialTheme.typography.titleSmall,
        )
        FilledTonalButton(onClick = { showTabOrder = true }) {
          Text(stringResource(R.string.manage_tab_order))
        }
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
      SettingsCard(stringResource(R.string.subscriptions), Icons.Filled.AddLink) {
        SettingsActionRow(
            title = stringResource(R.string.add_subscription),
            summary = stringResource(R.string.add_subscription_summary),
            icon = Icons.Filled.AddLink,
            onClick = { showAddSubscription = true },
        )
        HorizontalDivider()
        SettingsActionRow(
            title = stringResource(R.string.import_opml),
            icon = Icons.Filled.FileUpload,
            onClick = {
              importer.launch(
                  arrayOf("application/x-opml", "text/x-opml", "text/xml", "application/xml")
              )
            },
        )
        SettingsActionRow(
            title = stringResource(R.string.export_opml),
            icon = Icons.Filled.FileDownload,
            onClick = { exporter.launch("xpod-subscriptions.opml") },
        )
      }
    }
    item {
      SettingsCard(stringResource(R.string.cloud_memos), Icons.Filled.Cloud) {
        Text(
            stringResource(R.string.cloud_memos_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        FilledTonalButton(onClick = { showCloudMemos = true }) {
          Text(
              stringResource(
                  if (cloudMemos.isConfigured) R.string.manage_connection
                  else R.string.cloud_memos_connect
              )
          )
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
    content: @Composable ColumnScope.() -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
              .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(
          summary,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun SettingsActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    summary: String? = null,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
          AppTab.Books -> R.string.books
          AppTab.Settings -> R.string.settings
        }
    )

@Composable
private fun tabOrderSummary(tabs: List<AppTab>): String {
  val labels = mutableListOf<String>()
  for (tab in tabs) labels += tabLabel(tab)
  return labels.joinToString("  ·  ")
}

@Composable
private fun themeLabel(theme: ThemeMode): String =
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
