package com.selecttube.app.ui.navigation

sealed class Screen(val route: String) {
    data object Feed       : Screen("feed")
    data object Settings   : Screen("settings")
    data object AddChannel : Screen("add_channel")
    data object Player     : Screen("player")
}
