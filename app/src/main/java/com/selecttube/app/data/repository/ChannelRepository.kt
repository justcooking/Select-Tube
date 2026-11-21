package com.selecttube.app.data.repository

import com.selecttube.app.data.db.ChannelDao
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.db.PlaylistDao
import com.selecttube.app.data.db.PlaylistEntity
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.preferences.AppPreferences
import com.selecttube.app.domain.Channel
import com.selecttube.app.domain.Playlist
import com.selecttube.app.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val dao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val prefs: AppPreferences,
) {
    /** Live stream of all channels (both active and pending), newest first. */
    val channels: Flow<List<Channel>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Live stream of all playlists (both active and pending), newest first. */
    val playlists: Flow<List<Playlist>> =
        playlistDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Whether initial onboarding flow has been completed. */
    val onboardingCompleted: Flow<Boolean> = prefs.onboardingCompleted

    suspend fun setOnboardingCompleted(completed: Boolean) = prefs.setOnboardingCompleted(completed)

    suspend fun isOnboardingCompleted(): Boolean = prefs.onboardingCompleted.first()

    /** Whether first-setup immediate activation has been consumed. */
    val firstSetupConsumed: Flow<Boolean> = prefs.firstSetupConsumed

    suspend fun setFirstSetupConsumed(consumed: Boolean) = prefs.setFirstSetupConsumed(consumed)

    suspend fun isFirstSetupConsumed(): Boolean = prefs.firstSetupConsumed.first()

    /** Active app mode (ADULT vs CHILD). */
    val appMode: Flow<AppMode> = prefs.appMode

    suspend fun setAppMode(mode: AppMode) = prefs.setAppMode(mode)

    suspend fun getAppMode(): AppMode = prefs.appMode.first()

    /** BCrypt hash of the parent's master password (in CHILD mode). */
    val passwordHash: Flow<String?> = prefs.passwordHash

    suspend fun getPasswordHash(): String? = prefs.passwordHash.first()

    suspend fun hasPassword(): Boolean = getPasswordHash() != null

    suspend fun setPasswordHash(hash: String?) = prefs.setPasswordHash(hash)

    // -------------------------------------------------------------
    // Parental Controls Flows & Setters
    // -------------------------------------------------------------

    val parentalShortsDisabled: Flow<Boolean> = prefs.parentalShortsDisabled
    suspend fun setParentalShortsDisabled(disabled: Boolean) = prefs.setParentalShortsDisabled(disabled)

    val parentalSearchDisabled: Flow<Boolean> = prefs.parentalSearchDisabled
    suspend fun setParentalSearchDisabled(disabled: Boolean) = prefs.setParentalSearchDisabled(disabled)

    val parentalDailyLimitMinutes: Flow<Int> = prefs.parentalDailyLimitMinutes
    suspend fun setParentalDailyLimitMinutes(minutes: Int) = prefs.setParentalDailyLimitMinutes(minutes)

    val dailyWatchSeconds: Flow<Int> = prefs.dailyWatchSeconds
    val dailyWatchDate: Flow<String?> = prefs.dailyWatchDate
    suspend fun recordWatchSeconds(additionalSeconds: Int, todayDate: String) =
        prefs.recordWatchSeconds(additionalSeconds, todayDate)
    suspend fun resetDailyWatchSeconds(todayDate: String) =
        prefs.resetDailyWatchSeconds(todayDate)

    val parentalBedtimeEnabled: Flow<Boolean> = prefs.parentalBedtimeEnabled
    suspend fun setParentalBedtimeEnabled(enabled: Boolean) = prefs.setParentalBedtimeEnabled(enabled)

    val parentalBedtimeStartHour: Flow<Int> = prefs.parentalBedtimeStartHour
    suspend fun setParentalBedtimeStartHour(hour: Int) = prefs.setParentalBedtimeStartHour(hour)

    val parentalBedtimeEndHour: Flow<Int> = prefs.parentalBedtimeEndHour
    suspend fun setParentalBedtimeEndHour(hour: Int) = prefs.setParentalBedtimeEndHour(hour)

    // -------------------------------------------------------------
    // Channel Operations
    // -------------------------------------------------------------

    /** Total number of channels in the DB. */
    suspend fun channelCount(): Int = dao.count()

    suspend fun addChannel(entity: ChannelEntity) = dao.insert(entity)

    suspend fun addChannels(entities: List<ChannelEntity>) = dao.insertAll(entities)

    /** Removes a channel by ID. Used for both "Remove" (active) and "Cancel" (pending). */
    suspend fun removeChannel(channelId: String) = dao.deleteById(channelId)

    suspend fun getChannel(channelId: String): Channel? = dao.getById(channelId)?.toDomain()

    // -------------------------------------------------------------
    // Playlist Operations
    // -------------------------------------------------------------

    /** Total number of playlists in the DB. */
    suspend fun playlistCount(): Int = playlistDao.count()

    suspend fun addPlaylist(entity: PlaylistEntity) = playlistDao.insert(entity)

    suspend fun removePlaylist(playlistId: String) = playlistDao.deleteById(playlistId)

    suspend fun getPlaylist(playlistId: String): Playlist? = playlistDao.getById(playlistId)?.toDomain()
}
