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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
      onDismissRequest = { if (!submitting) onDismiss() },
      title = { Text(stringResource(R.string.add_subscription)) },
      text = {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.feed_url)) },
            placeholder = { Text("https://example.com/feed.xml") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        val addActionLabel =
            stringResource(if (submitting) R.string.add_feed_submitting else R.string.add_feed)
        IconButton(
            modifier = Modifier.semantics { contentDescription = addActionLabel },
            onClick = {
              val normalizedUrl = normalizedHttpsUrl(url) ?: return@IconButton
              submitting = true
              add(normalizedUrl) { success -> if (success) onDismiss() else submitting = false }
            },
            enabled = !submitting && normalizedHttpsUrl(url) != null,
        ) {
          if (submitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Filled.AddLink, contentDescription = null)
          }
        }
      },
      dismissButton = {
        IconButton(onClick = onDismiss, enabled = !submitting) {
          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
        }
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
      onDismissRequest = { if (!state.isBusy) onDismiss() },
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
            onClick = {
              val normalizedUrl = normalizedHttpsUrl(baseUrl) ?: return@Button
              configure(normalizedUrl, token.trim()) { onDismiss() }
            },
            enabled =
                !state.isBusy &&
                    normalizedHttpsUrl(baseUrl) != null &&
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
          TextButton(onClick = onDismiss, enabled = !state.isBusy) {
            Text(stringResource(R.string.cancel))
          }
        }
      },
  )
}

private fun normalizedHttpsUrl(value: String): String? {
  val trimmed = value.trim()
  if (trimmed.isEmpty()) return null
  val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
  if (!uri.scheme.equals("https", ignoreCase = true)) return null
  val authority = uri.rawAuthority ?: return null
  if (uri.rawUserInfo != null || !validHttpsAuthority(authority, uri.port)) return null
  return trimmed.replaceRange(0, uri.scheme.length, "https")
}

private fun validHttpsAuthority(authority: String, parsedPort: Int): Boolean {
  if (parsedPort !in -1..65535) return false
  val host =
      if (authority.startsWith("[")) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return false
        val suffix = authority.substring(closingBracket + 1)
        if (suffix.isNotEmpty() && !suffix.matches(Regex(":\\d{1,5}"))) return false
        authority.substring(1, closingBracket)
      } else {
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
          if (authority.indexOf(':') != colon) return false
          val port = authority.substring(colon + 1).toIntOrNull() ?: return false
          if (port !in 0..65535) return false
          authority.substring(0, colon)
        } else {
          authority
        }
      }
  if (host.isBlank()) return false
  if (host.contains(':')) return true // Bracketed IPv6 literal.
  return runCatching {
        java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES).isNotBlank()
      }
      .getOrDefault(false)
}
