package com.selecttube.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.db.LocalPlaylistWithCount
import com.selecttube.app.data.db.WatchHistoryEntity
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.ui.common.AddToPlaylistSheet
import com.selecttube.app.ui.common.VideoActionBottomSheet
import com.selecttube.app.ui.common.VideoActionTarget
import com.selecttube.app.ui.common.VideoItemCard
import com.selecttube.app.ui.update.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onVideoClick: (VideoFeedItem) -> Unit = {},
    onRecentVideoClick: (WatchHistoryEntity) -> Unit = {},
    onPlaylistClick: (PlaylistItemUiState) -> Unit = {},
    onLocalPlaylistClick: (LocalPlaylistWithCount) -> Unit = {},
    onPlayBackground: (VideoActionTarget) -> Unit = {},
    onPlayNext: (VideoActionTarget) -> Unit = {},
    onEnqueue: (VideoActionTarget) -> Unit = {},
    onAddPlaylist: () -> Unit = {},
    isCurrentlyPlaying: Boolean = false,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    var selectedFeedTab by rememberSaveable { mutableIntStateOf(0) }

    var activeActionTarget by remember { mutableStateOf<VideoActionTarget?>(null) }
    var activeAddToPlaylistTarget by remember { mutableStateOf<VideoActionTarget?>(null) }
    var showCreateLocalPlaylistDialog by remember { mutableStateOf(false) }
    var newLocalPlaylistName by remember { mutableStateOf("") }

    val assignedPlaylistIds by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = activeAddToPlaylistTarget?.videoId,
    ) {
        val targetId = activeAddToPlaylistTarget?.videoId
        if (targetId != null) {
            viewModel.observeAssignedPlaylistsForVideo(targetId).collect { value = it }
        } else {
            value = emptyList()
        }
    }

    val watchProgressMap = remember(watchHistory) {
        watchHistory.associate { item ->
            val totalMs = item.durationSeconds * 1000L
            val prog = if (totalMs > 0L) (item.lastPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
            item.videoId to prog
        }
    }

    UpdateDialog(
        state = updateState,
        onDismiss = viewModel::dismissUpdate,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installUpdate,
        onRetry = viewModel::retryUpdate,
    )

    if (showCreateLocalPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateLocalPlaylistDialog = false
                newLocalPlaylistName = ""
            },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newLocalPlaylistName,
                    onValueChange = { newLocalPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newLocalPlaylistName.trim()
                        if (name.isNotBlank()) {
                            viewModel.createLocalPlaylist(name)
                            newLocalPlaylistName = ""
                            showCreateLocalPlaylistDialog = false
                        }
                    },
                    enabled = newLocalPlaylistName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateLocalPlaylistDialog = false
                        newLocalPlaylistName = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    activeActionTarget?.let { target ->
        VideoActionBottomSheet(
            video = target,
            isCurrentlyPlaying = isCurrentlyPlaying,
            onDismiss = { activeActionTarget = null },
            onPlayInPlayer = {
                activeActionTarget = null
                onVideoClick(
                    VideoFeedItem(
                        videoId = target.videoId,
                        title = target.title,
                        channelName = target.channelName,
                        channelId = target.channelId,
                        thumbnailUrl = target.thumbnailUrl,
                        uploadDateText = null,
                        uploadTimestamp = 0L,
                        durationSeconds = target.durationSeconds,
                        url = target.videoId,
                    )
                )
            },
            onPlayInBackground = {
                activeActionTarget = null
                onPlayBackground(target)
            },
            onPlayNext = {
                activeActionTarget = null
                onPlayNext(target)
            },
            onEnqueue = {
                activeActionTarget = null
                onEnqueue(target)
            },
            onAddToPlaylist = {
                val savedTarget = target
                activeActionTarget = null
                activeAddToPlaylistTarget = savedTarget
            },
        )
    }

    activeAddToPlaylistTarget?.let { target ->
        AddToPlaylistSheet(
            video = target,
            playlists = localPlaylists,
            assignedPlaylistIds = assignedPlaylistIds.toSet(),
            onTogglePlaylist = { playlistId ->
                viewModel.toggleVideoInLocalPlaylist(playlistId, target)
            },
            onCreatePlaylist = { name ->
                viewModel.createLocalPlaylist(name, target)
            },
            onDismiss = { activeAddToPlaylistTarget = null },
        )
    }

    val totalPlaylistsCount = localPlaylists.size + state.playlists.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Select Tube") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                NavigationBarItem(
                    selected = selectedFeedTab == 0,
                    onClick = { selectedFeedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Subscriptions,
                            contentDescription = "Channels Feed",
                        )
                    },
                    label = { Text("Feed") },
                )
                NavigationBarItem(
                    selected = selectedFeedTab == 1,
                    onClick = { selectedFeedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Playlists",
                        )
                    },
                    label = {
                        Text(if (totalPlaylistsCount > 0) "Playlists ($totalPlaylistsCount)" else "Playlists")
                    },
                )
                NavigationBarItem(
                    selected = selectedFeedTab == 2,
                    onClick = { selectedFeedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Recents",
                        )
                    },
                    label = {
                        Text(if (watchHistory.isNotEmpty()) "Recents (${watchHistory.size})" else "Recents")
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedFeedTab) {
                0 -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            state.isLoading && state.videos.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            !state.hasActiveChannels -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "No channels selected",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Go to Settings to add channels.\nOnly videos from your selected channels will appear here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            state.videos.isEmpty() && !state.isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    if (state.channelErrors.isNotEmpty()) {
                                        ChannelErrorsSection(errors = state.channelErrors)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Text(
                                        text = "No videos found",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No videos available from your active channels yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                ) {
                                    if (state.channelErrors.isNotEmpty()) {
                                        item(key = "feed_errors") {
                                            ChannelErrorsSection(
                                                errors = state.channelErrors,
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }

                                    items(
                                        items = state.videos,
                                        key = { it.videoId.ifBlank { it.url } },
                                        contentType = { "video_item" },
                                    ) { video ->
                                        val videoKey = video.videoId.ifBlank { video.url }
                                        val target = remember(video) {
                                            VideoActionTarget(
                                                videoId = videoKey,
                                                title = video.title,
                                                channelName = video.channelName,
                                                channelId = video.channelId.orEmpty(),
                                                thumbnailUrl = video.thumbnailUrl,
                                                durationSeconds = video.durationSeconds,
                                            )
                                        }
                                        VideoItemCard(
                                            title = video.title,
                                            channelName = video.channelName,
                                            thumbnailUrl = video.thumbnailUrl,
                                            durationSeconds = video.durationSeconds,
                                            uploadDateText = video.uploadDateText,
                                            watchProgress = watchProgressMap[videoKey] ?: 0f,
                                            onClick = { onVideoClick(video) },
                                            onMoreOptions = { activeActionTarget = target },
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    PlaylistsFeedView(
                        youtubePlaylists = state.playlists,
                        localPlaylists = localPlaylists,
                        onPlaylistClick = onPlaylistClick,
                        onLocalPlaylistClick = onLocalPlaylistClick,
                        onAddPlaylist = onAddPlaylist,
                        onCreateLocalPlaylist = {
                            newLocalPlaylistName = ""
                            showCreateLocalPlaylistDialog = true
                        },
                        onDeleteLocalPlaylist = viewModel::deleteLocalPlaylist,
                    )
                }

                2 -> {
                    RecentsView(
                        history = watchHistory,
                        onVideoClick = onRecentVideoClick,
                        onDeleteItem = viewModel::deleteHistoryItem,
                        onClearHistory = viewModel::clearWatchHistory,
                        onMoreOptions = { target -> activeActionTarget = target },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelErrorsSection(
    errors: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        errors.forEach { (channelName, error) ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Could not load $channelName",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private enum class PlaylistFilter {
    ALL, MY_PLAYLISTS, YOUTUBE
}

@Composable
private fun PlaylistsFeedView(
    youtubePlaylists: List<PlaylistItemUiState>,
    localPlaylists: List<LocalPlaylistWithCount>,
    onPlaylistClick: (PlaylistItemUiState) -> Unit,
    onLocalPlaylistClick: (LocalPlaylistWithCount) -> Unit,
    onAddPlaylist: () -> Unit,
    onCreateLocalPlaylist: () -> Unit,
    onDeleteLocalPlaylist: (String) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(PlaylistFilter.ALL) }
    var playlistToDelete by remember { mutableStateOf<LocalPlaylistWithCount?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist?") },
            text = { Text("Are you sure you want to delete '${playlistToDelete?.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let { onDeleteLocalPlaylist(it.id) }
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val showLocal = selectedFilter == PlaylistFilter.ALL || selectedFilter == PlaylistFilter.MY_PLAYLISTS
    val showYouTube = selectedFilter == PlaylistFilter.ALL || selectedFilter == PlaylistFilter.YOUTUBE
    val hasAny = (showLocal && localPlaylists.isNotEmpty()) || (showYouTube && youtubePlaylists.isNotEmpty())

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedFilter == PlaylistFilter.ALL,
                onClick = { selectedFilter = PlaylistFilter.ALL },
                label = { Text("All (${localPlaylists.size + youtubePlaylists.size})") },
            )
            FilterChip(
                selected = selectedFilter == PlaylistFilter.MY_PLAYLISTS,
                onClick = { selectedFilter = PlaylistFilter.MY_PLAYLISTS },
                label = { Text("My Playlists (${localPlaylists.size})") },
            )
            FilterChip(
                selected = selectedFilter == PlaylistFilter.YOUTUBE,
                onClick = { selectedFilter = PlaylistFilter.YOUTUBE },
                label = { Text("YouTube (${youtubePlaylists.size})") },
            )
        }

        // Action creation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCreateLocalPlaylist,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Playlist")
            }
            OutlinedButton(
                onClick = onAddPlaylist,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("YouTube Playlist")
            }
        }

        if (!hasAny) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when (selectedFilter) {
                            PlaylistFilter.MY_PLAYLISTS -> "No Custom Playlists"
                            PlaylistFilter.YOUTUBE -> "No YouTube Playlists"
                            PlaylistFilter.ALL -> "No Playlists Added"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Create your own custom playlists or import playlists from YouTube to watch without distraction.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showLocal && localPlaylists.isNotEmpty()) {
                    if (selectedFilter == PlaylistFilter.ALL) {
                        item(key = "local_playlists_header") {
                            Text(
                                text = "My Playlists (${localPlaylists.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(localPlaylists, key = { "local_${it.id}" }) { playlist ->
                        LocalPlaylistCard(
                            playlist = playlist,
                            onClick = { onLocalPlaylistClick(playlist) },
                            onDelete = { playlistToDelete = playlist },
                        )
                    }
                }

                if (showYouTube && youtubePlaylists.isNotEmpty()) {
                    if (selectedFilter == PlaylistFilter.ALL) {
                        item(key = "yt_playlists_header") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "YouTube Playlists (${youtubePlaylists.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    items(youtubePlaylists, key = { "yt_${it.playlistId}" }) { playlist ->
                        PlaylistFeedCard(
                            playlist = playlist,
                            onClick = {
                                if (playlist.isActive) {
                                    onPlaylistClick(playlist)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistCard(
    playlist: LocalPlaylistWithCount,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${playlist.videoCount} videos",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Custom Playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Playlist options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistFeedCard(
    playlist: PlaylistItemUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (playlist.thumbnailUrl != null) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }

                // Video count overlay badge
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${playlist.videoCount} videos",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = playlist.uploaderName ?: "Unknown Creator",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!playlist.isActive && playlist.remainingCountdown != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = playlist.remainingCountdown,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
