package com.selecttube.app.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.domain.Channel
import com.selecttube.app.ui.common.AddToPlaylistSheet
import com.selecttube.app.ui.common.VideoActionBottomSheet
import com.selecttube.app.ui.common.VideoActionTarget
import com.selecttube.app.ui.theme.Brand500
import com.selecttube.app.ui.theme.Brand700
import com.selecttube.app.ui.theme.Brand900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (VideoFeedItem) -> Unit,
    onChannelClick: (String, String) -> Unit,
    onPlayBackground: (VideoActionTarget) -> Unit = {},
    onPlayNext: (VideoActionTarget) -> Unit = {},
    onEnqueue: (VideoActionTarget) -> Unit = {},
    isCurrentlyPlaying: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
        containerColor = Brand900,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand900,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brand700)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFAAAAAA),
                                modifier = Modifier.size(20.dp),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "Search allowed channels...",
                                        color = Color(0xFF888888),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                BasicTextField(
                                    value = state.query,
                                    onValueChange = viewModel::onQueryChange,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF2979FF)),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                            viewModel.onSearchSubmitted()
                                        },
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                )
                            }

                            if (state.query.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearQuery,
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Channel Filter Chips Bar
            if (state.activeChannels.isNotEmpty()) {
                ChannelFilterChipsBar(
                    channels = state.activeChannels,
                    selectedChannelId = state.selectedChannelId,
                    onChannelSelected = viewModel::onChannelFilterSelected,
                )
            }

            // Body content depending on state
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF2979FF),
                                    strokeWidth = 3.dp,
                                )
                                Text(
                                    text = "Searching allowed channels...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFAAAAAA),
                                )
                            }
                        }
                    }

                    state.hasSearched && state.results.isEmpty() -> {
                        // Empty search results
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Brand700),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color(0xFFAAAAAA),
                                    )
                                }

                                Text(
                                    text = "No matching videos found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )

                                val filterDesc = if (state.selectedChannelId != null) {
                                    val matchedName = state.activeChannels.firstOrNull { it.channelId == state.selectedChannelId }?.name.orEmpty()
                                    "No videos matching \"${state.query}\" in $matchedName."
                                } else {
                                    "None of your ${state.activeChannels.size} allowed channels have videos matching \"${state.query}\"."
                                }

                                Text(
                                    text = filterDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFAAAAAA),
                                    textAlign = TextAlign.Center,
                                )

                                if (state.selectedChannelId != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(onClick = { viewModel.onChannelFilterSelected(null) }) {
                                        Text(
                                            text = "Search all allowed channels",
                                            color = Color(0xFF2979FF),
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    !state.hasSearched && state.query.isBlank() -> {
                        // Initial landing prompt
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Brand700),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = Color(0xFF2979FF),
                                    )
                                }

                                Text(
                                    text = "Search Allowed Channels",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )

                                Text(
                                    text = "Type keywords or select a channel chip above to find videos strictly from your active allowed channels.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFAAAAAA),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    else -> {
                        // Results list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            item {
                                Text(
                                    text = "${state.results.size} result${if (state.results.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }

                            items(state.results, key = { it.videoId }) { video ->
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
                                SearchResultCard(
                                    video = video,
                                    onVideoClick = { onVideoClick(video) },
                                    onChannelClick = {
                                        onChannelClick(video.channelId, video.channelName)
                                    },
                                    onMoreOptions = { activeActionTarget = target },
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
private fun ChannelFilterChipsBar(
    channels: List<Channel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "All Channels" Chip
        item {
            val isSelected = selectedChannelId == null
            FilterChipItem(
                label = "All Channels",
                isSelected = isSelected,
                avatarUrl = null,
                onClick = { onChannelSelected(null) },
            )
        }

        // Per-channel chips
        items(channels, key = { it.channelId }) { channel ->
            val isSelected = selectedChannelId == channel.channelId
            FilterChipItem(
                label = channel.name,
                isSelected = isSelected,
                avatarUrl = channel.thumbnailUrl,
                onClick = {
                    if (isSelected) onChannelSelected(null) else onChannelSelected(channel.channelId)
                },
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color.White else Brand700,
        contentColor = if (isSelected) Color(0xFF0F0F0F) else Color.White,
        modifier = Modifier
            .height(34.dp)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.White else Color(0xFF26334D),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultCard(
    video: VideoFeedItem,
    onVideoClick: () -> Unit,
    onChannelClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onVideoClick,
                onLongClick = onMoreOptions,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 16:9 Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brand700),
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            // Duration badge at bottom right
            if (video.durationSeconds > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Metadata row (Channel avatar on left, Title & subtitle in center, 3-dots on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Channel Avatar (clickable)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brand500)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 18.dp),
                        onClick = onChannelClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = video.channelName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Channel Name & upload date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = video.channelName,
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onChannelClick,
                        ),
                    )

                    if (!video.uploadDateText.isNullOrBlank()) {
                        Text(
                            text = "•",
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = video.uploadDateText,
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }

            IconButton(
                onClick = onMoreOptions,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
