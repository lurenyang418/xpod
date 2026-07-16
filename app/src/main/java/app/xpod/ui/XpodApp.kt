package app.xpod.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.playback.NowPlaying

private enum class Destination {
  Subscriptions,
  Reader,
  Library,
  Settings,
}

@Composable
fun XpodApp(viewModel: MainViewModel = hiltViewModel()) {
  val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
  val theme by viewModel.appTheme.collectAsStateWithLifecycle()
  val dark =
      when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
  val context = LocalContext.current
  val notificationPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val requestNotificationPermission = {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    )
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
  val scheme =
      when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
      }
  MaterialTheme(colorScheme = scheme) {
    XpodHome(viewModel, theme, dynamic, requestNotificationPermission)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XpodHome(
    viewModel: MainViewModel,
    theme: ThemeMode,
    dynamic: Boolean,
    requestNotificationPermission: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
  val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
  val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val containerWidth = LocalWindowInfo.current.containerSize.width
  val wide = with(LocalDensity.current) { containerWidth.toDp() >= 600.dp }
  val snackbar = remember { SnackbarHostState() }
  var destination by rememberSaveable { mutableStateOf(Destination.Subscriptions) }
  var selectedEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedArticleId by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedEpisode = selectedEpisodeId?.let { id ->
    (state.episodes + state.libraryEpisodes + queue.episodes).firstOrNull { it.id == id }
  }
  val selectedArticle = selectedArticleId?.let { id ->
    state.articles.firstOrNull { article -> article.id == id }
  }
  var podcastToDelete by remember { mutableStateOf<PodcastEntity?>(null) }
  var articleFeedToDelete by remember { mutableStateOf<ArticleFeedEntity?>(null) }
  var downloadToRemove by remember { mutableStateOf<EpisodeEntity?>(null) }
  var fullPlayer by rememberSaveable { mutableStateOf(false) }
  var showSpeedPicker by rememberSaveable { mutableStateOf(false) }
  var showQueue by rememberSaveable { mutableStateOf(false) }
  var confirmClearQueue by remember { mutableStateOf(false) }
  val handleDownload: (EpisodeEntity) -> Unit = { episode ->
    if (downloadStates[episode.id]?.isCompleted == true) {
      downloadToRemove = episode
    } else {
      requestNotificationPermission()
      viewModel.download(episode)
    }
  }

  LaunchedEffect(state.status) {
    state.status?.let {
      snackbar.showSnackbar(it)
      viewModel.dismissStatus()
    }
  }
  val back: () -> Unit = {
    when {
      fullPlayer -> fullPlayer = false
      selectedEpisode != null -> selectedEpisodeId = null
      selectedArticleId != null -> selectedArticleId = null
      destination == Destination.Subscriptions && state.selectedPodcastId != null ->
          viewModel.selectPodcast(null)
    }
  }
  BackHandler(
      enabled =
          fullPlayer ||
              selectedEpisode != null ||
              selectedArticleId != null ||
              destination == Destination.Subscriptions && state.selectedPodcastId != null,
      onBack = back,
  )
  val content: @Composable () -> Unit = {
    when {
      fullPlayer ->
          nowPlaying?.let { playing ->
            FullPlayerScreen(
                nowPlaying = playing,
                podcast = state.podcasts.firstOrNull { it.id == playing.episode.podcastId },
                onToggle = {
                  requestNotificationPermission()
                  viewModel.togglePlayback()
                },
                onSeek = viewModel::seekTo,
                onSkipBack = { viewModel.seekBy(-10_000L) },
                onSkipForward = { viewModel.seekBy(30_000L) },
                onShowSpeedPicker = { showSpeedPicker = true },
                onOpenPodcast = {
                  destination = Destination.Subscriptions
                  fullPlayer = false
                  viewModel.selectPodcast(playing.episode.podcastId)
                },
            )
          }
      selectedEpisode != null -> {
        val episode = selectedEpisode
        EpisodeDetailScreen(
            episode = episode,
            isPlaying = nowPlaying?.episode?.id == episode.id && nowPlaying?.isPlaying == true,
            onPlay = {
              requestNotificationPermission()
              viewModel.play(episode)
            },
            onTogglePlayback = {
              requestNotificationPermission()
              viewModel.togglePlayback()
            },
            onFavorite = { viewModel.toggleFavorite(episode.id) },
            onPlayed = { viewModel.markPlayed(episode.id, !episode.isPlayed) },
            downloadState = downloadStates[episode.id],
            onDownload = { handleDownload(episode) },
            onPlayNext = { viewModel.playNext(episode) },
            onAddToQueue = { viewModel.addToQueue(episode) },
        )
      }
      selectedArticle != null ->
          ArticleReaderScreen(
              article = selectedArticle,
              feedTitle = state.articleFeeds.firstOrNull { it.id == selectedArticle.feedId }?.title,
              setRead = viewModel::setArticleRead,
              toggleFavorite = viewModel::toggleArticleFavorite,
              onBack = { selectedArticleId = null },
          )
      destination == Destination.Subscriptions ->
          SubscriptionScreen(
              state = state,
              wide = wide,
              select = viewModel::selectPodcast,
              refresh = viewModel::refresh,
              play = {
                requestNotificationPermission()
                viewModel.play(it)
              },
              download = handleDownload,
              favorite = viewModel::toggleFavorite,
              played = viewModel::markPlayed,
              nowPlaying = nowPlaying,
              downloadStates = downloadStates,
              openEpisode = { selectedEpisodeId = it.id },
              togglePlayback = {
                requestNotificationPermission()
                viewModel.togglePlayback()
              },
              addToQueue = viewModel::addToQueue,
              showQueue = { showQueue = true },
              delete = { podcastToDelete = it },
              openSettings = { destination = Destination.Settings },
          )
      destination == Destination.Library ->
          LibraryScreen(
              state = state,
              play = {
                requestNotificationPermission()
                viewModel.play(it)
              },
              favorite = viewModel::toggleFavorite,
              download = handleDownload,
              played = viewModel::markPlayed,
              nowPlaying = nowPlaying,
              downloadStates = downloadStates,
              openEpisode = { selectedEpisodeId = it.id },
              togglePlayback = {
                requestNotificationPermission()
                viewModel.togglePlayback()
              },
              addToQueue = viewModel::addToQueue,
          )
      destination == Destination.Reader ->
          ReaderScreen(
              state = state,
              refresh = viewModel::refreshArticles,
              openArticle = { article ->
                viewModel.markArticleRead(article.id)
                selectedArticleId = article.id
              },
              setRead = viewModel::setArticleRead,
              toggleFavorite = viewModel::toggleArticleFavorite,
              delete = { articleFeedToDelete = it },
          )
      else ->
          SettingsScreen(
              theme = theme,
              dynamicColor = dynamic,
              wifiOnlyDownloads = wifiOnlyDownloads,
              setTheme = viewModel::setAppTheme,
              setDynamicColor = viewModel::setDynamicColor,
              setWifiOnlyDownloads = viewModel::setWifiOnlyDownloads,
              showQueue = { showQueue = true },
              add = { url, onSuccess -> viewModel.addFeed(url, onSuccess) },
              importOpml = viewModel::importOpml,
              exportOpml = viewModel::exportOpml,
          )
    }
  }
  Scaffold(
      topBar = {
        HomeTopBar(
            show =
                fullPlayer ||
                    selectedEpisode != null ||
                    !wide &&
                        destination == Destination.Subscriptions &&
                        state.selectedPodcastId != null,
            fullPlayer = fullPlayer,
            selectedEpisode = selectedEpisode,
            onBack = back,
            onShowQueue = { showQueue = true },
        )
      },
      bottomBar = {
        HomeBottomBar(
            visible = !wide && !fullPlayer && selectedArticleId == null,
            nowPlaying = nowPlaying,
            destination = destination,
            onDestinationSelected = {
              destination = it
              selectedEpisodeId = null
              selectedArticleId = null
            },
            onToggle = {
              requestNotificationPermission()
              viewModel.togglePlayback()
            },
            onOpenPlayer = { fullPlayer = true },
            onShowSpeedPicker = { showSpeedPicker = true },
        )
      },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      Row(Modifier.fillMaxSize()) {
        if (wide && selectedArticleId == null)
            NavigationRail {
              Destination.entries.forEach { item ->
                NavigationRailItem(
                    selected = item == destination,
                    onClick = {
                      destination = item
                      selectedEpisodeId = null
                      selectedArticleId = null
                    },
                    icon = { DestinationIcon(item) },
                    label = { Text(destinationLabel(item)) },
                )
              }
            }
        content()
      }
      SnackbarHost(
          snackbar,
          Modifier.align(Alignment.TopCenter)
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
      )
    }
  }
  nowPlaying
      ?.takeIf { showSpeedPicker }
      ?.let { playing ->
        SpeedPicker(
            selected = playing.speed,
            onSelect = { speed ->
              viewModel.setPlaybackSpeed(speed)
              showSpeedPicker = false
            },
            onDismiss = { showSpeedPicker = false },
        )
      }
  if (showQueue) {
    QueueSheet(
        queue = queue,
        onDismiss = { showQueue = false },
        onClear = { confirmClearQueue = true },
        onOpenEpisode = {
          selectedEpisodeId = it.id
          showQueue = false
        },
        onPlay = viewModel::playQueueItem,
        onMove = viewModel::moveQueueItem,
        onRemove = viewModel::removeFromQueue,
    )
  }
  HomeDialogs(
      podcastToDelete = podcastToDelete,
      articleFeedToDelete = articleFeedToDelete,
      downloadToRemove = downloadToRemove,
      confirmClearQueue = confirmClearQueue,
      onDismissPodcastDelete = { podcastToDelete = null },
      onRemovePodcast = {
        viewModel.removePodcast(it)
        podcastToDelete = null
      },
      onDismissArticleFeedDelete = { articleFeedToDelete = null },
      onRemoveArticleFeed = {
        viewModel.removeArticleFeed(it)
        articleFeedToDelete = null
      },
      onDismissDownloadRemove = { downloadToRemove = null },
      onRemoveDownload = {
        viewModel.removeDownload(it)
        downloadToRemove = null
      },
      onDismissClearQueue = { confirmClearQueue = false },
      onClearQueue = {
        viewModel.clearQueue()
        confirmClearQueue = false
        showQueue = false
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    show: Boolean,
    fullPlayer: Boolean,
    selectedEpisode: EpisodeEntity?,
    onBack: () -> Unit,
    onShowQueue: () -> Unit,
) {
  if (!show) return
  TopAppBar(
      title = {
        Text(
            if (fullPlayer) stringResource(R.string.now_playing)
            else if (selectedEpisode != null) stringResource(R.string.episode)
            else stringResource(R.string.episodes),
        )
      },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
      },
      actions = {
        if (fullPlayer)
            IconButton(onClick = onShowQueue) {
              Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
            }
      },
  )
}

@Composable
private fun HomeBottomBar(
    visible: Boolean,
    nowPlaying: NowPlaying?,
    destination: Destination,
    onDestinationSelected: (Destination) -> Unit,
    onToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowSpeedPicker: () -> Unit,
) {
  if (!visible) return
  Column {
    nowPlaying?.let { MiniPlayer(it, onToggle, onOpenPlayer, onShowSpeedPicker) }
    NavigationBar {
      Destination.entries.forEach { item ->
        NavigationBarItem(
            selected = item == destination,
            onClick = { onDestinationSelected(item) },
            icon = { DestinationIcon(item) },
            label = { Text(destinationLabel(item)) },
        )
      }
    }
  }
}

@Composable
private fun HomeDialogs(
    podcastToDelete: PodcastEntity?,
    articleFeedToDelete: ArticleFeedEntity?,
    downloadToRemove: EpisodeEntity?,
    confirmClearQueue: Boolean,
    onDismissPodcastDelete: () -> Unit,
    onRemovePodcast: (String) -> Unit,
    onDismissArticleFeedDelete: () -> Unit,
    onRemoveArticleFeed: (String) -> Unit,
    onDismissDownloadRemove: () -> Unit,
    onRemoveDownload: (String) -> Unit,
    onDismissClearQueue: () -> Unit,
    onClearQueue: () -> Unit,
) {
  podcastToDelete?.let { podcast ->
    AlertDialog(
        onDismissRequest = onDismissPodcastDelete,
        title = { Text(stringResource(R.string.remove_subscription_title)) },
        text = { Text(stringResource(R.string.remove_subscription_message)) },
        confirmButton = {
          TextButton(onClick = { onRemovePodcast(podcast.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissPodcastDelete) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
  articleFeedToDelete?.let { feed ->
    AlertDialog(
        onDismissRequest = onDismissArticleFeedDelete,
        title = { Text(stringResource(R.string.remove_subscription_title)) },
        text = { Text(stringResource(R.string.remove_article_subscription_message)) },
        confirmButton = {
          TextButton(onClick = { onRemoveArticleFeed(feed.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissArticleFeedDelete) {
            Text(stringResource(R.string.cancel))
          }
        },
    )
  }
  downloadToRemove?.let { episode ->
    AlertDialog(
        onDismissRequest = onDismissDownloadRemove,
        title = { Text(stringResource(R.string.remove_download_title)) },
        text = { Text(stringResource(R.string.remove_download_message, episode.title)) },
        confirmButton = {
          TextButton(onClick = { onRemoveDownload(episode.id) }) {
            Text(stringResource(R.string.remove))
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissDownloadRemove) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
  if (confirmClearQueue) {
    AlertDialog(
        onDismissRequest = onDismissClearQueue,
        title = { Text(stringResource(R.string.clear_queue_title)) },
        text = { Text(stringResource(R.string.clear_queue_message)) },
        confirmButton = {
          TextButton(onClick = onClearQueue) { Text(stringResource(R.string.clear_queue)) }
        },
        dismissButton = {
          TextButton(onClick = onDismissClearQueue) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
}

@Composable
private fun DestinationIcon(destination: Destination) =
    when (destination) {
      Destination.Subscriptions -> Icon(Icons.Filled.RssFeed, null)
      Destination.Reader -> Icon(Icons.AutoMirrored.Filled.Article, null)
      Destination.Library -> Icon(Icons.Filled.LibraryMusic, null)
      Destination.Settings -> Icon(Icons.Filled.Settings, null)
    }

@Composable
private fun destinationLabel(destination: Destination): String =
    stringResource(
        when (destination) {
          Destination.Subscriptions -> R.string.podcasts
          Destination.Reader -> R.string.reader
          Destination.Library -> R.string.library
          Destination.Settings -> R.string.settings
        }
    )
