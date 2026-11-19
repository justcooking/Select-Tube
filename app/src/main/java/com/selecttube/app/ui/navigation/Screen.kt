package com.selecttube.app.ui.navigation

sealed class Screen(val route: String) {
    data object Feed       : Screen("feed")
    data object Settings   : Screen("settings")
    data object AddChannel : Screen("add_channel")
    data object Player     : Screen("player")
    data object Channel    : Screen("channel/{channelId}?name={channelName}") {
        fun createRoute(channelId: String, channelName: String = ""): String {
            val encId = java.net.URLEncoder.encode(channelId, "UTF-8")
            val encName = java.net.URLEncoder.encode(channelName, "UTF-8")
            return "channel/$encId?name=$encName"
        }
    }
}
