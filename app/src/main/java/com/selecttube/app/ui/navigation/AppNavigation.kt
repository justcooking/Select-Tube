package com.selecttube.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.selecttube.app.ui.addchannel.AddChannelScreen
import com.selecttube.app.ui.addplaylist.AddPlaylistScreen
import com.selecttube.app.ui.channel.ChannelScreen
import com.selecttube.app.ui.feed.FeedScreen
import com.selecttube.app.ui.onboarding.OnboardingScreen
import com.selecttube.app.ui.player.PlayerScreen
import com.selecttube.app.ui.player.PlayerViewModel
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isBottomBarVisible = navBackStackEntry?.destination?.route == Screen.Feed.route

    if (isOnboardingDone == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val startRoute = if (isOnboardingDone == true) Screen.Feed.route else Screen.Onboarding.route
    val hasVideo = playbackState.playbackInfo != null || playbackState.isResolving

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hasVideo && !isExpanded) {
                            Modifier.padding(bottom = 64.dp)
                        } else {
                            Modifier
                        }
                    ),
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
                                urlOrId = video.url.ifBlank { video.videoId },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
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
                        onAddPlaylist = { navController.navigate(Screen.AddPlaylist.route) },
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onVideoClick = { video ->
                            playerViewModel.playVideo(
                                urlOrId = video.url.ifBlank { video.videoId },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
                        onChannelClick = { channelId, channelName ->
                            playerViewModel.collapsePlayer()
                            navController.navigate(Screen.Channel.createRoute(channelId, channelName))
                        },
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
                                urlOrId = video.url.ifBlank { video.videoId },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
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
                                urlOrId = video.url.ifBlank { video.videoId },
                                title = video.title,
                                channelName = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                            )
                            playerViewModel.expandPlayer()
                        },
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
}
