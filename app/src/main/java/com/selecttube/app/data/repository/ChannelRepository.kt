package com.selecttube.app.data.repository

import com.selecttube.app.data.db.ChannelDao
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.preferences.AppPreferences
import com.selecttube.app.domain.Channel
import com.selecttube.app.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val dao: ChannelDao,
    private val prefs: AppPreferences,
) {
    /** Live stream of all channels (both active and pending), newest first. */
    val channels: Flow<List<Channel>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** BCrypt hash of the user's password, or null before first channel is added. */
    val passwordHash: Flow<String?> = prefs.passwordHash

    suspend fun getPasswordHash(): String? = prefs.passwordHash.first()

    suspend fun hasPassword(): Boolean = getPasswordHash() != null

    /** Total number of channels in the DB. */
    suspend fun channelCount(): Int = dao.count()

    suspend fun addChannel(entity: ChannelEntity) = dao.insert(entity)

    suspend fun addChannels(entities: List<ChannelEntity>) = dao.insertAll(entities)

    /** Removes a channel by ID. Used for both "Remove" (active) and "Cancel" (pending). */
    suspend fun removeChannel(channelId: String) = dao.deleteById(channelId)

    suspend fun getChannel(channelId: String): Channel? = dao.getById(channelId)?.toDomain()

    suspend fun setPasswordHash(hash: String) = prefs.setPasswordHash(hash)
}
