package app.xpod.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.ThemeMode

@Composable
internal fun SettingsScreen(
    theme: ThemeMode,
    dynamicColor: Boolean,
    wifiOnlyDownloads: Boolean,
    setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
    setWifiOnlyDownloads: (Boolean) -> Unit,
    showQueue: () -> Unit,
    add: (String, () -> Unit) -> Unit,
    importOpml: (Uri) -> Unit,
    exportOpml: (Uri) -> Unit,
) =
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
      }
      item { SettingsSectionHeader(stringResource(R.string.appearance)) }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
      }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.dynamic_color),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.dynamic_color_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
          Switch(checked = dynamicColor, onCheckedChange = setDynamicColor)
        }
      }
      item { SettingsSectionHeader(stringResource(R.string.downloads)) }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.wifi_only_downloads),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.wifi_only_downloads_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
          Switch(checked = wifiOnlyDownloads, onCheckedChange = setWifiOnlyDownloads)
        }
      }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
              stringResource(R.string.queue),
              Modifier.weight(1f),
              style = MaterialTheme.typography.titleSmall,
          )
          IconButton(onClick = showQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
          }
        }
      }
      item { SettingsSectionHeader(stringResource(R.string.subscriptions)) }
      item { AddSubscriptionSection(add) }
      item { SettingsSectionHeader(stringResource(R.string.opml)) }
      item { OpmlSection(importOpml, exportOpml) }
    }

@Composable
private fun AddSubscriptionSection(add: (String, () -> Unit) -> Unit) {
  var url by remember { mutableStateOf("") }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(R.string.add_subscription), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        url,
        { url = it },
        label = { Text(stringResource(R.string.feed_url)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.add_feed), Modifier.weight(1f))
      FilledIconButton(onClick = { add(url) { url = "" } }, enabled = url.startsWith("https://")) {
        Icon(Icons.Filled.Link, stringResource(R.string.add_feed))
      }
    }
  }
}

@Composable
private fun OpmlSection(importOpml: (Uri) -> Unit, exportOpml: (Uri) -> Unit) {
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
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.import_opml), Modifier.weight(1f))
      IconButton(
          onClick = {
            importer.launch(
                arrayOf("application/x-opml", "text/x-opml", "text/xml", "application/xml")
            )
          }
      ) {
        Icon(Icons.Filled.FileUpload, stringResource(R.string.import_opml))
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.export_opml), Modifier.weight(1f))
      IconButton(onClick = { exporter.launch("xpod-subscriptions.opml") }) {
        Icon(Icons.Filled.FileDownload, stringResource(R.string.export_opml))
      }
    }
  }
}

@Composable
private fun SettingsSectionHeader(title: String) =
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Text(
          title,
          Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
