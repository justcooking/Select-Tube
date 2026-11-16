package com.selecttube.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A channel the user has added.
 *
 * Status is derived at runtime — no stored enum:
 *   - [activationTimestamp] == null  → active immediately (the very first channel)
 *   - System.currentTimeMillis() >= [activationTimestamp]  → active
 *   - otherwise → pending
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    /** YouTube channel ID, e.g. "UCxxxxxx" */
    @PrimaryKey val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    /**
     * Epoch-ms timestamp at which the channel becomes active.
     * Null means it was the first channel and became active immediately.
     */
    val activationTimestamp: Long?,
    /** When the user submitted the add request. */
    val addedAt: Long,
)
