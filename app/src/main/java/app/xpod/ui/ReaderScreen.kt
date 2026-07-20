package app.xpod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xpod.R
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ReaderFilter {
  All,
  Unread,
  Favorites,
}

@Composable
internal fun ReaderScreen(
    state: MainUiState,
    refresh: (String?) -> Unit,
    openArticle: (ArticleEntity) -> Unit,
    setRead: (String, Boolean) -> Unit,
    toggleFavorite: (String) -> Unit,
    delete: (ArticleFeedEntity) -> Unit,
    requestMarkAllRead: (String?) -> Unit,
    bulkActionBusy: Boolean,
) {
  var filter by rememberSaveable { mutableStateOf(ReaderFilter.All) }
  var feedId by rememberSaveable { mutableStateOf<String?>(null) }
  var actionsExpanded by remember { mutableStateOf(false) }
  val selectedFeed = state.articleFeeds.firstOrNull { it.id == feedId }
  val unreadCount = unreadArticleCount(state.articles, feedId)
  LaunchedEffect(feedId, selectedFeed) {
    if (feedId != null && selectedFeed == null) feedId = null
  }
  val contentParser = remember { ArticleContentParser() }
  val feedTitles =
      remember(state.articleFeeds) { state.articleFeeds.associate { it.id to it.title } }
  val articles =
      when (filter) {
        ReaderFilter.All -> state.articles
        ReaderFilter.Unread -> state.articles.filterNot { it.isRead }
        ReaderFilter.Favorites -> state.articles.filter { it.isFavorite }
      }.filter { feedId == null || it.feedId == feedId }
  Column(Modifier.fillMaxSize().padding(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.reader),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.headlineSmall,
      )
      if (state.isRefreshingArticles) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
      } else {
        IconButton(
            onClick = { refresh(selectedFeed?.feedUrl) },
            enabled = state.articleFeeds.isNotEmpty(),
        ) {
          Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_articles))
        }
      }
      if (state.articleFeeds.isNotEmpty()) {
        Box {
          IconButton(onClick = { actionsExpanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.reader_actions))
          }
          DropdownMenu(
              expanded = actionsExpanded,
              onDismissRequest = { actionsExpanded = false },
          ) {
            DropdownMenuItem(
                text = {
                  Text(
                      stringResource(
                          if (selectedFeed == null) R.string.mark_all_articles_read
                          else R.string.mark_feed_read
                      )
                  )
                },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                enabled = unreadCount > 0 && !bulkActionBusy,
                onClick = {
                  actionsExpanded = false
                  requestMarkAllRead(selectedFeed?.id)
                },
            )
            selectedFeed?.let { feed ->
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.remove_subscription)) },
                  leadingIcon = { Icon(Icons.Filled.Delete, null) },
                  enabled = !bulkActionBusy,
                  onClick = {
                    actionsExpanded = false
                    delete(feed)
                  },
              )
            }
          }
        }
      }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      item {
        FilterChip(
            selected = feedId == null,
            onClick = { feedId = null },
            label = { Text(stringResource(R.string.all_feeds)) },
        )
      }
      items(state.articleFeeds, key = { it.id }) { feed ->
        FilterChip(
            selected = feedId == feed.id,
            onClick = { feedId = feed.id },
            label = { Text(feed.title) },
        )
      }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(ReaderFilter.entries) { item ->
        FilterChip(
            selected = filter == item,
            onClick = { filter = item },
            label = { Text(readerFilterLabel(item)) },
        )
      }
    }
    if (state.articleFeeds.isEmpty()) {
      Text(stringResource(R.string.no_reader_subscriptions), Modifier.padding(top = 24.dp))
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (articles.isEmpty() && state.articleFeeds.isNotEmpty()) {
        item {
          Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = stringResource(R.string.no_articles_match),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      items(articles, key = { it.id }) { article ->
        Card(
            onClick = { openArticle(article) },
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (!article.isRead) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Spacer(Modifier.size(8.dp))
              }
              Text(
                  text = article.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
              )
            }
            val metadata =
                listOfNotNull(
                        feedTitles[article.feedId]?.takeIf { it.isNotBlank() },
                        article.author.takeIf { it.isNotBlank() },
                    )
                    .joinToString(" · ")
            if (metadata.isNotEmpty()) {
              Text(metadata, style = MaterialTheme.typography.bodySmall)
            }
            article.publishedEpochMs
                .takeIf { it > 0 }
                ?.let { Text(formatArticleDate(it), style = MaterialTheme.typography.bodySmall) }
            val summary = remember(article.content) { contentParser.plainText(article.content) }
            if (summary.isNotBlank()) {
              Text(
                  text = summary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              IconButton(onClick = { setRead(article.id, !article.isRead) }) {
                Icon(
                    imageVector =
                        if (article.isRead) Icons.Filled.CheckCircle
                        else Icons.Filled.RadioButtonUnchecked,
                    contentDescription =
                        stringResource(
                            if (article.isRead) R.string.mark_unread else R.string.mark_read
                        ),
                    tint =
                        if (article.isRead) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { toggleFavorite(article.id) }) {
                Icon(
                    imageVector =
                        if (article.isFavorite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                    contentDescription =
                        stringResource(
                            if (article.isFavorite) R.string.remove_favorite else R.string.favorite
                        ),
                    tint =
                        if (article.isFavorite) MaterialTheme.colorScheme.primary
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

@Composable
private fun readerFilterLabel(filter: ReaderFilter) =
    stringResource(
        when (filter) {
          ReaderFilter.All -> R.string.all
          ReaderFilter.Unread -> R.string.unread
          ReaderFilter.Favorites -> R.string.favorites
        }
    )

internal fun formatArticleDate(value: Long) =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(value))
