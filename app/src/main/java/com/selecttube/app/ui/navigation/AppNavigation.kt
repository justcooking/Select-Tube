package com.selecttube.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.selecttube.app.ui.feed.FeedScreen
import com.selecttube.app.ui.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController   = navController,
        startDestination = Screen.Feed.route,
    ) {
        composable(Screen.Feed.route) {
            FeedScreen(
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddChannel   = { navController.navigate(Screen.AddChannel.route) },
            )
        }

        // Phase 2: AddChannel screen — stub kept so the nav graph compiles.
        composable(Screen.AddChannel.route) {
            // TODO: implement in Phase 2
        }
    }
}
