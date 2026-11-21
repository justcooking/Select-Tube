package com.selecttube.app.data.repository

import com.selecttube.app.data.db.WatchHistoryDao
import com.selecttube.app.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepository @Inject constructor(
    private val dao: WatchHistoryDao,
) {
    val history: Flow<List<WatchHistoryEntity>> = dao.getAll()

    suspend fun recordWatch(
        videoId: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
        positionMs: Long = 0L,
    ) {
        val entry = WatchHistoryEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            lastPositionMs = positionMs,
            watchedAt = System.currentTimeMillis(),
        )
        dao.upsert(entry)
    }

    suspend fun updateProgress(videoId: String, positionMs: Long) {
        dao.updateProgress(videoId, positionMs, System.currentTimeMillis())
    }

    suspend fun deleteItem(videoId: String) {
        dao.delete(videoId)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}
