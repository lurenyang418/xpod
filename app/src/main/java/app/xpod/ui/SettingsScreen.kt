package app.xpod.ui

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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ThemeMode

@Composable
internal fun SettingsScreen(
    theme: ThemeMode,
    dynamicColor: Boolean,
    wifiOnlyDownloads: Boolean,
    cloudMemos: CloudMemosUiState,
    setTheme: (ThemeMode) -> Unit,
    setDynamicColor: (Boolean) -> Unit,
    setWifiOnlyDownloads: (Boolean) -> Unit,
    showQueue: () -> Unit,
    add: (String, () -> Unit) -> Unit,
    importOpml: (Uri) -> Unit,
    exportOpml: (Uri) -> Unit,
    configureCloudMemos: (String, String, () -> Unit) -> Unit,
    disconnectCloudMemos: () -> Unit,
    tabOrder: List<AppTab>,
    enabledTabs: Set<AppTab>,
    moveTab: (AppTab, Int) -> Unit,
    setTabEnabled: (AppTab, Boolean) -> Unit,
) {
  var showTabOrder by rememberSaveable { mutableStateOf(false) }
  var showAddSubscription by rememberSaveable { mutableStateOf(false) }
  var showCloudMemos by rememberSaveable { mutableStateOf(false) }
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
      modifier = Modifier.fillMaxSize(),
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
  }

  if (showTabOrder) {
    TabOrderDialog(
        tabOrder = tabOrder,
        enabledTabs = enabledTabs,
        moveTab = moveTab,
        setTabEnabled = setTabEnabled,
        onDismiss = { showTabOrder = false },
    )
  }
  if (showAddSubscription) {
    AddSubscriptionDialog(
        add = add,
        onDismiss = { showAddSubscription = false },
    )
  }
  if (showCloudMemos) {
    CloudMemosDialog(
        state = cloudMemos,
        configure = configureCloudMemos,
        disconnect = disconnectCloudMemos,
        onDismiss = { showCloudMemos = false },
    )
  }
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
private fun TabOrderDialog(
    tabOrder: List<AppTab>,
    enabledTabs: Set<AppTab>,
    moveTab: (AppTab, Int) -> Unit,
    setTabEnabled: (AppTab, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.tab_order)) },
      text = {
        Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
          Text(
              stringResource(R.string.tab_order_summary),
              modifier = Modifier.padding(bottom = 8.dp),
              style = MaterialTheme.typography.bodyMedium,
          )
          tabOrder.forEachIndexed { index, tab ->
            val tabName = tabLabel(tab)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(tabName)
                if (tab == AppTab.Settings) {
                  Text(
                      stringResource(R.string.tab_always_shown),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
              Switch(
                  checked = tab in enabledTabs,
                  onCheckedChange = { enabled -> setTabEnabled(tab, enabled) },
                  enabled = tab != AppTab.Settings,
                  modifier = Modifier.semantics { contentDescription = tabName },
              )
              IconButton(onClick = { moveTab(tab, -1) }, enabled = index > 0) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
              }
              IconButton(onClick = { moveTab(tab, 1) }, enabled = index < tabOrder.lastIndex) {
                Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
  )
}

@Composable
private fun AddSubscriptionDialog(
    add: (String, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by rememberSaveable { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.add_subscription)) },
      text = {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.feed_url)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
            onClick = { add(url) { onDismiss() } },
            enabled = url.startsWith("https://", ignoreCase = true),
        ) {
          Text(stringResource(R.string.add_feed))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
  )
}

@Composable
private fun CloudMemosDialog(
    state: CloudMemosUiState,
    configure: (String, String, () -> Unit) -> Unit,
    disconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
  var baseUrl by rememberSaveable(state.baseUrl) { mutableStateOf(state.baseUrl) }
  var token by rememberSaveable { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.cloud_memos)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              stringResource(R.string.cloud_memos_summary),
              style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedTextField(
              value = baseUrl,
              onValueChange = { baseUrl = it },
              label = { Text(stringResource(R.string.cloud_memos_instance_url)) },
              placeholder = { Text("https://memos.example.com") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
              value = token,
              onValueChange = { token = it },
              label = { Text(stringResource(R.string.cloud_memos_api_token)) },
              placeholder = { Text("cm_pat_…") },
              supportingText = {
                Text(
                    stringResource(
                        if (state.isConfigured) R.string.cloud_memos_token_saved
                        else R.string.cloud_memos_token_hint
                    )
                )
              },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth(),
          )
          if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
          }
        }
      },
      confirmButton = {
        Button(
            onClick = { configure(baseUrl, token) { onDismiss() } },
            enabled =
                !state.isBusy &&
                    baseUrl.startsWith("https://", ignoreCase = true) &&
                    (token.isNotBlank() || state.isConfigured),
        ) {
          Text(
              stringResource(
                  if (state.isConfigured) R.string.cloud_memos_verify
                  else R.string.cloud_memos_connect
              )
          )
        }
      },
      dismissButton = {
        Row {
          if (state.isConfigured) {
            TextButton(
                onClick = {
                  disconnect()
                  onDismiss()
                },
                enabled = !state.isBusy,
            ) {
              Text(stringResource(R.string.cloud_memos_disconnect))
            }
          }
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
      },
  )
}

@Composable
private fun tabLabel(tab: AppTab): String =
    stringResource(
        when (tab) {
          AppTab.Podcasts -> R.string.podcasts
          AppTab.Reader -> R.string.reader
          AppTab.Library -> R.string.library
          AppTab.Memos -> R.string.memos
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
