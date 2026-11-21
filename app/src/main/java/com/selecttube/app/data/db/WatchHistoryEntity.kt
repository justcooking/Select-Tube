package com.selecttube.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val lastPositionMs: Long = 0L,
    val watchedAt: Long = System.currentTimeMillis(),
)
