package com.selecttube.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A curated YouTube playlist added by the user.
 *
 * Status is derived at runtime:
 *   - [activationTimestamp] == null -> active immediately
 *   - System.currentTimeMillis() >= [activationTimestamp] -> active
 *   - otherwise -> pending
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    /** YouTube playlist ID, e.g. "PLxxxxxx" */
    @PrimaryKey val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videoCount: Long,
    /** Epoch-ms timestamp when the playlist becomes active. Null if active immediately. */
    val activationTimestamp: Long?,
    /** When the user added the playlist. */
    val addedAt: Long,
)
