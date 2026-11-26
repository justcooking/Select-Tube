package com.selecttube.app.ui.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.ui.common.AddToPlaylistSheet
import com.selecttube.app.ui.common.VideoActionBottomSheet
import com.selecttube.app.ui.common.VideoActionTarget
import com.selecttube.app.ui.theme.Brand700
import com.selecttube.app.ui.theme.Brand900
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (VideoFeedItem) -> Unit,
    onPlayBackground: (VideoActionTarget) -> Unit = {},
    onPlayNext: (VideoActionTarget) -> Unit = {},
    onEnqueue: (VideoActionTarget) -> Unit = {},
    isCurrentlyPlaying: Boolean = false,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()

    var activeActionTarget by remember { mutableStateOf<VideoActionTarget?>(null) }
    var activeAddToPlaylistTarget by remember { mutableStateOf<VideoActionTarget?>(null) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.playlist?.name ?: viewModel.playlistNameParam.ifBlank { "Playlist" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.loadPlaylist(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.playlist == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.playlist == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = state.error ?: "Failed to load playlist",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = { viewModel.loadPlaylist(isRefresh = false) }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        // Playlist Header (YouTube-style hero matching SelectTube navy theme)
                        item(key = "playlist_header") {
                            PlaylistHeaderCard(
                                state = state,
                                onPrimaryAction = {
                                    if (!state.isAllowed) {
                                        viewModel.openAddDialog()
                                    } else if (state.isActive && state.videos.isNotEmpty()) {
                                        onVideoClick(state.videos.first())
                                    }
                                },
                            )
                        }

                        // Status Gating: If pending, show quiet raised cooling-down notice
                        if (!state.isActive && state.remainingCountdown != null) {
                            item(key = "pending_card") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Brand700,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HourglassTop,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Playlist cooling down",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Available in ${state.remainingCountdown}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Video items
                            item(key = "tracklist_header") {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.08f),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Videos",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${state.videos.size}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            itemsIndexed(state.videos, key = { _, v -> v.videoId }) { index, video ->
                                val target = remember(video) {
                                    VideoActionTarget(
                                        videoId = video.videoId.ifBlank { video.url },
                                        title = video.title,
                                        channelName = video.channelName,
                                        channelId = video.channelId.orEmpty(),
                                        thumbnailUrl = video.thumbnailUrl,
                                        durationSeconds = video.durationSeconds,
                                    )
                                }
                                PlaylistTrackItem(
                                    index = index + 1,
                                    video = video,
                                    isInteractive = state.isAllowed && state.isActive,
                                    onClick = {
                                        if (!state.isAllowed) {
                                            viewModel.openAddDialog()
                                        } else {
                                            onVideoClick(video)
                                        }
                                    },
                                    onMoreOptions = {
                                        if (state.isAllowed && state.isActive) {
                                            activeActionTarget = target
                                        } else {
                                            viewModel.openAddDialog()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddDialog,
            title = {
                Text(if (state.appMode == AppMode.CHILD) "Parent Approval Required" else "Add Playlist?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.appMode == AppMode.CHILD)
                            "Enter your Parent Master Password to add \"${state.playlist?.name ?: "this playlist"}\" to your library."
                        else
                            "Add \"${state.playlist?.name ?: "this playlist"}\" to your curated playlists?"
                    )
                    if (state.appMode == AppMode.CHILD) {
                        OutlinedTextField(
                            value = state.passwordInput,
                            onValueChange = viewModel::onPasswordChange,
                            label = { Text("Parent Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = state.addError != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.addError != null) {
                        Text(
                            text = state.addError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAddPlaylist,
                    enabled = !state.isAdding && (state.appMode != AppMode.CHILD || state.passwordInput.isNotBlank()),
                ) {
                    if (state.isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Add Playlist")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAddDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PlaylistHeaderCard(
    state: PlaylistUiState,
    onPrimaryAction: () -> Unit,
) {
    val playlist = state.playlist
    val videoCount = playlist?.videoCount ?: state.videos.size.toLong()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Brand700, Brand900),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // Artwork Banner with YouTube-style video count pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist?.thumbnailUrl != null) {
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }

            if (videoCount > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "$videoCount videos",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = playlist?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = listOfNotNull(
                playlist?.uploaderName?.takeIf { it.isNotBlank() },
                if (videoCount > 0) "$videoCount videos" else null,
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !state.isAllowed && state.playlist != null -> {
                Button(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to Library",
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Brand700,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Preview only. Add this playlist to play videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.isActive -> {
                Button(
                    onClick = onPrimaryAction,
                    enabled = state.videos.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play All",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackItem(
    index: Int,
    video: VideoFeedItem,
    isInteractive: Boolean,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tabular index
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        // Video Thumbnail with duration overlay (120x68 on 4dp grid)
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationSeconds > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                ) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isInteractive) {
            IconButton(
                onClick = onMoreOptions,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}
