package app.xpod.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.EpisodeEntity
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.data.cloudMemoWebUrl
import app.xpod.playback.NowPlaying
import kotlinx.coroutines.launch

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
  val context = LocalContext.current
  val resources = LocalResources.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
  val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
  val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
  val cloudMemos by viewModel.cloudMemosState.collectAsStateWithLifecycle()
  val memos by viewModel.memosState.collectAsStateWithLifecycle()
  val bulkActions by viewModel.bulkActionsState.collectAsStateWithLifecycle()
  val tabOrder by viewModel.tabOrder.collectAsStateWithLifecycle()
  val enabledTabs by viewModel.enabledTabs.collectAsStateWithLifecycle()
  val visibleTabs = tabOrder.filter(enabledTabs::contains)
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val containerWidth = LocalWindowInfo.current.containerSize.width
  val wide = with(LocalDensity.current) { containerWidth.toDp() >= 600.dp }
  val snackbar = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val memosComposerActions =
      remember(viewModel) {
        MemosComposerActions(
            setDraft = viewModel::setMemoDraft,
            setVisibility = viewModel::setMemoVisibility,
            create = viewModel::createMemo,
        )
      }
  val memosListActions =
      remember(viewModel) {
        MemosListActions(
            load = viewModel::loadMemos,
            refresh = viewModel::refreshMemos,
            loadMore = viewModel::loadMoreMemos,
            setQuery = viewModel::setMemoQuery,
            selectTag = viewModel::selectMemoTag,
            search = viewModel::searchMemos,
        )
      }
  val memosShareActions =
      remember(viewModel, context, resources, cloudMemos.baseUrl, coroutineScope, snackbar) {
        MemosShareActions(
            copyMemo = { memo -> copyMemoMarkdown(context, memo) },
            shareMemoLink = { memo ->
              val url = cloudMemoWebUrl(cloudMemos.baseUrl, memo.id)
              val text =
                  if (memo.visibility == CloudMemoVisibility.Members) {
                    resources.getString(R.string.member_memo_share_text, url)
                  } else {
                    url
                  }
              if (
                  !shareText(
                      context = context,
                      text = text,
                      chooserTitle = resources.getString(R.string.share_memo),
                  )
              ) {
                coroutineScope.launch {
                  snackbar.showSnackbar(resources.getString(R.string.share_unavailable))
                }
              }
            },
            requestPrivateMemoShare = viewModel::requestPrivateMemoShare,
            dismissPrivateMemoShare = viewModel::dismissPrivateMemoShare,
            sharePrivateMemoContent = { memo ->
              if (memo.visibility == CloudMemoVisibility.Private) {
                if (
                    !shareText(
                        context = context,
                        text = memo.content,
                        chooserTitle = resources.getString(R.string.share_markdown),
                    )
                ) {
                  coroutineScope.launch {
                    snackbar.showSnackbar(resources.getString(R.string.share_unavailable))
                  }
                }
              }
            },
        )
      }
  val memosManageActions =
      remember(viewModel) {
        MemosManageActions(
            archiveMemo = viewModel::archiveMemo,
            requestDelete = viewModel::requestMemoDelete,
            dismissDelete = viewModel::dismissMemoDelete,
            moveToTrash = viewModel::moveMemoToTrash,
        )
      }
  var destination by rememberSaveable { mutableStateOf(AppTab.Podcasts) }
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

  LaunchedEffect(visibleTabs, destination) {
    if (destination !in visibleTabs) {
      destination = AppTab.Settings
      selectedEpisodeId = null
      selectedArticleId = null
      viewModel.selectPodcast(null)
    }
  }
  LaunchedEffect(destination) {
    if (destination != AppTab.Memos) {
      viewModel.dismissPrivateMemoShare()
      viewModel.dismissMemoDelete()
    }
  }
  LaunchedEffect(nowPlaying == null) {
    if (nowPlaying == null) {
      fullPlayer = false
      showSpeedPicker = false
    }
  }
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
  val archivedMemoForUndo = memos.archivedMemoForUndo
  LaunchedEffect(
      archivedMemoForUndo?.id,
      archivedMemoForUndo?.version,
      memos.archivedMemoUndoSequence,
  ) {
    archivedMemoForUndo?.let { memo ->
      val result =
          snackbar.showSnackbar(
              message = resources.getString(R.string.cloud_memo_archived),
              actionLabel = resources.getString(R.string.undo),
              withDismissAction = true,
              duration = SnackbarDuration.Long,
          )
      if (result == SnackbarResult.ActionPerformed) {
        viewModel.restoreArchivedMemo(memo.id)
      } else {
        viewModel.dismissArchivedMemoUndo(memo.id)
      }
    }
  }
  val bulkUndoEvent = bulkActions.undoEvent
  LaunchedEffect(bulkUndoEvent?.id) {
    bulkUndoEvent?.let { event ->
      val message =
          when (event.kind) {
            BulkMarkKind.PodcastEpisodes ->
                resources.getQuantityString(
                    R.plurals.marked_episodes_played,
                    event.count,
                    event.count,
                )
            BulkMarkKind.Articles ->
                resources.getQuantityString(
                    R.plurals.marked_articles_read,
                    event.count,
                    event.count,
                )
          }
      val result =
          snackbar.showSnackbar(
              message = message,
              actionLabel = resources.getString(R.string.undo),
              withDismissAction = true,
              duration = SnackbarDuration.Long,
          )
      if (result == SnackbarResult.ActionPerformed) {
        viewModel.undoBulkMark(event.id)
      } else {
        viewModel.dismissBulkUndo(event.id)
      }
    }
  }
  val back: () -> Unit = {
    when {
      fullPlayer -> fullPlayer = false
      selectedEpisode != null -> selectedEpisodeId = null
      selectedArticleId != null -> selectedArticleId = null
      destination == AppTab.Podcasts && state.selectedPodcastId != null ->
          viewModel.selectPodcast(null)
    }
  }
  BackHandler(
      enabled =
          fullPlayer ||
              selectedEpisode != null ||
              selectedArticleId != null ||
              destination == AppTab.Podcasts && state.selectedPodcastId != null,
      onBack = back,
  )
  val content: @Composable () -> Unit = {
    when {
      fullPlayer && nowPlaying != null -> {
        val playing = requireNotNull(nowPlaying)
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
              destination = AppTab.Podcasts
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
            onSaveToCloudMemos =
                if (cloudMemos.isConfigured && !cloudMemos.isBusy) {
                  {
                    viewModel.saveEpisodeToCloudMemos(
                        episode,
                        state.podcasts.firstOrNull { it.id == episode.podcastId }?.title,
                    )
                  }
                } else {
                  null
                },
        )
      }
      selectedArticle != null ->
          ArticleReaderScreen(
              article = selectedArticle,
              feedTitle = state.articleFeeds.firstOrNull { it.id == selectedArticle.feedId }?.title,
              setRead = viewModel::setArticleRead,
              toggleFavorite = viewModel::toggleArticleFavorite,
              saveToCloudMemos =
                  if (cloudMemos.isConfigured && !cloudMemos.isBusy) {
                    {
                      viewModel.saveArticleToCloudMemos(
                          selectedArticle,
                          state.articleFeeds.firstOrNull { it.id == selectedArticle.feedId }?.title,
                      )
                    }
                  } else {
                    null
                  },
              onBack = { selectedArticleId = null },
          )
      destination == AppTab.Podcasts ->
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
              requestMarkAllPlayed = viewModel::requestPodcastMarkAllPlayed,
              bulkActionBusy = bulkActions.isBusy,
              openSettings = { destination = AppTab.Settings },
          )
      destination == AppTab.Library ->
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
      destination == AppTab.Reader ->
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
              requestMarkAllRead = viewModel::requestArticlesMarkAllRead,
              bulkActionBusy = bulkActions.isBusy,
          )
      destination == AppTab.Memos ->
          MemosScreen(
              state = memos,
              isConfigured = cloudMemos.isConfigured,
              openSettings = { destination = AppTab.Settings },
              composerActions = memosComposerActions,
              listActions = memosListActions,
              shareActions = memosShareActions,
              manageActions = memosManageActions,
          )
      else ->
          SettingsScreen(
              theme = theme,
              dynamicColor = dynamic,
              wifiOnlyDownloads = wifiOnlyDownloads,
              cloudMemos = cloudMemos,
              setTheme = viewModel::setAppTheme,
              setDynamicColor = viewModel::setDynamicColor,
              setWifiOnlyDownloads = viewModel::setWifiOnlyDownloads,
              showQueue = { showQueue = true },
              add = { url, onSuccess -> viewModel.addFeed(url, onSuccess) },
              importOpml = viewModel::importOpml,
              exportOpml = viewModel::exportOpml,
              configureCloudMemos = viewModel::configureCloudMemos,
              disconnectCloudMemos = viewModel::disconnectCloudMemos,
              tabOrder = tabOrder,
              enabledTabs = enabledTabs,
              moveTab = viewModel::moveTab,
              setTabEnabled = viewModel::setTabEnabled,
          )
    }
  }
  Scaffold(
      topBar = {
        HomeTopBar(
            show =
                fullPlayer ||
                    selectedEpisode != null ||
                    !wide && destination == AppTab.Podcasts && state.selectedPodcastId != null,
            fullPlayer = fullPlayer,
            selectedEpisode = selectedEpisode,
            selectedPodcastId = state.selectedPodcastId,
            selectedPodcastUnplayedCount =
                state.selectedPodcastId?.let { state.unplayedEpisodeCounts[it] } ?: 0,
            bulkActionBusy = bulkActions.isBusy,
            onRequestPodcastMarkAllPlayed = viewModel::requestPodcastMarkAllPlayed,
            onBack = back,
            onShowQueue = { showQueue = true },
        )
      },
      bottomBar = {
        HomeBottomBar(
            visible = !wide && !fullPlayer && selectedArticleId == null,
            nowPlaying = nowPlaying,
            destination = destination,
            tabOrder = visibleTabs,
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
        if (wide && selectedArticleId == null && !fullPlayer)
            NavigationRail {
              visibleTabs.forEach { item ->
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
        playbackStatus = nowPlaying?.takeIf { it.episode.id == queue.currentEpisodeId }?.status,
        onDismiss = { showQueue = false },
        onClear = { confirmClearQueue = true },
        onOpenEpisode = {
          selectedEpisodeId = it.id
          showQueue = false
        },
        onPlay = viewModel::playQueueItem,
        onTogglePlayback = viewModel::togglePlayback,
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
  BulkMarkDialog(
      request = bulkActions.pendingRequest,
      isBusy = bulkActions.isBusy,
      onConfirm = viewModel::confirmBulkMark,
      onDismiss = viewModel::dismissBulkMarkRequest,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    show: Boolean,
    fullPlayer: Boolean,
    selectedEpisode: EpisodeEntity?,
    selectedPodcastId: String?,
    selectedPodcastUnplayedCount: Int,
    bulkActionBusy: Boolean,
    onRequestPodcastMarkAllPlayed: (String) -> Unit,
    onBack: () -> Unit,
    onShowQueue: () -> Unit,
) {
  if (!show) return
  var podcastActionsExpanded by remember(selectedPodcastId) { mutableStateOf(false) }
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
        if (fullPlayer) {
          IconButton(onClick = onShowQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue))
          }
        } else if (selectedEpisode == null && selectedPodcastId != null) {
          Box {
            IconButton(onClick = { podcastActionsExpanded = true }) {
              Icon(Icons.Filled.MoreVert, stringResource(R.string.subscription_actions))
            }
            DropdownMenu(
                expanded = podcastActionsExpanded,
                onDismissRequest = { podcastActionsExpanded = false },
            ) {
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.mark_all_episodes_played)) },
                  leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                  enabled = selectedPodcastUnplayedCount > 0 && !bulkActionBusy,
                  onClick = {
                    podcastActionsExpanded = false
                    onRequestPodcastMarkAllPlayed(selectedPodcastId)
                  },
              )
            }
          }
        }
      },
  )
}

@Composable
private fun BulkMarkDialog(
    request: BulkMarkRequest?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  request ?: return
  val isPodcast = request is BulkMarkRequest.Podcast
  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
            stringResource(
                if (isPodcast) R.string.mark_all_episodes_played_title
                else R.string.mark_all_articles_read_title
            )
        )
      },
      text = {
        Text(
            when (request) {
              is BulkMarkRequest.Podcast ->
                  pluralStringResource(
                      R.plurals.mark_all_episodes_played_message,
                      request.count,
                      request.count,
                      request.podcastTitle,
                  )
              is BulkMarkRequest.Articles ->
                  if (request.feedTitle == null) {
                    pluralStringResource(
                        R.plurals.mark_all_articles_read_message,
                        request.count,
                        request.count,
                    )
                  } else {
                    pluralStringResource(
                        R.plurals.mark_feed_read_message,
                        request.count,
                        request.count,
                        request.feedTitle,
                    )
                  }
            }
        )
      },
      confirmButton = {
        TextButton(onClick = onConfirm, enabled = !isBusy) {
          Text(
              stringResource(
                  if (isPodcast) R.string.mark_all_episodes_played else R.string.mark_as_read
              )
          )
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss, enabled = !isBusy) {
          Text(stringResource(R.string.cancel))
        }
      },
  )
}

@Composable
private fun HomeBottomBar(
    visible: Boolean,
    nowPlaying: NowPlaying?,
    destination: AppTab,
    tabOrder: List<AppTab>,
    onDestinationSelected: (AppTab) -> Unit,
    onToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    onShowSpeedPicker: () -> Unit,
) {
  if (!visible) return
  Column {
    nowPlaying?.let { MiniPlayer(it, onToggle, onOpenPlayer, onShowSpeedPicker) }
    NavigationBar {
      tabOrder.forEach { item ->
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

private fun copyMemoMarkdown(context: Context, memo: CloudMemo) {
  val clip = ClipData.newPlainText(context.getString(R.string.cloud_memo_markdown), memo.content)
  if (memo.visibility == CloudMemoVisibility.Private) {
    clip.description.extras =
        PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
  }
  context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private fun shareText(context: Context, text: String, chooserTitle: String): Boolean =
    try {
      val intent =
          Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
          }
      context.startActivity(Intent.createChooser(intent, chooserTitle))
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: SecurityException) {
      false
    }

@Composable
private fun DestinationIcon(destination: AppTab) =
    when (destination) {
      AppTab.Podcasts -> Icon(Icons.Filled.RssFeed, null)
      AppTab.Reader -> Icon(Icons.AutoMirrored.Filled.Article, null)
      AppTab.Library -> Icon(Icons.Filled.LibraryMusic, null)
      AppTab.Memos -> Icon(Icons.AutoMirrored.Filled.Notes, null)
      AppTab.Settings -> Icon(Icons.Filled.Settings, null)
    }

@Composable
private fun destinationLabel(destination: AppTab): String =
    stringResource(
        when (destination) {
          AppTab.Podcasts -> R.string.podcasts
          AppTab.Reader -> R.string.reader
          AppTab.Library -> R.string.library
          AppTab.Memos -> R.string.memos
          AppTab.Settings -> R.string.settings
        }
    )
