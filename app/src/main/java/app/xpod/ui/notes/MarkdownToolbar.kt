package app.xpod.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.xpod.R
import kotlinx.coroutines.launch

@Composable
internal fun MarkdownToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsert: (String, String, String) -> Unit,
    onChooseImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()
  val actions =
      listOf(
          MarkdownToolbarAction(Icons.Filled.Title, R.string.notes_toolbar_heading, "# ", ""),
          MarkdownToolbarAction(Icons.Filled.FormatBold, R.string.notes_toolbar_bold, "**", "**"),
          MarkdownToolbarAction(Icons.Filled.FormatItalic, R.string.notes_toolbar_italic, "*", "*"),
          MarkdownToolbarAction(
              Icons.Filled.StrikethroughS,
              R.string.notes_toolbar_strike,
              "~~",
              "~~",
          ),
          MarkdownToolbarAction(
              Icons.Filled.InsertLink,
              R.string.notes_toolbar_link,
              "[",
              "](https://)",
          ),
          MarkdownToolbarAction(Icons.Filled.FormatQuote, R.string.notes_toolbar_quote, "> ", ""),
          MarkdownToolbarAction(Icons.Filled.Code, R.string.notes_toolbar_code, "`", "`"),
          MarkdownToolbarAction(
              Icons.Filled.DataObject,
              R.string.notes_toolbar_code_block,
              "```\n",
              "\n```",
          ),
          MarkdownToolbarAction(
              Icons.AutoMirrored.Filled.FormatListBulleted,
              R.string.notes_toolbar_bullet,
              "- ",
              "",
          ),
          MarkdownToolbarAction(
              Icons.Filled.FormatListNumbered,
              R.string.notes_toolbar_numbered,
              "1. ",
              "",
          ),
          MarkdownToolbarAction(Icons.Filled.Checklist, R.string.notes_toolbar_task, "- [ ] ", ""),
          MarkdownToolbarAction(
              Icons.Filled.HorizontalRule,
              R.string.notes_toolbar_rule,
              "\n---\n",
              "",
          ),
      )
  Box(modifier = modifier) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(48.dp)) {
          Icon(
              Icons.AutoMirrored.Filled.Undo,
              contentDescription = stringResource(R.string.notes_toolbar_undo),
          )
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(48.dp)) {
          Icon(
              Icons.AutoMirrored.Filled.Redo,
              contentDescription = stringResource(R.string.notes_toolbar_redo),
          )
        }
        Spacer(
            Modifier.padding(horizontal = 3.dp)
                .size(width = 1.dp, height = 24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(modifier = Modifier.weight(1f)) {
          Row(
              modifier = Modifier.horizontalScroll(scrollState).padding(horizontal = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            actions.forEach { action ->
              IconButton(
                  onClick = { onInsert(action.prefix, action.suffix, "text") },
                  modifier = Modifier.size(48.dp),
              ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = stringResource(action.labelRes),
                )
              }
            }
            IconButton(onClick = onChooseImage, modifier = Modifier.size(48.dp)) {
              Icon(
                  imageVector = Icons.Filled.Image,
                  contentDescription = stringResource(R.string.notes_toolbar_image),
              )
            }
          }
          if (scrollState.canScrollBackward) {
            IconButton(
                onClick = {
                  coroutineScope.launch {
                    scrollState.animateScrollTo(
                        (scrollState.value - TOOLBAR_SCROLL_STEP).coerceAtLeast(0)
                    )
                  }
                },
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
              Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.notes_toolbar_scroll_previous),
              )
            }
          }
          if (scrollState.canScrollForward) {
            IconButton(
                onClick = {
                  coroutineScope.launch {
                    scrollState.animateScrollTo(
                        (scrollState.value + TOOLBAR_SCROLL_STEP).coerceAtMost(scrollState.maxValue)
                    )
                  }
                },
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
              Icon(
                  Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = stringResource(R.string.notes_toolbar_scroll_next),
              )
            }
          }
        }
      }
    }
  }
}

private const val TOOLBAR_SCROLL_STEP = 240

private data class MarkdownToolbarAction(
    val icon: ImageVector,
    val labelRes: Int,
    val prefix: String,
    val suffix: String,
)

internal fun insertMarkdownAtSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
  val text = value.text
  val start = value.selection.start.coerceIn(0, text.length)
  val end = value.selection.end.coerceIn(start, text.length)
  val inserted = "$prefix$placeholder$suffix"
  val nextText = text.replaceRange(start, end, inserted)
  val nextSelection = start + prefix.length + placeholder.length
  return TextFieldValue(
      text = nextText,
      selection = TextRange(nextSelection.coerceIn(0, nextText.length)),
  )
}
