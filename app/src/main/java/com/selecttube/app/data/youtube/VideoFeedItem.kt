package com.selecttube.app.data.youtube

data class VideoFeedItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val uploadDateText: String?,
    val uploadTimestamp: Long,
    val durationSeconds: Long,
    val url: String,
)
