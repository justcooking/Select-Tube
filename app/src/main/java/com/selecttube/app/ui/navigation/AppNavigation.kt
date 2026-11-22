package com.selecttube.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.playback.PlayQueueItem
import com.selecttube.app.ui.addchannel.AddChannelScreen
import com.selecttube.app.ui.addplaylist.AddPlaylistScreen
import com.selecttube.app.ui.channel.ChannelScreen
import com.selecttube.app.ui.common.VideoActionTarget
import com.selecttube.app.ui.feed.FeedScreen
import com.selecttube.app.ui.onboarding.OnboardingScreen
import com.selecttube.app.ui.player.PlayerScreen
import com.selecttube.app.ui.player.PlayerViewModel
import com.selecttube.app.ui.playlist.LocalPlaylistScreen
import com.selecttube.app.ui.playlist.PlaylistScreen
import com.selecttube.app.ui.search.SearchScreen
import com.selecttube.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navViewModel: AppNavViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val isExpanded by playerViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isOnboardingDone by navViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val appMode by navViewModel.appMode.collectAsStateWithLifecycle()
    val dialogState by navViewModel.dialogState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val startRoute = if (isOnboardingDone == true) Screen.Feed.route else Screen.Onboarding.route
    val currentRoute = navBackStackEntry?.destination?.route ?: startRoute
    val isBottomBarVisible = currentRoute == Screen.Feed.route

    LaunchedEffect(Unit) {
        navViewModel.navCommands.collect { cmd ->
            when (cmd) {
                is NavCommand.PlayVideo -> {
                    playerViewModel.playVideo(
                        urlOrId = cmd.videoId,
                        title = cmd.title,
                        channelName = cmd.channelName,
                        thumbnailUrl = cmd.thumbnailUrl,
                    )
                    playerViewModel.expandPlayer()
                }
                is NavCommand.OpenPlaylist -> {
                    playerViewModel.collapsePlayer()
                    navController.navigate(Screen.Playlist.createRoute(cmd.playlistId, ""))
                }
                is NavCommand.OpenChannel -> {
                    playerViewModel.collapsePlayer()
                    navController.navigate(Screen.Channel.createRoute(cmd.channelId, cmd.channelName))
                }
            }
        }
    }

    if (isOnboardingDone == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val hasVideo = playbackState.playbackInfo != null || playbackState.isResolving

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onOnboardingComplete = {
                            navController.navigate(Screen.AddChannel.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Screen.Feed.route) {
                    FeedScreen(
                        onOpenSettings = { navController.navigate(Screen.Settings.route) },
                        onOpenSearch = { navController.navigate(Screen.Search.route) },
                        onVideoClick = { video ->
                            playerViewModel.playVideo(
                                urlOrId = video.videoId.ifBlank { video.url },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlayBackground = { target ->
                            playerViewModel.playInBackground(
                                urlOrId = target.videoId,
                                title = target.title,
                                channelName = target.channelName,
                                thumbnailUrl = target.thumbnailUrl,
                            )
                        },
                        onPlayNext = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = true,
                            )
                        },
                        onEnqueue = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = false,
                            )
                        },
                        onRecentVideoClick = { recent ->
                            playerViewModel.playVideo(
                                urlOrId = recent.videoId,
                                title = recent.title,
                                channelName = recent.channelName,
                                thumbnailUrl = recent.thumbnailUrl,
                                startPositionMs = recent.lastPositionMs,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlaylistClick = { playlist ->
                            playerViewModel.collapsePlayer()
                            navController.navigate(Screen.Playlist.createRoute(playlist.playlistId, playlist.name))
                        },
                        onLocalPlaylistClick = { localPlaylist ->
                            playerViewModel.collapsePlayer()
                            navController.navigate(Screen.LocalPlaylist.createRoute(localPlaylist.id, localPlaylist.name))
                        },
                        onAddPlaylist = { navController.navigate(Screen.AddPlaylist.route) },
                        isCurrentlyPlaying = hasVideo,
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            playerViewModel.playVideo(
                                urlOrId = video.videoId.ifBlank { video.url },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlayBackground = { target ->
                            playerViewModel.playInBackground(
                                urlOrId = target.videoId,
                                title = target.title,
                                channelName = target.channelName,
                                thumbnailUrl = target.thumbnailUrl,
                            )
                        },
                        onPlayNext = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = true,
                            )
                        },
                        onEnqueue = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = false,
                            )
                        },
                        onChannelClick = { channelId, channelName ->
                            playerViewModel.collapsePlayer()
                            navController.navigate(Screen.Channel.createRoute(channelId, channelName))
                        },
                        isCurrentlyPlaying = hasVideo,
                    )
                }

                composable(Screen.Player.route) {
                    LaunchedEffect(Unit) {
                        playerViewModel.expandPlayer()
                        navController.popBackStack()
                    }
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onAddChannel = { navController.navigate(Screen.AddChannel.route) },
                        onAddPlaylist = { navController.navigate(Screen.AddPlaylist.route) },
                    )
                }

                composable(Screen.AddChannel.route) {
                    AddChannelScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.AddPlaylist.route) {
                    AddPlaylistScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Screen.Playlist.route,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                        navArgument("playlistName") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    PlaylistScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            playerViewModel.playVideo(
                                urlOrId = video.videoId.ifBlank { video.url },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlayBackground = { target ->
                            playerViewModel.playInBackground(
                                urlOrId = target.videoId,
                                title = target.title,
                                channelName = target.channelName,
                                thumbnailUrl = target.thumbnailUrl,
                            )
                        },
                        onPlayNext = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = true,
                            )
                        },
                        onEnqueue = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = false,
                            )
                        },
                        isCurrentlyPlaying = hasVideo,
                    )
                }

                composable(
                    route = Screen.LocalPlaylist.route,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                        navArgument("name") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    LocalPlaylistScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onPlayVideo = { videoId, title, channelName, thumbnailUrl ->
                            playerViewModel.playVideo(
                                urlOrId = videoId,
                                title = title,
                                channelName = channelName,
                                thumbnailUrl = thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlayAll = { queueItems ->
                            playerViewModel.playQueue(queueItems, startIndex = 0)
                        },
                        onPlayBackground = { videoId, title, channelName, thumbnailUrl ->
                            playerViewModel.playInBackground(
                                urlOrId = videoId,
                                title = title,
                                channelName = channelName,
                                thumbnailUrl = thumbnailUrl,
                            )
                        },
                    )
                }

                composable(
                    route = Screen.Channel.route,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("channelName") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ChannelScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            playerViewModel.playVideo(
                                urlOrId = video.videoId.ifBlank { video.url },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onPlayBackground = { target ->
                            playerViewModel.playInBackground(
                                urlOrId = target.videoId,
                                title = target.title,
                                channelName = target.channelName,
                                thumbnailUrl = target.thumbnailUrl,
                            )
                        },
                        onPlayNext = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = true,
                            )
                        },
                        onEnqueue = { target ->
                            playerViewModel.enqueue(
                                PlayQueueItem(
                                    videoId = target.videoId,
                                    title = target.title,
                                    channelName = target.channelName,
                                    thumbnailUrl = target.thumbnailUrl,
                                    durationSeconds = target.durationSeconds,
                                ),
                                playNext = false,
                            )
                        },
                        onPlaylistClick = { playlist ->
                            playerViewModel.collapsePlayer()
                            navController.navigate(Screen.Playlist.createRoute(playlist.playlistId, playlist.name))
                        },
                        isCurrentlyPlaying = hasVideo,
                    )
                }
            }

            // Single unified, persistent sliding Player sheet
            if (hasVideo) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    bottomBarOffset = if (isBottomBarVisible) 80.dp else 0.dp,
                    onNavigateBack = { playerViewModel.collapsePlayer() },
                    onChannelClick = { channelId, channelName ->
                        playerViewModel.collapsePlayer()
                        navController.navigate(Screen.Channel.createRoute(channelId, channelName))
                    },
                )
            }
        }
    }

    // ── Incoming Link & Share Dialogs ──────────────────────────────────────────
    when (val state = dialogState) {
        is IncomingLinkDialogState.None -> {}
        is IncomingLinkDialogState.Resolving -> {
            AlertDialog(
                onDismissRequest = navViewModel::dismissDialog,
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = navViewModel::dismissDialog) {
                        Text("Cancel")
                    }
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    }
                },
            )
        }
        is IncomingLinkDialogState.ChannelNotAllowed -> {
            AlertDialog(
                onDismissRequest = navViewModel::dismissDialog,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = {
                    Text("Channel Not in Curated List")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.videoTitle != null) {
                            Text(
                                text = state.videoTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.channelThumbnailUrl != null) {
                                AsyncImage(
                                    model = state.channelThumbnailUrl,
                                    contentDescription = state.channelName,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                )
                            }
                            Text(
                                text = "Channel: ${state.channelName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Text(
                            text = if (appMode == AppMode.CHILD)
                                "This channel has not been approved by a parent. Enter your Parent Master Password to approve and add it."
                            else
                                "This channel is not in your curated collection. Would you like to add it?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (appMode == AppMode.CHILD) {
                            OutlinedTextField(
                                value = state.passwordInput,
                                onValueChange = navViewModel::onPasswordChange,
                                label = { Text("Parent Master Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                isError = state.errorMessage != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (state.errorMessage != null) {
                            Text(
                                text = state.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = navViewModel::confirmAddChannel,
                        enabled = !state.isResolvingAdd && (appMode != AppMode.CHILD || state.passwordInput.isNotBlank()),
                    ) {
                        if (state.isResolvingAdd) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Add Channel")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = navViewModel::dismissDialog) {
                        Text("Cancel")
                    }
                },
            )
        }
        is IncomingLinkDialogState.MessageDialog -> {
            AlertDialog(
                onDismissRequest = navViewModel::dismissDialog,
                title = { Text(state.title) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = navViewModel::dismissDialog) {
                        Text("OK")
                    }
                },
            )
        }
    }
}
