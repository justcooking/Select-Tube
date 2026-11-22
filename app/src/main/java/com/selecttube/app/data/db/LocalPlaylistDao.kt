package com.selecttube.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class LocalPlaylistWithCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val videoCount: Int,
    val thumbnailUrl: String?,
)

@Dao
interface LocalPlaylistDao {

    @Query(
        """
        SELECT 
            p.id, 
            p.name, 
            p.createdAt, 
            p.updatedAt, 
            COUNT(v.videoId) AS videoCount,
            (SELECT v2.thumbnailUrl FROM local_playlist_videos v2 WHERE v2.playlistId = p.id ORDER BY v2.position ASC LIMIT 1) AS thumbnailUrl
        FROM local_playlists p
        LEFT JOIN local_playlist_videos v ON p.id = v.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeAllPlaylists(): Flow<List<LocalPlaylistWithCount>>

    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): LocalPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity)

    @Query("UPDATE local_playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM local_playlist_videos WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeVideosForPlaylist(playlistId: String): Flow<List<LocalPlaylistVideoEntity>>

    @Query("SELECT MAX(position) FROM local_playlist_videos WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: LocalPlaylistVideoEntity)

    @Query("DELETE FROM local_playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteVideo(playlistId: String, videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM local_playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId)")
    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean

    @Query("SELECT playlistId FROM local_playlist_videos WHERE videoId = :videoId")
    fun observePlaylistIdsForVideo(videoId: String): Flow<List<String>>

    @Transaction
    suspend fun addVideoToPlaylist(
        playlistId: String,
        videoId: String,
        title: String,
        channelName: String,
        channelId: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
    ) {
        val nextPos = (getMaxPosition(playlistId) ?: -1) + 1
        val item = LocalPlaylistVideoEntity(
            playlistId = playlistId,
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            position = nextPos,
            addedAt = System.currentTimeMillis(),
        )
        insertVideo(item)
        touchPlaylist(playlistId)
    }

    @Query("UPDATE local_playlists SET updatedAt = :now WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, now: Long = System.currentTimeMillis())
}
