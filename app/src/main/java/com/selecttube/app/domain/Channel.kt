package com.selecttube.app.domain

import com.selecttube.app.data.db.ChannelEntity

/**
 * Domain model for a channel.
 *
 * Status is derived from [activationTimestamp] and the current clock — not stored.
 */
data class Channel(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    /**
     * Epoch-ms timestamp at which this channel becomes active.
     * Null = was the first channel; became active immediately.
     */
    val activationTimestamp: Long?,
    val addedAt: Long,
) {
    val isActive: Boolean
        get() = activationTimestamp == null || System.currentTimeMillis() >= activationTimestamp

    val isPending: Boolean
        get() = !isActive

    /** Milliseconds remaining until the channel activates. 0 if already active. */
    val msUntilActive: Long
        get() = if (activationTimestamp == null) 0L
                else maxOf(0L, activationTimestamp - System.currentTimeMillis())
}

fun ChannelEntity.toDomain() = Channel(
    channelId         = channelId,
    name              = name,
    thumbnailUrl      = thumbnailUrl,
    activationTimestamp = activationTimestamp,
    addedAt           = addedAt,
)
