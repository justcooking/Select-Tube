package com.selecttube.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "local_playlist_videos",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["playlistId", "position"])],
)
data class LocalPlaylistVideoEntity(
    val playlistId: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)
