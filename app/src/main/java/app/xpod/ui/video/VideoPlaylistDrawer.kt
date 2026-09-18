package app.xpod.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xpod.R
import app.xpod.data.LocalVideoEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlaylistDrawer(
    playlist: List<LocalVideoEntity>,
    currentVideoId: String?,
    onDismiss: () -> Unit,
    onSelectVideo: (String) -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(Modifier.fillMaxSize()) {
      Box(
          Modifier.fillMaxHeight()
              .fillMaxWidth(0.5f)
              .align(Alignment.CenterStart)
              .background(Color.Black.copy(alpha = 0.28f))
              .clickable(onClick = onDismiss),
      )
      Surface(
          modifier =
              Modifier.fillMaxHeight()
                  .fillMaxWidth(0.5f)
                  .align(Alignment.CenterEnd)
                  .statusBarsPadding()
                  .navigationBarsPadding()
                  .clickable(onClick = {}),
          color = Color.Transparent,
          tonalElevation = 0.dp,
          shape = MaterialTheme.shapes.large,
      ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(playlist, key = { it.id }) { item ->
            val active = item.id == currentVideoId
            Surface(
                color =
                    if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier.fillMaxWidth().clickable(enabled = !active) { onSelectVideo(item.id) },
            ) {
              Row(
                  Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                VideoThumbnail(
                    video = item,
                    modifier = Modifier.size(72.dp, 44.dp),
                    contentDescription = item.title,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                  Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(
                      if (active) stringResource(R.string.now_playing)
                      else videoDetails(item, stringResource(R.string.video_file_details)),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      style = MaterialTheme.typography.bodySmall,
                      color =
                          if (active) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
