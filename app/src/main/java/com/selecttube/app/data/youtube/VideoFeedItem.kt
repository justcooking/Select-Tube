package com.selecttube.app.data.youtube

data class VideoFeedItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    val thumbnailUrl: String? = null,
    val uploadDateText: String? = null,
    val uploadTimestamp: Long = 0L,
    val durationSeconds: Long = 0L,
    val url: String = "",
)
