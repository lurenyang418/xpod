package app.xpod.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.EpisodeEntity
import app.xpod.data.LocalTrackEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.data.cloudMemoWebUrl
import kotlinx.coroutines.launch

@Composable
fun XpodApp(viewModel: MainViewModel = hiltViewModel()) {
  val settingsViewModel: SettingsViewModel = hiltViewModel()
  val dynamic by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
  val theme by settingsViewModel.appTheme.collectAsStateWithLifecycle()
  val readerPreferences by settingsViewModel.readerPreferences.collectAsStateWithLifecycle()
  val dark =
      when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
  val context = LocalContext.current
  val view = LocalView.current
  val activity = LocalActivity.current
  if (!view.isInEditMode) {
    SideEffect {
      activity?.window?.let { window ->
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
      }
    }
  }
  val notificationPermission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val requestNotificationPermission =
      remember(context) {
        val request: () -> Unit = {
          if (
              ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
          )
              notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        request
      }
  val scheme =
      when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
  }
  MaterialTheme(colorScheme = scheme) {
    XpodHome(
        viewModel,
        settingsViewModel,
        theme,
        dynamic,
        readerPreferences,
        requestNotificationPermission,
    )
  }
}

@Composable
private fun XpodHome(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    theme: ThemeMode,
    dynamic: Boolean,
    readerPreferences: app.xpod.data.ReadingPreferences,
    requestNotificationPermission: () -> Unit,
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val articleSummaries by viewModel.articleSummaries.collectAsStateWithLifecycle()
  // Position-free view: the full player collects the ticking flow itself.
  val nowPlaying by viewModel.nowPlayingDisplay.collectAsStateWithLifecycle()
  val miniSummary =
      remember(nowPlaying) {
        nowPlaying?.let {
          MiniPlaybackSummary(
              mediaType = it.item.mediaType,
              title = it.item.title,
              artworkUri = it.item.artworkUri,
              isPlaying = it.isPlaying,
              speed = it.speed,
          )
        }
      }
  val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
  val wifiOnlyDownloads by settingsViewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
  val cloudMemos by viewModel.cloudMemosState.collectAsStateWithLifecycle()
  val memosViewModel: MemosViewModel = hiltViewModel()
  val memos by memosViewModel.memosState.collectAsStateWithLifecycle()
  val memosConnection by memosViewModel.connection.collectAsStateWithLifecycle()
  val memosReloadToken by memosViewModel.reloadToken.collectAsStateWithLifecycle()
  val memosStatus by memosViewModel.status.collectAsStateWithLifecycle()
  val musicViewModel: MusicViewModel = hiltViewModel()
  val music by musicViewModel.musicState.collectAsStateWithLifecycle()
  val musicStatus by musicViewModel.status.collectAsStateWithLifecycle()
  val booksViewModel: BooksViewModel = hiltViewModel()
  val books by booksViewModel.state.collectAsStateWithLifecycle()
  val booksStatus by booksViewModel.status.collectAsStateWithLifecycle()
  val bulkActions by viewModel.bulkActionsState.collectAsStateWithLifecycle()
  val tabOrder by settingsViewModel.tabOrder.collectAsStateWithLifecycle()
  val enabledTabs by settingsViewModel.enabledTabs.collectAsStateWithLifecycle()
  val visibleTabs = tabOrder.filter(enabledTabs::contains)
  val visibleRoutes = visibleTabs.toAppRoutes()
  val latestVisibleRoutes = rememberUpdatedState(visibleRoutes)
  val navigation by viewModel.navigation.collectAsStateWithLifecycle()
  val podcastSelection by viewModel.podcastSelection.collectAsStateWithLifecycle()
  val destination = navigation.destination
  val selectedPodcastId = navigation.podcast.selectedPodcastId
  val selectedPodcastEpisodes =
      podcastSelection.episodes
          .takeIf { podcastSelection.selectedPodcastId == selectedPodcastId }
          .orEmpty()
  val selectedPodcastEpisodesLoading =
      selectedPodcastId != null &&
          (podcastSelection.selectedPodcastId != selectedPodcastId || podcastSelection.isLoading)
  val selectedEpisodeId = navigation.selectedEpisodeId
  val selectedArticleId = navigation.selectedArticleId
  val selectedBookId = navigation.selectedBookId
  val fullPlayer = navigation.fullPlayer
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val musicPlaybackSettings by viewModel.musicPlaybackSettings.collectAsStateWithLifecycle()
  val musicFolderPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(musicViewModel::selectMusicFolder)
      }
  val booksFolderPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(booksViewModel::selectBookFolder)
      }
  val containerWidth = LocalWindowInfo.current.containerSize.width
  val wide = with(LocalDensity.current) { containerWidth.toDp() >= 600.dp }
  val snackbar = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val memosComposerActions =
      remember(memosViewModel) {
        MemosComposerActions(
            setDraft = memosViewModel::setMemoDraft,
            setVisibility = memosViewModel::setMemoVisibility,
            create = memosViewModel::createMemo,
        )
      }
  val memosListActions =
      remember(memosViewModel) {
        MemosListActions(
            load = memosViewModel::loadMemos,
            refresh = memosViewModel::refreshMemos,
            loadMore = memosViewModel::loadMoreMemos,
            setQuery = memosViewModel::setMemoQuery,
            selectTag = memosViewModel::selectMemoTag,
            search = memosViewModel::searchMemos,
        )
      }
  val memosShareActions =
      remember(
          memosViewModel,
          context,
          resources,
          memosConnection.baseUrl,
          coroutineScope,
          snackbar,
      ) {
        MemosShareActions(
            copyMemo = { memo -> copyMemoMarkdown(context, memo) },
            shareMemoLink = { memo ->
              val url = cloudMemoWebUrl(memosConnection.baseUrl, memo.id)
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
                  snackbar.showXpodSnackbar(
                      resources.getString(R.string.share_unavailable),
                      StatusSeverity.Error,
                  )
                }
              }
            },
            requestPrivateMemoShare = memosViewModel::requestPrivateMemoShare,
            dismissPrivateMemoShare = memosViewModel::dismissPrivateMemoShare,
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
                    snackbar.showXpodSnackbar(
                        resources.getString(R.string.share_unavailable),
                        StatusSeverity.Error,
                    )
                  }
                }
              }
            },
        )
      }
  val memosManageActions =
      remember(memosViewModel) {
        MemosManageActions(
            archiveMemo = memosViewModel::archiveMemo,
            requestDelete = memosViewModel::requestMemoDelete,
            dismissDelete = memosViewModel::dismissMemoDelete,
            moveToTrash = memosViewModel::moveMemoToTrash,
        )
      }
  val selectedEpisode = selectedEpisodeId?.let { id ->
    (selectedPodcastEpisodes + state.libraryEpisodes).firstOrNull { it.id == id }
  }
  val selectedArticle = selectedArticleId?.let { id ->
    state.articles.firstOrNull { article -> article.id == id }
  }
  var podcastToDelete by remember { mutableStateOf<PodcastEntity?>(null) }
  var articleFeedToDelete by remember { mutableStateOf<ArticleFeedEntity?>(null) }
  var downloadToRemove by remember { mutableStateOf<EpisodeEntity?>(null) }
  var showSpeedPicker by rememberSaveable { mutableStateOf(false) }
  var showQueue by rememberSaveable { mutableStateOf(false) }
  var confirmClearQueue by remember { mutableStateOf(false) }

  LaunchedEffect(visibleRoutes, destination) {
    if (destination !in visibleRoutes) {
      viewModel.selectDestination(destination, visibleRoutes)
    }
  }
  LaunchedEffect(destination) {
    if (destination != AppRoute.Memos) {
      memosViewModel.dismissPrivateMemoShare()
      memosViewModel.dismissMemoDelete()
    }
  }
  LaunchedEffect(nowPlaying == null) {
    if (nowPlaying == null) {
      viewModel.closeFullPlayer()
      showSpeedPicker = false
    }
  }
  LaunchedEffect(nowPlaying?.item?.mediaType) {
    if (nowPlaying?.item?.mediaType == PlaybackMediaType.Music) showSpeedPicker = false
  }
  val latestDownloadStates = rememberUpdatedState(downloadStates)
  val handleDownload =
      remember(viewModel, requestNotificationPermission) {
        val download: (EpisodeEntity) -> Unit = { episode ->
          if (latestDownloadStates.value[episode.id]?.isCompleted == true) {
            downloadToRemove = episode
          } else {
            requestNotificationPermission()
            viewModel.download(episode)
          }
        }
        download
      }
  val playEpisode =
      remember(viewModel, requestNotificationPermission) {
        val play: (EpisodeEntity) -> Unit = { episode ->
          requestNotificationPermission()
          viewModel.play(episode)
        }
        play
      }
  val playMusicTrack =
      remember(musicViewModel, requestNotificationPermission, music) {
        val play: (LocalTrackEntity) -> Unit = { track ->
          requestNotificationPermission()
          musicViewModel.playMusic(
              music.playbackTracks,
              track.id,
          )
        }
        play
      }
  val playQueueItem =
      remember(viewModel, requestNotificationPermission) {
        val play: (String) -> Unit = { mediaId ->
          requestNotificationPermission()
          viewModel.playQueueItem(mediaId)
        }
        play
      }
  val togglePlayback =
      remember(viewModel, requestNotificationPermission) {
        val toggle: () -> Unit = {
          requestNotificationPermission()
          viewModel.togglePlayback()
        }
        toggle
      }
  val skipToPrevious =
      remember(viewModel, requestNotificationPermission) {
        val skip: () -> Unit = {
          requestNotificationPermission()
          viewModel.skipToPrevious()
        }
        skip
      }
  val skipToNext =
      remember(viewModel, requestNotificationPermission) {
        val skip: () -> Unit = {
          requestNotificationPermission()
          viewModel.skipToNext()
        }
        skip
      }
  val openFullPlayer = remember {
    val open: () -> Unit = viewModel::openFullPlayer
    open
  }
  val showSpeedPickerAction = remember {
    val show: () -> Unit = { showSpeedPicker = true }
    show
  }
  val selectDestination: (AppRoute) -> Unit = { route ->
    viewModel.selectDestination(route, visibleRoutes)
  }
  val podcastHubActions =
      remember(viewModel, playEpisode, handleDownload, togglePlayback) {
        PodcastHubActions(
            openPodcast = { id -> viewModel.openPodcast(id, latestVisibleRoutes.value) },
            refresh = viewModel::refresh,
            refreshAll = viewModel::refreshAllPodcasts,
            play = playEpisode,
            download = handleDownload,
            requestRemoveFailedDownload = { downloadToRemove = it },
            favorite = viewModel::toggleFavorite,
            played = viewModel::markPlayed,
            openEpisode = { viewModel.openEpisode(it.id) },
            togglePlayback = togglePlayback,
            addToQueue = viewModel::addToQueue,
            showQueue = { showQueue = true },
            delete = { podcastToDelete = it },
            requestMarkAllPlayed = viewModel::requestPodcastMarkAllPlayed,
            openSettings = {
              viewModel.selectDestination(AppRoute.Settings, latestVisibleRoutes.value)
            },
        )
      }

  LaunchedEffect(state.status) {
    state.status?.let {
      snackbar.showXpodSnackbar(it)
      viewModel.dismissStatus()
    }
  }
  LaunchedEffect(memosStatus) {
    memosStatus?.let {
      snackbar.showXpodSnackbar(it)
      memosViewModel.dismissStatus()
    }
  }
  LaunchedEffect(musicStatus) {
    musicStatus?.let {
      snackbar.showXpodSnackbar(it)
      musicViewModel.dismissStatus()
    }
  }
  LaunchedEffect(booksStatus) {
    booksStatus?.let {
      snackbar.showXpodSnackbar(it)
      booksViewModel.dismissStatus()
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
          snackbar.showXpodSnackbar(
              message = resources.getString(R.string.cloud_memo_archived),
              actionLabel = resources.getString(R.string.undo),
              withDismissAction = true,
              duration = SnackbarDuration.Long,
          )
      if (result == SnackbarResult.ActionPerformed) {
        memosViewModel.restoreArchivedMemo(memo.id)
      } else {
        memosViewModel.dismissArchivedMemoUndo(memo.id)
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
          snackbar.showXpodSnackbar(
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
  val back: () -> Unit = viewModel::navigateBack
  BackHandler(
      enabled =
          fullPlayer ||
              selectedEpisode != null ||
              selectedArticleId != null ||
              selectedBookId != null ||
              destination == AppRoute.Podcasts && selectedPodcastId != null,
      onBack = back,
  )
  val contentRouteId: String =
      when {
        fullPlayer && nowPlaying != null -> "player"
        selectedEpisode != null -> "episode"
        selectedArticleId != null -> "article"
        selectedBookId != null -> "book"
        else -> "tab:${destination.name}"
      }
  val saveableStateHolder = rememberSaveableStateHolder()
  val content: @Composable () -> Unit = {
    XpodRouteContent(
        ui =
            XpodRouteUiState(
                destination = destination,
                wide = wide,
                main = state,
                articleSummaries = articleSummaries,
                nowPlaying = nowPlaying,
                downloadStates = downloadStates,
                cloudMemos = cloudMemos,
                memos = memos,
                memosConnection = memosConnection,
                memosReloadToken = memosReloadToken,
                music = music,
                books = books,
                bulkActionBusy = bulkActions.isBusy,
                selectedPodcastId = selectedPodcastId,
                podcastSubView = navigation.podcast.subView,
                selectedPodcastEpisodes = selectedPodcastEpisodes,
                selectedPodcastEpisodesLoading = selectedPodcastEpisodesLoading,
                selectedEpisode = selectedEpisode,
                selectedArticle = selectedArticle,
                selectedBookId = selectedBookId,
                fullPlayer = fullPlayer,
                visibleRoutes = visibleRoutes,
            ),
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        musicViewModel = musicViewModel,
        booksViewModel = booksViewModel,
        podcastHubActions = podcastHubActions,
        playEpisode = playEpisode,
        handleDownload = handleDownload,
        togglePlayback = togglePlayback,
        skipToPrevious = skipToPrevious,
        skipToNext = skipToNext,
        onShowSpeedPicker = { showSpeedPicker = true },
        onRequestRemoveFailedDownload = { downloadToRemove = it },
        onShowQueue = { showQueue = true },
        onDeleteArticleFeed = { articleFeedToDelete = it },
        onChooseMusicFolder = { musicFolderPicker.launch(null) },
        onChooseBooksFolder = { booksFolderPicker.launch(null) },
        onSelectDestination = selectDestination,
        onOpenReleases = {
          if (!openExternalUrl(context, XPOD_RELEASES_URL)) {
            coroutineScope.launch {
              snackbar.showXpodSnackbar(
                  resources.getString(R.string.release_page_unavailable),
                  StatusSeverity.Error,
              )
            }
          }
        },
        playMusicTrack = playMusicTrack,
        memosComposerActions = memosComposerActions,
        memosListActions = memosListActions,
        memosShareActions = memosShareActions,
        memosManageActions = memosManageActions,
        theme = theme,
        dynamic = dynamic,
        wifiOnlyDownloads = wifiOnlyDownloads,
        readerPreferences = readerPreferences,
        tabOrder = tabOrder,
        enabledTabs = enabledTabs,
    )
  }
  XpodHomeScaffold(
      snackbar = snackbar,
      showTopBar =
          fullPlayer ||
              selectedEpisode != null ||
              !wide && destination == AppRoute.Podcasts && selectedPodcastId != null,
      fullPlayer = fullPlayer,
      selectedEpisode = selectedEpisode,
      selectedPodcastId = selectedPodcastId,
      selectedPodcastUnplayedCount = selectedPodcastId?.let { state.unplayedEpisodeCounts[it] } ?: 0,
      bulkActionBusy = bulkActions.isBusy,
      onRequestPodcastMarkAllPlayed = viewModel::requestPodcastMarkAllPlayed,
      onBack = back,
      onShowQueue = { showQueue = true },
      showBottomBar = !wide && !fullPlayer && selectedArticleId == null && selectedBookId == null,
      summary = miniSummary,
      destination = destination,
      routes = visibleRoutes,
      onDestinationSelected = selectDestination,
      onToggle = togglePlayback,
      onPrevious = skipToPrevious,
      onNext = skipToNext,
      onOpenPlayer = openFullPlayer,
      onShowSpeedPicker = showSpeedPickerAction,
      showNavigationRail = wide && selectedArticleId == null && selectedBookId == null && !fullPlayer,
      saveableStateHolder = saveableStateHolder,
      contentRouteId = contentRouteId,
      content = content,
  )
  nowPlaying
      ?.takeIf { showSpeedPicker && it.item.mediaType == PlaybackMediaType.Podcast }
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
        playbackStatus = nowPlaying?.takeIf { it.item.id == queue.currentMediaId }?.status,
        musicPlaybackSettings = musicPlaybackSettings,
        onDismiss = { showQueue = false },
        onClear = { confirmClearQueue = true },
        onOpenItem = {
          if (it.mediaType == PlaybackMediaType.Podcast) {
            viewModel.openEpisode(it.id)
          } else {
            selectDestination(AppRoute.Music)
          }
          showQueue = false
        },
        onPlay = playQueueItem,
        onTogglePlayback = togglePlayback,
        onToggleShuffle = viewModel::toggleMusicShuffle,
        onCycleRepeatMode = viewModel::cycleMusicRepeatMode,
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
