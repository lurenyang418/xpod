package app.xpod.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.ui.coordination.CloudMemosUiState

@Composable
internal fun SettingsDialogs(
    showTabOrder: Boolean,
    tabOrder: List<AppTab>,
    enabledTabs: Set<AppTab>,
    moveTab: (AppTab, Int) -> Unit,
    setTabEnabled: (AppTab, Boolean) -> Unit,
    onDismissTabOrder: () -> Unit,
    showAddSubscription: Boolean,
    add: (String, (Boolean) -> Unit) -> Unit,
    onDismissAddSubscription: () -> Unit,
    showCloudMemos: Boolean,
    cloudMemos: CloudMemosUiState,
    configureCloudMemos: (String, String, () -> Unit) -> Unit,
    disconnectCloudMemos: () -> Unit,
    onDismissCloudMemos: () -> Unit,
) {
  if (showTabOrder) {
    TabOrderDialog(
        tabOrder = tabOrder,
        enabledTabs = enabledTabs,
        moveTab = moveTab,
        setTabEnabled = setTabEnabled,
        onDismiss = onDismissTabOrder,
    )
  }
  if (showAddSubscription) {
    AddSubscriptionDialog(
        add = add,
        onDismiss = onDismissAddSubscription,
    )
  }
  if (showCloudMemos) {
    CloudMemosDialog(
        state = cloudMemos,
        configure = configureCloudMemos,
        disconnect = disconnectCloudMemos,
        onDismiss = onDismissCloudMemos,
    )
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
    add: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by rememberSaveable { mutableStateOf("") }
  var submitting by remember { mutableStateOf(false) }
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
            onClick = {
              submitting = true
              add(url) { success -> if (success) onDismiss() else submitting = false }
            },
            enabled = !submitting && url.startsWith("https://", ignoreCase = true),
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
  // The API token is a secret: keep it out of saved instance state (plain remember only).
  var token by remember { mutableStateOf("") }
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
