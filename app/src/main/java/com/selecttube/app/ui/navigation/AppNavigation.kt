package com.selecttube.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.selecttube.app.ui.addchannel.AddChannelScreen
import com.selecttube.app.ui.feed.FeedScreen
import com.selecttube.app.ui.player.MiniPlayer
import com.selecttube.app.ui.player.PlayerScreen
import com.selecttube.app.ui.player.PlayerViewModel
import com.selecttube.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val showMiniPlayer = currentRoute != Screen.Player.route &&
        playbackState.playbackInfo != null

    Scaffold(
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayer(
                    playbackState = playbackState,
                    player = playerViewModel.player,
                    onExpand = {
                        if (navController.currentDestination?.route != Screen.Player.route) {
                            navController.navigate(Screen.Player.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onTogglePlayPause = {
                        playerViewModel.togglePlayPause()
                    },
                    onClose = {
                        playerViewModel.stop()
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Screen.Feed.route) {
                FeedScreen(
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onVideoClick = { video ->
                        playerViewModel.playVideo(
                            urlOrId = video.url.ifBlank { video.videoId },
                            title = video.title,
                            channelName = video.channelName,
                            thumbnailUrl = video.thumbnailUrl,
                        )
                        navController.navigate(Screen.Player.route)
                    },
                )
            }

            composable(Screen.Player.route) {
                PlayerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = playerViewModel,
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAddChannel = { navController.navigate(Screen.AddChannel.route) },
                )
            }

            composable(Screen.AddChannel.route) {
                AddChannelScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
