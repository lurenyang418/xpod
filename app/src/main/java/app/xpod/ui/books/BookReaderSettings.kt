package app.xpod.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.ReadingTheme

@Composable
internal fun ReadingSettingsDialog(
    fontSizeSp: Float,
    lineHeight: Float,
    theme: ReadingTheme,
    setFontSize: (Float) -> Unit,
    setLineHeight: (Float) -> Unit,
    setTheme: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit,
) {
  var draftFontSizeSp by remember(fontSizeSp) { mutableFloatStateOf(fontSizeSp) }
  var draftLineHeight by remember(lineHeight) { mutableFloatStateOf(lineHeight) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.reading_theme)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.reading_font_size))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FormatSize, null)
            Slider(
                value = draftFontSizeSp,
                onValueChange = { draftFontSizeSp = it },
                onValueChangeFinished = { setFontSize(draftFontSizeSp) },
                valueRange = 12f..32f,
                modifier = Modifier.weight(1f),
            )
          }
          Text(stringResource(R.string.reading_line_height))
          Slider(
              value = draftLineHeight,
              onValueChange = { draftLineHeight = it },
              onValueChangeFinished = { setLineHeight(draftLineHeight) },
              valueRange = 1.1f..2.4f,
          )
          ReadingTheme.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              androidx.compose.material3.RadioButton(
                  selected = theme == option,
                  onClick = { setTheme(option) },
              )
              Text(readingThemeLabel(option))
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
  )
}

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
