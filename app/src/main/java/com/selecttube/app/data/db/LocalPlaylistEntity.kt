package com.selecttube.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
