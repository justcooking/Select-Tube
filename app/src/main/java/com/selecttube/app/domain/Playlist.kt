package com.selecttube.app.domain

import com.selecttube.app.data.db.PlaylistEntity

data class Playlist(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videoCount: Long,
    val activationTimestamp: Long?,
    val addedAt: Long,
)

fun PlaylistEntity.toDomain() = Playlist(
    playlistId = playlistId,
    name = name,
    thumbnailUrl = thumbnailUrl,
    uploaderName = uploaderName,
    videoCount = videoCount,
    activationTimestamp = activationTimestamp,
    addedAt = addedAt,
)
