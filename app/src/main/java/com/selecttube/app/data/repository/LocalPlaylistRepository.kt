package com.selecttube.app.data.repository

import com.selecttube.app.data.db.LocalPlaylistDao
import com.selecttube.app.data.db.LocalPlaylistEntity
import com.selecttube.app.data.db.LocalPlaylistVideoEntity
import com.selecttube.app.data.db.LocalPlaylistWithCount
import com.selecttube.app.playback.PlayQueueItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlaylistRepository @Inject constructor(
    private val dao: LocalPlaylistDao,
) {
    fun observeAllPlaylists(): Flow<List<LocalPlaylistWithCount>> = dao.observeAllPlaylists()

    fun observeVideosForPlaylist(playlistId: String): Flow<List<LocalPlaylistVideoEntity>> =
        dao.observeVideosForPlaylist(playlistId)

    fun observePlaylistIdsForVideo(videoId: String): Flow<List<String>> =
        dao.observePlaylistIdsForVideo(videoId)

    suspend fun getPlaylistById(playlistId: String): LocalPlaylistEntity? =
        dao.getPlaylistById(playlistId)

    suspend fun createPlaylist(name: String): String {
        val trimmed = name.trim()
        val id = UUID.randomUUID().toString()
        val entity = LocalPlaylistEntity(
            id = id,
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.insertPlaylist(entity)
        return id
    }

    suspend fun renamePlaylist(id: String, name: String) {
        dao.renamePlaylist(id = id, name = name.trim())
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addVideoToPlaylist(
        playlistId: String,
        videoId: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
    ) {
        dao.addVideoToPlaylist(
            playlistId = playlistId,
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String) {
        dao.deleteVideo(playlistId = playlistId, videoId = videoId)
    }

    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean =
        dao.isVideoInPlaylist(playlistId = playlistId, videoId = videoId)

    suspend fun toggleVideoInPlaylist(
        playlistId: String,
        videoId: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
    ): Boolean {
        val exists = dao.isVideoInPlaylist(playlistId, videoId)
        return if (exists) {
            dao.deleteVideo(playlistId, videoId)
            false
        } else {
            addVideoToPlaylist(
                playlistId = playlistId,
                videoId = videoId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
            )
            true
        }
    }

    suspend fun addVideosToPlaylist(
        playlistId: String,
        videos: List<PlayQueueItem>,
    ) {
        videos.forEach { video ->
            addVideoToPlaylist(
                playlistId = playlistId,
                videoId = video.videoId,
                title = video.title,
                channelName = video.channelName,
                channelId = "",
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
            )
        }
    }
}
