package app.xpod.ui.shell

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xpod.R
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.EpisodeEntity
import app.xpod.data.LocalTrackEntity
import app.xpod.data.LocalVideoEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.data.cloudMemoWebUrl
import app.xpod.ui.books.BooksViewModel
import app.xpod.ui.coordination.BulkMarkKind
import app.xpod.ui.memos.MemosComposerActions
import app.xpod.ui.memos.MemosListActions
import app.xpod.ui.memos.MemosManageActions
import app.xpod.ui.memos.MemosShareActions
import app.xpod.ui.memos.MemosViewModel
import app.xpod.ui.music.MusicViewModel
import app.xpod.ui.navigation.AppRoute
import app.xpod.ui.navigation.toAppRoutes
import app.xpod.ui.notes.NotesViewModel
import app.xpod.ui.player.MiniPlaybackSummary
import app.xpod.ui.player.buildPictureInPictureParams
import app.xpod.ui.podcasts.PodcastHubActions
import app.xpod.ui.settings.SettingsViewModel
import app.xpod.ui.shared.StatusSeverity
import app.xpod.ui.shared.XPOD_RELEASES_URL
import app.xpod.ui.shared.copyMemoMarkdown
import app.xpod.ui.shared.openExternalUrl
import app.xpod.ui.shared.shareFile
import app.xpod.ui.shared.shareText
import app.xpod.ui.video.VideoViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun XpodHome(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    theme: ThemeMode,
    dynamic: Boolean,
    readerPreferences: app.xpod.data.ReadingPreferences,
    requestNotificationPermission: () -> Unit,
) {
  val context = LocalContext.current
  val activity = LocalActivity.current
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
  val videoViewModel: VideoViewModel = hiltViewModel()
  val video by videoViewModel.state.collectAsStateWithLifecycle()
  val videoStatus by videoViewModel.status.collectAsStateWithLifecycle()
  val videoDeleteLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        videoViewModel.onDeleteConfirmationResult(it.resultCode == Activity.RESULT_OK)
      }
  val videoRenameLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        videoViewModel.onRenameConfirmationResult(it.resultCode == Activity.RESULT_OK)
      }
  LaunchedEffect(videoViewModel) {
    videoViewModel.videoDeleteRequests.collect { intentSender ->
      videoDeleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }
  }
  LaunchedEffect(videoViewModel) {
    videoViewModel.videoRenameRequests.collect { intentSender ->
      videoRenameLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }
  }
  val booksViewModel: BooksViewModel = hiltViewModel()
  val books by booksViewModel.state.collectAsStateWithLifecycle()
  val booksStatus by booksViewModel.status.collectAsStateWithLifecycle()
  val notesViewModel: NotesViewModel = hiltViewModel()
  val notes by notesViewModel.state.collectAsStateWithLifecycle()
  val noteEditor by notesViewModel.editorState.collectAsStateWithLifecycle()
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
  val selectedNoteId = navigation.selectedNoteId
  val fullPlayer = navigation.fullPlayer
  val pictureInPictureVideo = video.videos.firstOrNull { it.id == video.playerVideoId }
  val pictureInPictureEnabled = video.playerVideoId != null && video.player.isPlaying
  val pictureInPictureWidth = pictureInPictureVideo?.width ?: 16
  val pictureInPictureHeight = pictureInPictureVideo?.height ?: 9
  DisposableEffect(
      activity,
      pictureInPictureEnabled,
      pictureInPictureWidth,
      pictureInPictureHeight,
  ) {
    activity?.setPictureInPictureParams(
        buildPictureInPictureParams(
            autoEnter = pictureInPictureEnabled,
            width = pictureInPictureWidth,
            height = pictureInPictureHeight,
        )
    )
    onDispose {
      activity?.setPictureInPictureParams(
          buildPictureInPictureParams(autoEnter = false, width = 16, height = 9)
      )
    }
  }
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val musicPlaybackSettings by viewModel.musicPlaybackSettings.collectAsStateWithLifecycle()
  val musicFolderPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(musicViewModel::selectMusicFolder)
      }
  val musicAudioPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        musicViewModel.onAudioPermissionResult(granted)
      }
  val requestMusicAudioPermission = {
    musicAudioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
  }
  val videoFolderPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(videoViewModel::selectVideoFolder)
      }
  val videoPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        videoViewModel.onVideoPermissionResult(granted)
      }
  val requestVideoPermission = {
    videoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
  }
  val startAutomaticVideoScan = {
    if (video.hasVideoPermission) videoViewModel.startAutomaticScan() else requestVideoPermission()
  }
  val startGlobalMusicScan = {
    if (music.hasAudioPermission) musicViewModel.startGlobalScan()
    else requestMusicAudioPermission()
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
  LaunchedEffect(selectedNoteId) {
    if (selectedNoteId != null) {
      notesViewModel.openNote(selectedNoteId) { viewModel.clearNoteSelection(selectedNoteId) }
    } else notesViewModel.closeEditor()
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
  val playVideo =
      remember(videoViewModel, video.playbackVideos) {
        val play: (LocalVideoEntity) -> Unit = { videoItem ->
          videoViewModel.openVideo(videoItem.id, video.playbackVideos)
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

  XpodHomeStatusEffects(
      snackbar = snackbar,
      mainStatus = state.status,
      onDismissMainStatus = viewModel::dismissStatus,
      memosStatus = memosStatus,
      onDismissMemosStatus = memosViewModel::dismissStatus,
      musicStatus = musicStatus,
      onDismissMusicStatus = musicViewModel::dismissStatus,
      videoStatus = videoStatus,
      onDismissVideoStatus = videoViewModel::dismissStatus,
      booksStatus = booksStatus,
      onDismissBooksStatus = booksViewModel::dismissStatus,
      notesStatus = notes.status,
      onDismissNotesStatus = notesViewModel::dismissStatus,
  )
  LaunchedEffect(destination) {
    when (destination) {
      AppRoute.Music -> {
        musicViewModel.onMusicScreenVisible()
        if (
            !music.hasAudioPermission &&
                (music.selectedTreeUri == null || music.isGlobalSource) &&
                musicViewModel.shouldRequestAutomaticAudioPermission()
        ) {
          requestMusicAudioPermission()
        }
      }
      AppRoute.Video -> {
        videoViewModel.onVideoScreenVisible()
        if (
            !video.hasVideoPermission &&
                (video.selectedTreeUri == null || video.isGlobalSource) &&
                videoViewModel.shouldRequestAutomaticVideoPermission()
        ) {
          requestVideoPermission()
        }
      }
      else -> Unit
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
  val back: () -> Unit = {
    if (video.playerVideoId != null) videoViewModel.closeVideo() else viewModel.navigateBack()
  }
  BackHandler(
      enabled =
          fullPlayer ||
              video.playerVideoId != null ||
              selectedEpisode != null ||
              selectedArticleId != null ||
              selectedBookId != null ||
              selectedNoteId != null ||
              destination == AppRoute.Podcasts && selectedPodcastId != null,
      onBack = back,
  )
  val contentRouteId: String =
      when {
        video.playerVideoId != null -> "video-player"
        fullPlayer && nowPlaying != null -> "player"
        selectedEpisode != null -> "episode"
        selectedArticleId != null -> "article"
        selectedBookId != null -> "book"
        selectedNoteId != null -> "note:$selectedNoteId"
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
                video = video,
                books = books,
                bulkActionBusy = bulkActions.isBusy,
                selectedPodcastId = selectedPodcastId,
                podcastSubView = navigation.podcast.subView,
                selectedPodcastEpisodes = selectedPodcastEpisodes,
                selectedPodcastEpisodesLoading = selectedPodcastEpisodesLoading,
                selectedEpisode = selectedEpisode,
                selectedArticle = selectedArticle,
                selectedBookId = selectedBookId,
                selectedNoteId = selectedNoteId,
                notes = notes,
                noteEditor = noteEditor,
                fullPlayer = fullPlayer,
                visibleRoutes = visibleRoutes,
            ),
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        musicViewModel = musicViewModel,
        videoViewModel = videoViewModel,
        booksViewModel = booksViewModel,
        notesViewModel = notesViewModel,
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
        onStartGlobalMusicScan = startGlobalMusicScan,
        onChooseVideoFolder = { videoFolderPicker.launch(null) },
        onStartAutomaticVideoScan = startAutomaticVideoScan,
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
        playVideo = playVideo,
        memosComposerActions = memosComposerActions,
        memosListActions = memosListActions,
        memosShareActions = memosShareActions,
        memosManageActions = memosManageActions,
        onShareMarkdownText = {
          selectedNoteId?.let { noteId ->
            notesViewModel.shareMarkdownText(noteId) { markdown ->
              if (!shareText(context, markdown, resources.getString(R.string.share_note))) {
                coroutineScope.launch {
                  snackbar.showXpodSnackbar(
                      resources.getString(R.string.share_note_unavailable),
                      StatusSeverity.Error,
                  )
                }
              }
            }
          }
        },
        onShareMarkdownFile = {
          selectedNoteId?.let { noteId ->
            notesViewModel.prepareMarkdownShareFile(noteId) { uri ->
              if (
                  !shareFile(
                      context,
                      uri,
                      "text/markdown",
                      resources.getString(R.string.share_note),
                  )
              ) {
                coroutineScope.launch {
                  snackbar.showXpodSnackbar(
                      resources.getString(R.string.share_note_unavailable),
                      StatusSeverity.Error,
                  )
                }
              }
            }
          }
        },
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
          video.playerVideoId == null &&
              (fullPlayer ||
                  selectedEpisode != null ||
                  (!wide && destination == AppRoute.Podcasts && selectedPodcastId != null)),
      fullPlayer = fullPlayer,
      selectedEpisode = selectedEpisode,
      selectedPodcastId = selectedPodcastId,
      selectedPodcastUnplayedCount =
          selectedPodcastId?.let { state.unplayedEpisodeCounts[it] } ?: 0,
      bulkActionBusy = bulkActions.isBusy,
      onRequestPodcastMarkAllPlayed = viewModel::requestPodcastMarkAllPlayed,
      onBack = back,
      onShowQueue = { showQueue = true },
      showBottomBar =
          !wide &&
              !fullPlayer &&
              video.playerVideoId == null &&
              selectedArticleId == null &&
              selectedBookId == null &&
              selectedNoteId == null,
      summary = miniSummary,
      destination = destination,
      routes = visibleRoutes,
      onDestinationSelected = selectDestination,
      onToggle = togglePlayback,
      onPrevious = skipToPrevious,
      onNext = skipToNext,
      onOpenPlayer = openFullPlayer,
      onShowSpeedPicker = showSpeedPickerAction,
      showNavigationRail =
          wide &&
              selectedArticleId == null &&
              selectedBookId == null &&
              selectedNoteId == null &&
              !fullPlayer &&
              video.playerVideoId == null,
      immersiveContent = video.playerVideoId != null,
      saveableStateHolder = saveableStateHolder,
      contentRouteId = contentRouteId,
      content = content,
  )
  XpodHomeOverlays(
      nowPlaying = nowPlaying,
      showSpeedPicker = showSpeedPicker,
      onSetPlaybackSpeed = { speed ->
        viewModel.setPlaybackSpeed(speed)
        showSpeedPicker = false
      },
      onDismissSpeedPicker = { showSpeedPicker = false },
      showQueue = showQueue,
      queue = queue,
      musicPlaybackSettings = musicPlaybackSettings,
      onDismissQueue = { showQueue = false },
      onClearQueue = { confirmClearQueue = true },
      onOpenQueueItem = {
        if (it.mediaType == PlaybackMediaType.Podcast) {
          viewModel.openEpisode(it.id)
        } else {
          selectDestination(AppRoute.Music)
        }
        showQueue = false
      },
      onPlayQueueItem = playQueueItem,
      onTogglePlayback = togglePlayback,
      onToggleShuffle = viewModel::toggleMusicShuffle,
      onCycleRepeatMode = viewModel::cycleMusicRepeatMode,
      onMoveQueueItem = viewModel::moveQueueItem,
      onRemoveQueueItem = viewModel::removeFromQueue,
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
      onClearQueueDialog = {
        viewModel.clearQueue()
        confirmClearQueue = false
        showQueue = false
      },
      bulkMarkRequest = bulkActions.pendingRequest,
      bulkActionBusy = bulkActions.isBusy,
      onConfirmBulkMark = viewModel::confirmBulkMark,
      onDismissBulkMark = viewModel::dismissBulkMarkRequest,
  )
}
