package app.xpod.ui.shell

import androidx.compose.runtime.Composable
import app.xpod.data.AppTab
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.CloudMemosConnection
import app.xpod.data.DownloadState
import app.xpod.data.EpisodeEntity
import app.xpod.data.LocalTrackEntity
import app.xpod.data.LocalVideoEntity
import app.xpod.data.ReadingPreferences
import app.xpod.data.ThemeMode
import app.xpod.playback.NowPlaying
import app.xpod.ui.books.BookReaderScreen
import app.xpod.ui.books.BooksScreen
import app.xpod.ui.books.BooksUiState
import app.xpod.ui.books.BooksViewModel
import app.xpod.ui.coordination.CloudMemosUiState
import app.xpod.ui.memos.MemosComposerActions
import app.xpod.ui.memos.MemosListActions
import app.xpod.ui.memos.MemosManageActions
import app.xpod.ui.memos.MemosScreen
import app.xpod.ui.memos.MemosShareActions
import app.xpod.ui.memos.MemosUiState
import app.xpod.ui.music.MusicScreen
import app.xpod.ui.music.MusicUiState
import app.xpod.ui.music.MusicViewModel
import app.xpod.ui.navigation.AppRoute
import app.xpod.ui.navigation.PodcastSubView
import app.xpod.ui.notes.NoteEditorScreen
import app.xpod.ui.notes.NoteEditorUiState
import app.xpod.ui.notes.NotesLoadingScreen
import app.xpod.ui.notes.NotesScreen
import app.xpod.ui.notes.NotesUiState
import app.xpod.ui.notes.NotesViewModel
import app.xpod.ui.player.FullPlayerScreen
import app.xpod.ui.podcasts.EpisodeDetailScreen
import app.xpod.ui.podcasts.PodcastHubActions
import app.xpod.ui.podcasts.PodcastHubScreen
import app.xpod.ui.reader.ArticleReaderScreen
import app.xpod.ui.reader.ReaderScreen
import app.xpod.ui.settings.SettingsScreen
import app.xpod.ui.settings.SettingsViewModel
import app.xpod.ui.video.VideoPlayerScreen
import app.xpod.ui.video.VideoScreen
import app.xpod.ui.video.VideoUiState
import app.xpod.ui.video.VideoViewModel

internal data class XpodRouteUiState(
    val destination: AppRoute,
    val wide: Boolean,
    val main: MainUiState,
    val articleSummaries: Map<String, String>,
    val nowPlaying: NowPlaying?,
    val downloadStates: Map<String, DownloadState>,
    val cloudMemos: CloudMemosUiState,
    val memos: MemosUiState,
    val memosConnection: CloudMemosConnection,
    val memosReloadToken: Int,
    val notes: NotesUiState,
    val noteEditor: NoteEditorUiState?,
    val music: MusicUiState,
    val video: VideoUiState,
    val books: BooksUiState,
    val bulkActionBusy: Boolean,
    val selectedPodcastId: String?,
    val podcastSubView: PodcastSubView,
    val selectedPodcastEpisodes: List<EpisodeEntity>,
    val selectedPodcastEpisodesLoading: Boolean,
    val selectedEpisode: EpisodeEntity?,
    val selectedArticle: ArticleEntity?,
    val selectedBookId: String?,
    val selectedNoteId: Long?,
    val fullPlayer: Boolean,
    val visibleRoutes: List<AppRoute>,
)

internal fun hasSelectedNoteEditor(selectedNoteId: Long?, editor: NoteEditorUiState?): Boolean =
    selectedNoteId != null && editor?.id == selectedNoteId

internal fun shouldShowNotesLoading(selectedNoteId: Long?, editor: NoteEditorUiState?): Boolean =
    selectedNoteId != null && !hasSelectedNoteEditor(selectedNoteId, editor)

/** Renders the active route while keeping route-specific UI wiring out of the app shell. */
@Composable
internal fun XpodRouteContent(
    ui: XpodRouteUiState,
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    musicViewModel: MusicViewModel,
    videoViewModel: VideoViewModel,
    booksViewModel: BooksViewModel,
    notesViewModel: NotesViewModel,
    podcastHubActions: PodcastHubActions,
    playEpisode: (EpisodeEntity) -> Unit,
    handleDownload: (EpisodeEntity) -> Unit,
    togglePlayback: () -> Unit,
    skipToPrevious: () -> Unit,
    skipToNext: () -> Unit,
    onShowSpeedPicker: () -> Unit,
    onRequestRemoveFailedDownload: (EpisodeEntity) -> Unit,
    onShowQueue: () -> Unit,
    onDeleteArticleFeed: (ArticleFeedEntity) -> Unit,
    onChooseMusicFolder: () -> Unit,
    onStartGlobalMusicScan: () -> Unit,
    onChooseVideoFolder: () -> Unit,
    onStartAutomaticVideoScan: () -> Unit,
    onChooseBooksFolder: () -> Unit,
    onSelectDestination: (AppRoute) -> Unit,
    onOpenReleases: () -> Unit,
    playMusicTrack: (LocalTrackEntity) -> Unit,
    playVideo: (LocalVideoEntity) -> Unit,
    memosComposerActions: MemosComposerActions,
    memosListActions: MemosListActions,
    memosShareActions: MemosShareActions,
    memosManageActions: MemosManageActions,
    onShareMarkdownText: () -> Unit,
    onShareMarkdownFile: () -> Unit,
    theme: ThemeMode,
    dynamic: Boolean,
    wifiOnlyDownloads: Boolean,
    readerPreferences: ReadingPreferences,
    tabOrder: List<AppTab>,
    enabledTabs: Set<AppTab>,
) {
  val state = ui.main
  val nowPlaying = ui.nowPlaying
  when {
    ui.destination == AppRoute.Video && ui.video.playerVideoId != null -> {
      val video = ui.video.videos.firstOrNull { it.id == ui.video.playerVideoId }
      VideoPlayerScreen(
          video = video,
          player = videoViewModel.player,
          playerState = ui.video.player,
          playlist = ui.video.playbackQueue,
          onClose = videoViewModel::closeVideo,
          onTogglePlayback = videoViewModel::togglePlayback,
          onSeekBy = videoViewModel::seekBy,
          onSetSpeed = videoViewModel::setSpeed,
          onSelectVideo = { videoViewModel.openVideo(it, ui.video.playbackQueue) },
      )
    }
    ui.fullPlayer && nowPlaying != null -> {
      val playing = nowPlaying
      FullPlayerScreen(
          nowPlayingFlow = viewModel.nowPlaying,
          podcast = state.podcasts.firstOrNull { it.id == playing.item.sourceId },
          onToggle = togglePlayback,
          onSeek = viewModel::seekTo,
          onSkipBack = { viewModel.seekBy(-10_000L) },
          onSkipForward = { viewModel.seekBy(30_000L) },
          onPrevious = skipToPrevious,
          onNext = skipToNext,
          onShowSpeedPicker = onShowSpeedPicker,
          onOpenPodcast = {
            playing.item.sourceId?.let { podcastId ->
              viewModel.openPodcast(podcastId, ui.visibleRoutes)
            }
          },
      )
    }
    ui.selectedEpisode != null -> {
      val episode = ui.selectedEpisode
      EpisodeDetailScreen(
          episode = episode,
          isPlaying = nowPlaying?.let { it.item.id == episode.id && it.isPlaying } == true,
          onPlay = { playEpisode(episode) },
          onTogglePlayback = togglePlayback,
          onFavorite = { viewModel.toggleFavorite(episode.id) },
          onPlayed = { viewModel.markPlayed(episode.id, !episode.isPlayed) },
          downloadState = ui.downloadStates[episode.id],
          onDownload = { handleDownload(episode) },
          onRequestRemoveFailedDownload = { onRequestRemoveFailedDownload(episode) },
          onPlayNext = { viewModel.playNext(episode) },
          onAddToQueue = { viewModel.addToQueue(episode) },
          onSaveToCloudMemos =
              if (ui.cloudMemos.isConfigured && !ui.cloudMemos.isBusy) {
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
    ui.selectedArticle != null -> {
      val article = ui.selectedArticle
      val feedTitle = state.articleFeeds.firstOrNull { it.id == article.feedId }?.title
      ArticleReaderScreen(
          article = article,
          feedTitle = feedTitle,
          setRead = viewModel::setArticleRead,
          toggleFavorite = viewModel::toggleArticleFavorite,
          saveToCloudMemos =
              if (ui.cloudMemos.isConfigured && !ui.cloudMemos.isBusy) {
                { viewModel.saveArticleToCloudMemos(article, feedTitle) }
              } else {
                null
              },
          onBack = viewModel::navigateBack,
      )
    }
    ui.selectedBookId != null ->
        BookReaderScreen(
            bookId = ui.selectedBookId,
            onBack = viewModel::navigateBack,
        )
    ui.destination == AppRoute.Podcasts ->
        PodcastHubScreen(
            state = state,
            wide = ui.wide,
            selectedPodcastId = ui.selectedPodcastId,
            episodes = ui.selectedPodcastEpisodes,
            episodesLoading = ui.selectedPodcastEpisodesLoading,
            subView = ui.podcastSubView,
            onSubViewSelected = viewModel::selectPodcastSubView,
            nowPlaying = nowPlaying,
            downloadStates = ui.downloadStates,
            bulkActionBusy = ui.bulkActionBusy,
            actions = podcastHubActions,
        )
    ui.destination == AppRoute.Reader ->
        ReaderScreen(
            state = state,
            summaries = ui.articleSummaries,
            refresh = viewModel::refreshArticles,
            openArticle = { article ->
              viewModel.markArticleRead(article.id)
              viewModel.openArticle(article.id)
            },
            setRead = viewModel::setArticleRead,
            toggleFavorite = viewModel::toggleArticleFavorite,
            delete = onDeleteArticleFeed,
            requestMarkAllRead = viewModel::requestArticlesMarkAllRead,
            bulkActionBusy = ui.bulkActionBusy,
        )
    ui.destination == AppRoute.Music ->
        MusicScreen(
            state = ui.music,
            nowPlaying = nowPlaying,
            chooseFolder = onChooseMusicFolder,
            startGlobalScan = onStartGlobalMusicScan,
            refresh = musicViewModel::refreshLocalMusic,
            cancelScan = musicViewModel::cancelLocalMusicScan,
            setQuery = musicViewModel::setMusicQuery,
            openFolder = musicViewModel::openMusicFolder,
            play = playMusicTrack,
            togglePlayback = togglePlayback,
            playNext = musicViewModel::playMusicNext,
            addToQueue = musicViewModel::addMusicToQueue,
        )
    ui.destination == AppRoute.Video ->
        VideoScreen(
            state = ui.video,
            chooseFolder = onChooseVideoFolder,
            startAutomaticScan = onStartAutomaticVideoScan,
            refresh = videoViewModel::refresh,
            cancelScan = videoViewModel::cancelScan,
            setQuery = videoViewModel::setQuery,
            openFolder = videoViewModel::openFolder,
            play = playVideo,
            renameVideo = videoViewModel::renameVideo,
            deleteVideo = videoViewModel::deleteVideo,
            deleteFolder = videoViewModel::deleteFolder,
        )
    ui.destination == AppRoute.Books ->
        BooksScreen(
            state = ui.books,
            chooseFolder = onChooseBooksFolder,
            refresh = booksViewModel::refresh,
            cancelScan = booksViewModel::cancelScan,
            setQuery = booksViewModel::setQuery,
            setFilter = booksViewModel::setFilter,
            setSort = booksViewModel::setSort,
            toggleFavorite = booksViewModel::toggleFavorite,
            openBook = viewModel::openBook,
        )
    ui.destination == AppRoute.Memos ->
        MemosScreen(
            state = ui.memos,
            isConfigured = ui.memosConnection.isConfigured,
            accountVersion = ui.memosReloadToken,
            openSettings = { onSelectDestination(AppRoute.Settings) },
            composerActions = memosComposerActions,
            listActions = memosListActions,
            shareActions = memosShareActions,
            manageActions = memosManageActions,
        )
    ui.destination == AppRoute.Notes && hasSelectedNoteEditor(ui.selectedNoteId, ui.noteEditor) ->
        NoteEditorScreen(
            editor = requireNotNull(ui.noteEditor),
            appTheme = theme,
            onBack = viewModel::navigateBack,
            onTitleChanged = notesViewModel::setTitle,
            onContentChanged = notesViewModel::setContent,
            onThemeChanged = notesViewModel::setTheme,
            customThemes = ui.notes.customThemes,
            onCustomThemeSelected = notesViewModel::setCustomTheme,
            onImportTheme = notesViewModel::importCustomTheme,
            onExportTheme = notesViewModel::exportCustomTheme,
            onExportMarkdown = {
              notesViewModel.exportMarkdown(requireNotNull(ui.selectedNoteId), it)
            },
            onExportHtml = { uri, theme ->
              notesViewModel.exportHtml(requireNotNull(ui.selectedNoteId), uri, theme)
            },
            onExportPdf = { uri, theme ->
              notesViewModel.exportPdf(requireNotNull(ui.selectedNoteId), uri, theme)
            },
            cloudMemosBusy = ui.cloudMemos.isBusy,
            onSaveToCloudMemos = {
              val editor = requireNotNull(ui.noteEditor)
              viewModel.saveMarkdownNoteToCloudMemos(editor.title, editor.content)
            },
            onShareMarkdownText = onShareMarkdownText,
            onShareMarkdownFile = onShareMarkdownFile,
            onAttachImage = notesViewModel::attachImage,
            onImageInsertionConsumed = notesViewModel::consumeImageInsertion,
            onFlush = notesViewModel::flushEditor,
            isExporting = ui.notes.isExporting,
            showBackupHint = ui.notes.showBackupHint,
            onDismissBackupHint = notesViewModel::dismissBackupHint,
        )
    ui.destination == AppRoute.Notes && shouldShowNotesLoading(ui.selectedNoteId, ui.noteEditor) ->
        NotesLoadingScreen()
    ui.destination == AppRoute.Notes ->
        NotesScreen(
            state = ui.notes,
            onQueryChanged = notesViewModel::setQuery,
            onCreate = { notesViewModel.createNote(viewModel::openNote) },
            onOpenNote = viewModel::openNote,
            onDeleteNote = notesViewModel::deleteNote,
            onSortChanged = notesViewModel::setSort,
            onImportMarkdown = { notesViewModel.importMarkdown(it, viewModel::openNote) },
            onExportZip = notesViewModel::exportZip,
        )
    else ->
        SettingsScreen(
            theme = theme,
            dynamicColor = dynamic,
            wifiOnlyDownloads = wifiOnlyDownloads,
            readingPreferences = readerPreferences,
            cloudMemos = ui.cloudMemos,
            setTheme = settingsViewModel::setAppTheme,
            setDynamicColor = settingsViewModel::setDynamicColor,
            setWifiOnlyDownloads = settingsViewModel::setWifiOnlyDownloads,
            setReadingFontSize = settingsViewModel::setReadingFontSize,
            setReadingLineHeight = settingsViewModel::setReadingLineHeight,
            setReadingTheme = settingsViewModel::setReadingTheme,
            showQueue = onShowQueue,
            add = { url, onComplete -> viewModel.addFeed(url, onComplete) },
            importOpml = viewModel::importOpml,
            exportOpml = viewModel::exportOpml,
            exportNotesZip = notesViewModel::exportZip,
            configureCloudMemos = viewModel::configureCloudMemos,
            disconnectCloudMemos = viewModel::disconnectCloudMemos,
            openReleases = onOpenReleases,
            tabOrder = tabOrder,
            enabledTabs = enabledTabs,
            moveTab = settingsViewModel::moveTab,
            setTabEnabled = settingsViewModel::setTabEnabled,
        )
  }
}
