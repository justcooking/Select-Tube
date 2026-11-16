package com.selecttube.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    /** Live list ordered newest-added first. */
    @Query("SELECT * FROM channels ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE channelId = :id")
    suspend fun getById(id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE channelId = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int
}
