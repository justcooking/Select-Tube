package com.selecttube.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selecttube.app.ui.addchannel.AddChannelScreen
import com.selecttube.app.ui.feed.FeedScreen
import com.selecttube.app.ui.player.PlayerScreen
import com.selecttube.app.ui.player.PlayerViewModel
import com.selecttube.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Feed.route,
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
