package app.xpod.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import app.xpod.data.EpisodeEntity
import app.xpod.data.DownloadState
import app.xpod.data.DownloadPhase
import app.xpod.data.PodcastEntity
import app.xpod.data.ThemeMode
import app.xpod.playback.NowPlaying
import app.xpod.playback.PlaybackQueue
import coil3.compose.AsyncImage
import app.xpod.R
import java.util.Locale

private enum class Destination { Subscriptions, Library, Settings }
private enum class LibraryFilter { All, Unplayed, Favorites, Downloaded }

@Composable
fun XpodApp(viewModel: MainViewModel = hiltViewModel()) {
    val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val theme by viewModel.appTheme.collectAsStateWithLifecycle()
    val dark = when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) { XpodHome(viewModel, theme, dynamic, requestNotificationPermission) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XpodHome(viewModel: MainViewModel, theme: ThemeMode, dynamic: Boolean, requestNotificationPermission: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(Destination.Subscriptions) }
    var selectedEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEpisode = selectedEpisodeId?.let { id ->
        (state.episodes + state.libraryEpisodes + queue.episodes).firstOrNull { it.id == id }
    }
    var podcastToDelete by remember { mutableStateOf<PodcastEntity?>(null) }
    var downloadToRemove by remember { mutableStateOf<EpisodeEntity?>(null) }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var showSpeedPicker by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    val handleDownload: (EpisodeEntity) -> Unit = { episode ->
        if (downloadStates[episode.id]?.isCompleted == true) downloadToRemove = episode
        else {
            requestNotificationPermission()
            viewModel.download(episode)
        }
    }

    LaunchedEffect(state.status) { state.status?.let { snackbar.showSnackbar(it); viewModel.dismissStatus() } }
    val back: () -> Unit = {
        when {
            fullPlayer -> fullPlayer = false
            selectedEpisode != null -> selectedEpisodeId = null
            destination == Destination.Subscriptions && state.selectedPodcastId != null -> viewModel.selectPodcast(null)
        }
    }
    BackHandler(enabled = fullPlayer || selectedEpisode != null || destination == Destination.Subscriptions && state.selectedPodcastId != null) {
        back()
    }
    val content: @Composable () -> Unit = {
        if (fullPlayer) {
            nowPlaying?.let { playing ->
                FullPlayerScreen(
                    nowPlaying = playing,
                    podcast = state.podcasts.firstOrNull { it.id == playing.episode.podcastId },
                    onToggle = { requestNotificationPermission(); viewModel.togglePlayback() },
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
        } else selectedEpisode?.let { episode ->
            EpisodeDetailScreen(
                episode = episode,
                isPlaying = nowPlaying?.episode?.id == episode.id && nowPlaying?.isPlaying == true,
                onPlay = { requestNotificationPermission(); viewModel.play(episode) },
                onTogglePlayback = { requestNotificationPermission(); viewModel.togglePlayback() },
                onFavorite = {
                    viewModel.toggleFavorite(episode.id)
                },
                onPlayed = {
                    viewModel.markPlayed(episode.id, !episode.isPlayed)
                },
                downloadState = downloadStates[episode.id],
                onDownload = { handleDownload(episode) },
                onPlayNext = { viewModel.playNext(episode) },
                onAddToQueue = { viewModel.addToQueue(episode) },
            )
        } ?: when (destination) {
            Destination.Subscriptions -> SubscriptionScreen(
                state, wide, viewModel::selectPodcast, viewModel::refresh, { requestNotificationPermission(); viewModel.play(it) },
                handleDownload, viewModel::toggleFavorite, viewModel::markPlayed,
                nowPlaying, downloadStates, { selectedEpisodeId = it.id }, { requestNotificationPermission(); viewModel.togglePlayback() }, viewModel::addToQueue, { showQueue = true }, { podcastToDelete = it }, { destination = Destination.Settings },
            )
            Destination.Library -> LibraryScreen(
                state, { requestNotificationPermission(); viewModel.play(it) }, viewModel::toggleFavorite, handleDownload,
                viewModel::markPlayed, nowPlaying, downloadStates, { selectedEpisodeId = it.id }, { requestNotificationPermission(); viewModel.togglePlayback() }, viewModel::addToQueue,
            )
            Destination.Settings -> SettingsScreen(theme, dynamic, wifiOnlyDownloads, viewModel::setAppTheme, viewModel::setDynamicColor, viewModel::setWifiOnlyDownloads, { showQueue = true }, { url, onSuccess -> viewModel.addFeed(url, onSuccess) }, viewModel::importOpml, viewModel::exportOpml)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                show = fullPlayer || selectedEpisode != null || !wide && destination == Destination.Subscriptions && state.selectedPodcastId != null,
                fullPlayer = fullPlayer,
                selectedEpisode = selectedEpisode,
                onBack = back,
                onShowQueue = { showQueue = true },
            )
        },
        snackbarHost = {},
        bottomBar = {
            HomeBottomBar(
                visible = !wide && !fullPlayer,
                nowPlaying = nowPlaying,
                destination = destination,
                onDestinationSelected = { destination = it; selectedEpisodeId = null },
                onToggle = { requestNotificationPermission(); viewModel.togglePlayback() },
                onOpenPlayer = { fullPlayer = true },
                onShowSpeedPicker = { showSpeedPicker = true },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxSize()) {
                if (wide) NavigationRail { Destination.entries.forEach { item ->
                    NavigationRailItem(selected = item == destination, onClick = { destination = item; selectedEpisodeId = null }, icon = { DestinationIcon(item) }, label = { Text(destinationLabel(item)) })
                } }
                content()
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
    nowPlaying?.takeIf { showSpeedPicker }?.let { playing ->
        SpeedPicker(playing.speed, onSelect = { viewModel.setPlaybackSpeed(it); showSpeedPicker = false }, onDismiss = { showSpeedPicker = false })
    }
    if (showQueue) {
        QueueSheet(
            queue,
            onDismiss = { showQueue = false },
            onClear = { confirmClearQueue = true },
            onOpenEpisode = { selectedEpisodeId = it.id; showQueue = false },
            onPlay = viewModel::playQueueItem,
            onMove = viewModel::moveQueueItem,
            onRemove = viewModel::removeFromQueue,
        )
    }
    HomeDialogs(
        podcastToDelete = podcastToDelete,
        downloadToRemove = downloadToRemove,
        confirmClearQueue = confirmClearQueue,
        onDismissPodcastDelete = { podcastToDelete = null },
        onRemovePodcast = { viewModel.removePodcast(it); podcastToDelete = null },
        onDismissDownloadRemove = { downloadToRemove = null },
        onRemoveDownload = { viewModel.removeDownload(it); downloadToRemove = null },
        onDismissClearQueue = { confirmClearQueue = false },
        onClearQueue = { viewModel.clearQueue(); confirmClearQueue = false; showQueue = false },
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
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        actions = {
            if (fullPlayer) IconButton(onClick = onShowQueue) {
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
        NavigationBar { Destination.entries.forEach { item ->
            NavigationBarItem(selected = item == destination, onClick = { onDestinationSelected(item) }, icon = { DestinationIcon(item) }, label = { Text(destinationLabel(item)) })
        } }
    }
}

@Composable
private fun HomeDialogs(
    podcastToDelete: PodcastEntity?,
    downloadToRemove: EpisodeEntity?,
    confirmClearQueue: Boolean,
    onDismissPodcastDelete: () -> Unit,
    onRemovePodcast: (String) -> Unit,
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
            confirmButton = { TextButton(onClick = { onRemovePodcast(podcast.id) }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = onDismissPodcastDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
    downloadToRemove?.let { episode ->
        AlertDialog(
            onDismissRequest = onDismissDownloadRemove,
            title = { Text(stringResource(R.string.remove_download_title)) },
            text = { Text(stringResource(R.string.remove_download_message, episode.title)) },
            confirmButton = { TextButton(onClick = { onRemoveDownload(episode.id) }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = onDismissDownloadRemove) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClearQueue) {
        AlertDialog(
            onDismissRequest = onDismissClearQueue,
            title = { Text(stringResource(R.string.clear_queue_title)) },
            text = { Text(stringResource(R.string.clear_queue_message)) },
            confirmButton = { TextButton(onClick = onClearQueue) { Text(stringResource(R.string.clear_queue)) } },
            dismissButton = { TextButton(onClick = onDismissClearQueue) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DestinationIcon(destination: Destination) = when (destination) {
    Destination.Subscriptions -> Icon(Icons.Filled.RssFeed, null)
    Destination.Library -> Icon(Icons.Filled.LibraryMusic, null)
    Destination.Settings -> Icon(Icons.Filled.Settings, null)
}

@Composable
private fun destinationLabel(destination: Destination): String = stringResource(when (destination) {
    Destination.Subscriptions -> R.string.subscriptions
    Destination.Library -> R.string.library
    Destination.Settings -> R.string.settings
})

@Composable
private fun MiniPlayer(nowPlaying: NowPlaying, onToggle: () -> Unit, onOpen: () -> Unit, onShowSpeedPicker: () -> Unit) = Surface(
    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onOpen).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(nowPlaying.episode.artworkUrl, null, Modifier.size(40.dp))
        Text(nowPlaying.episode.title, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onShowSpeedPicker) { Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelMedium) }
        IconButton(onClick = onToggle) {
            Icon(if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPicker(selected: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) = ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.playback_speed), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed -> FilterChip(selected = selected == speed, onClick = { onSelect(speed) }, label = { Text(speedLabel(speed)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.75f, 2f).forEach { speed -> FilterChip(selected = selected == speed, onClick = { onSelect(speed) }, label = { Text(speedLabel(speed)) }) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FullPlayerScreen(nowPlaying: NowPlaying, podcast: PodcastEntity?, onToggle: () -> Unit, onSeek: (Long) -> Unit, onSkipBack: () -> Unit, onSkipForward: () -> Unit, onShowSpeedPicker: () -> Unit, onOpenPodcast: () -> Unit) {
    val duration = nowPlaying.durationMs.coerceAtLeast(1L)
    var scrubPosition by remember(nowPlaying.episode.id) { mutableStateOf(nowPlaying.positionMs.toFloat()) }
    var isScrubbing by remember(nowPlaying.episode.id) { mutableStateOf(false) }
    LaunchedEffect(nowPlaying.positionMs, nowPlaying.durationMs) {
        if (!isScrubbing) scrubPosition = nowPlaying.positionMs.coerceIn(0L, duration).toFloat()
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Artwork(nowPlaying.episode.artworkUrl ?: podcast?.artworkUrl, null, Modifier.size(200.dp))
        Spacer(Modifier.height(36.dp))
        Text(nowPlaying.episode.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        podcast?.let { Text(it.title, Modifier.clickable(onClick = onOpenPodcast), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(8.dp))
        Text(nowPlaying.episode.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Slider(
            value = scrubPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { isScrubbing = true; scrubPosition = it },
            onValueChangeFinished = { onSeek(scrubPosition.toLong()); isScrubbing = false },
            valueRange = 0f..duration.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel(scrubPosition.toLong()), style = MaterialTheme.typography.labelMedium)
            Text(timeLabel(nowPlaying.durationMs), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconButton(onClick = onShowSpeedPicker) { Text(speedLabel(nowPlaying.speed), style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = onSkipBack) { Icon(Icons.Filled.Replay10, stringResource(R.string.back_10_seconds)) }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) { Icon(if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play), Modifier.size(32.dp)) }
            IconButton(onClick = onSkipForward) { Icon(Icons.Filled.Forward30, stringResource(R.string.forward_30_seconds)) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Artwork(url: String?, contentDescription: String?, modifier: Modifier) = Box(modifier, contentAlignment = Alignment.Center) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.secondaryContainer) {}
    Icon(Icons.Filled.RssFeed, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

private fun speedLabel(speed: Float): String = if (speed % 1f == 0f) String.format(Locale.US, "%.0fx", speed) else String.format(Locale.US, "%.2gx", speed)

private fun timeLabel(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun SubscriptionScreen(state: MainUiState, wide: Boolean, select: (String?) -> Unit, refresh: (String) -> Unit, play: (EpisodeEntity) -> Unit, download: (EpisodeEntity) -> Unit, favorite: (String) -> Unit, played: (String, Boolean) -> Unit, nowPlaying: NowPlaying?, downloadStates: Map<String, DownloadState>, openEpisode: (EpisodeEntity) -> Unit, togglePlayback: () -> Unit, addToQueue: (EpisodeEntity) -> Unit, showQueue: () -> Unit, delete: (PodcastEntity) -> Unit, openSettings: () -> Unit) {
    if (state.podcasts.isEmpty()) {
        EmptySubscriptions(openSettings, Modifier.fillMaxSize())
        return
    }
    if (wide) Row(Modifier.fillMaxSize()) {
        PodcastList(state.podcasts, select, refresh, showQueue, delete, Modifier.weight(0.42f))
        EpisodeList(state.episodes, play, download, favorite, played, nowPlaying, downloadStates, openEpisode, togglePlayback, addToQueue, Modifier.weight(0.58f), showTitle = true)
    } else if (state.selectedPodcastId == null) PodcastList(state.podcasts, select, refresh, showQueue, delete, Modifier.fillMaxSize())
    else EpisodeList(state.episodes, play, download, favorite, played, nowPlaying, downloadStates, openEpisode, togglePlayback, addToQueue, Modifier.fillMaxSize(), showTitle = false)
}

@Composable
private fun EmptySubscriptions(openSettings: () -> Unit, modifier: Modifier) = Column(
    modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
) {
    Icon(Icons.Filled.RssFeed, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.no_subscriptions), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FilledIconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.open_settings)) }
}

@Composable
private fun PodcastList(items: List<PodcastEntity>, select: (String?) -> Unit, refresh: (String) -> Unit, showQueue: () -> Unit, delete: (PodcastEntity) -> Unit, modifier: Modifier) = LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.subscriptions), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = showQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue)) }
        }
    }
    items(items, key = { it.id }) { podcast ->
        Card(onClick = { select(podcast.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(podcast.artworkUrl, null, Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(podcast.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(podcast.author.ifBlank { stringResource(R.string.unknown_author) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { refresh(podcast.feedUrl) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_feed)) }
                IconButton(onClick = { delete(podcast) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove_subscription)) }
            }
        }
    }
}

@Composable
private fun EpisodeList(items: List<EpisodeEntity>, play: (EpisodeEntity) -> Unit, download: (EpisodeEntity) -> Unit, favorite: (String) -> Unit, played: (String, Boolean) -> Unit, nowPlaying: NowPlaying?, downloadStates: Map<String, DownloadState>, openEpisode: (EpisodeEntity) -> Unit, togglePlayback: () -> Unit, addToQueue: (EpisodeEntity) -> Unit, modifier: Modifier, showTitle: Boolean) = LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (showTitle) item { Text(stringResource(R.string.episodes), style = MaterialTheme.typography.headlineSmall) }
    items(items, key = { it.id }) { episode -> EpisodeCard(episode, play, download, favorite, played, nowPlaying, downloadStates[episode.id], openEpisode, togglePlayback, addToQueue) }
}

@Composable
private fun EpisodeCard(episode: EpisodeEntity, play: (EpisodeEntity) -> Unit, download: (EpisodeEntity) -> Unit, favorite: (String) -> Unit, played: (String, Boolean) -> Unit, nowPlaying: NowPlaying?, downloadState: DownloadState?, openEpisode: (EpisodeEntity) -> Unit, togglePlayback: () -> Unit, addToQueue: (EpisodeEntity) -> Unit) = Card(onClick = { openEpisode(episode) }, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Artwork(episode.artworkUrl, null, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (episode.description.isNotBlank()) Text(episode.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (downloadState != null && !downloadState.isCompleted) {
                    if (downloadState.phase != DownloadPhase.Failed && downloadState.progress != null) LinearProgressIndicator(progress = { downloadState.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    else if (downloadState.phase != DownloadPhase.Failed) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(
                        when (downloadState.phase) {
                            DownloadPhase.WaitingForNetwork -> stringResource(R.string.waiting_for_network)
                            DownloadPhase.Queued -> stringResource(R.string.waiting_for_download)
                            DownloadPhase.Downloading -> stringResource(R.string.downloaded_size, Formatter.formatFileSize(LocalContext.current, downloadState.bytesDownloaded))
                            DownloadPhase.Failed -> stringResource(R.string.download_failed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val active = nowPlaying?.episode?.id == episode.id
            IconButton(onClick = { if (active) togglePlayback() else play(episode) }) { Icon(if (active && nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (active && nowPlaying.isPlaying) R.string.pause else R.string.play)) }
            IconButton(onClick = { favorite(episode.id) }) { Icon(if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(if (episode.isFavorite) R.string.remove_favorite else R.string.favorite)) }
            IconButton(onClick = { played(episode.id, !episode.isPlayed) }) { Icon(if (episode.isPlayed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, stringResource(if (episode.isPlayed) R.string.mark_unplayed else R.string.mark_played)) }
            IconButton(onClick = { download(episode) }) {
                val failed = downloadState?.phase == DownloadPhase.Failed
                val icon = if (downloadState?.isCompleted == true) Icons.Filled.CheckCircle else if (failed) Icons.Filled.Error else if (downloadState != null) Icons.Filled.CloudDownload else Icons.Filled.Download
                val label = if (downloadState?.isCompleted == true) R.string.remove_download else if (failed) R.string.retry_download else if (downloadState != null) R.string.download_in_progress else R.string.download
                Icon(icon, stringResource(label))
            }
            IconButton(onClick = { addToQueue(episode) }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.add_to_queue)) }
        }
    }
}

@Composable
private fun EpisodeDetailScreen(episode: EpisodeEntity, isPlaying: Boolean, onPlay: () -> Unit, onTogglePlayback: () -> Unit, onFavorite: () -> Unit, onPlayed: () -> Unit, downloadState: DownloadState?, onDownload: () -> Unit, onPlayNext: () -> Unit, onAddToQueue: () -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
        Text(episode.title, style = MaterialTheme.typography.headlineSmall)
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = { if (isPlaying) onTogglePlayback() else onPlay() }) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (isPlaying) R.string.pause else R.string.play)) }
            IconButton(onClick = onFavorite) { Icon(if (episode.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(if (episode.isFavorite) R.string.remove_favorite else R.string.favorite)) }
            IconButton(onClick = onPlayed) { Icon(if (episode.isPlayed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, stringResource(if (episode.isPlayed) R.string.mark_unplayed else R.string.mark_played)) }
            IconButton(onClick = onDownload) {
                val failed = downloadState?.phase == DownloadPhase.Failed
                val icon = if (downloadState?.isCompleted == true) Icons.Filled.CheckCircle else if (failed) Icons.Filled.Error else if (downloadState != null) Icons.Filled.CloudDownload else Icons.Filled.Download
                val label = if (downloadState?.isCompleted == true) R.string.remove_download else if (failed) R.string.retry_download else if (downloadState != null) R.string.download_in_progress else R.string.download
                Icon(icon, stringResource(label))
            }
            IconButton(onClick = onPlayNext) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.play_next)) }
            IconButton(onClick = onAddToQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.add_to_queue)) }
        }
    }
    if (episode.description.isNotBlank()) item { Text(episode.description, style = MaterialTheme.typography.bodyLarge) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(queue: PlaybackQueue, onDismiss: () -> Unit, onClear: () -> Unit, onOpenEpisode: (EpisodeEntity) -> Unit, onPlay: (String) -> Unit, onMove: (Int, Int) -> Unit, onRemove: (String) -> Unit) = ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.queue), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClear, enabled = queue.episodes.isNotEmpty()) { Icon(Icons.Filled.Delete, stringResource(R.string.clear_queue)) }
        }
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue.episodes, key = { _, episode -> episode.id }) { index, episode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(episode.artworkUrl, null, Modifier.size(44.dp))
                    Text(episode.title, Modifier.weight(1f).clickable { onOpenEpisode(episode) }.padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onPlay(episode.id) }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.play)) }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.queue)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_up)) },
                                onClick = { onMove(index, index - 1); menuExpanded = false },
                                enabled = index > 0,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_down)) },
                                onClick = { onMove(index, index + 1); menuExpanded = false },
                                enabled = index < queue.episodes.lastIndex,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_from_queue)) },
                                onClick = { onRemove(episode.id); menuExpanded = false },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AddSubscriptionSection(add: (String, () -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_subscription), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.feed_url)) }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.add_feed), Modifier.weight(1f))
            FilledIconButton(onClick = { add(url) { url = "" } }, enabled = url.startsWith("https://")) { Icon(Icons.Filled.Link, stringResource(R.string.add_feed)) }
        }
    }
}

@Composable
private fun OpmlSection(importOpml: (android.net.Uri) -> Unit, exportOpml: (android.net.Uri) -> Unit) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(importOpml) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-opml")) { it?.let(exportOpml) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.import_opml), Modifier.weight(1f))
            IconButton(onClick = { importer.launch(arrayOf("application/x-opml", "text/x-opml", "text/xml", "application/xml")) }) { Icon(Icons.Filled.FileUpload, stringResource(R.string.import_opml)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.export_opml), Modifier.weight(1f))
            IconButton(onClick = { exporter.launch("xpod-subscriptions.opml") }) { Icon(Icons.Filled.FileDownload, stringResource(R.string.export_opml)) }
        }
    }
}

@Composable
private fun LibraryScreen(state: MainUiState, play: (EpisodeEntity) -> Unit, favorite: (String) -> Unit, download: (EpisodeEntity) -> Unit, played: (String, Boolean) -> Unit, nowPlaying: NowPlaying?, downloadStates: Map<String, DownloadState>, openEpisode: (EpisodeEntity) -> Unit, togglePlayback: () -> Unit, addToQueue: (EpisodeEntity) -> Unit) {
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    val episodes = when (filter) {
        LibraryFilter.Unplayed -> state.libraryEpisodes.filterNot { it.isPlayed }
        LibraryFilter.Favorites -> state.libraryEpisodes.filter { it.isFavorite }
        LibraryFilter.Downloaded -> state.libraryEpisodes.filter { downloadStates[it.id]?.isCompleted == true }
        else -> state.libraryEpisodes
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LibraryFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(libraryFilterLabel(item)) }) } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(episodes, key = { it.id }) { EpisodeCard(it, play, download, favorite, played, nowPlaying, downloadStates[it.id], openEpisode, togglePlayback, addToQueue) } }
    }
}

@Composable
private fun libraryFilterLabel(filter: LibraryFilter): String = stringResource(when (filter) {
    LibraryFilter.All -> R.string.all
    LibraryFilter.Unplayed -> R.string.unplayed
    LibraryFilter.Favorites -> R.string.favorites
    LibraryFilter.Downloaded -> R.string.downloaded
})

@Composable
private fun SettingsScreen(theme: ThemeMode, dynamicColor: Boolean, wifiOnlyDownloads: Boolean, setTheme: (ThemeMode) -> Unit, setDynamicColor: (Boolean) -> Unit, setWifiOnlyDownloads: (Boolean) -> Unit, showQueue: () -> Unit, add: (String, () -> Unit) -> Unit, importOpml: (android.net.Uri) -> Unit, exportOpml: (android.net.Uri) -> Unit) = LazyColumn(
    Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) }
    item { SettingsSectionHeader(stringResource(R.string.appearance)) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { option ->
                    FilterChip(selected = theme == option, onClick = { setTheme(option) }, label = { Text(themeLabel(option)) })
                }
            }
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.dynamic_color_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = dynamicColor, onCheckedChange = setDynamicColor)
        }
    }
    item { SettingsSectionHeader(stringResource(R.string.downloads)) }
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wifi_only_downloads), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.wifi_only_downloads_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = wifiOnlyDownloads, onCheckedChange = setWifiOnlyDownloads)
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.queue), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = showQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue)) }
        }
    }
    item { SettingsSectionHeader(stringResource(R.string.subscriptions)) }
    item { AddSubscriptionSection(add) }
    item { SettingsSectionHeader(stringResource(R.string.opml)) }
    item { OpmlSection(importOpml, exportOpml) }
}

@Composable
private fun SettingsSectionHeader(title: String) = Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun themeLabel(theme: ThemeMode): String = stringResource(when (theme) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
})
