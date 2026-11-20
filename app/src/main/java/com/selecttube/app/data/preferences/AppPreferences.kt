package com.selecttube.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class AppMode {
    ADULT,
    CHILD,
}

/**
 * DataStore preferences wrapper for Select Tube.
 *
 * Stores:
 *   - [onboardingCompleted] Whether initial onboarding setup has been finished.
 *   - [appMode] Whether the app runs in ADULT mode or CHILD (Parental Controls) mode.
 *   - [passwordHash] BCrypt hash of the parent's master password (in CHILD mode).
 *   - Parental control flags (Shorts disabled, search disabled, watch limits, bedtime).
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_APP_MODE = stringPreferencesKey("app_mode")
        val KEY_PARENTAL_SHORTS_DISABLED = booleanPreferencesKey("parental_shorts_disabled")
        val KEY_PARENTAL_SEARCH_DISABLED = booleanPreferencesKey("parental_search_disabled")
        val KEY_PARENTAL_DAILY_LIMIT_MINUTES = intPreferencesKey("parental_daily_limit_minutes")
        val KEY_PARENTAL_BEDTIME_ENABLED = booleanPreferencesKey("parental_bedtime_enabled")
        val KEY_PARENTAL_BEDTIME_START_HOUR = intPreferencesKey("parental_bedtime_start_hour")
        val KEY_PARENTAL_BEDTIME_END_HOUR = intPreferencesKey("parental_bedtime_end_hour")
    }

    /** Emits whether initial onboarding/mode setup has been completed. */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    /** Emits the active AppMode (defaults to ADULT). */
    val appMode: Flow<AppMode> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_APP_MODE]
            if (raw == AppMode.CHILD.name) AppMode.CHILD else AppMode.ADULT
        }

    suspend fun setAppMode(mode: AppMode) {
        context.dataStore.edit { it[KEY_APP_MODE] = mode.name }
    }

    /** Emits the stored BCrypt hash, or null if no password has been set yet. */
    val passwordHash: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_PASSWORD_HASH] }

    suspend fun setPasswordHash(hash: String?) {
        context.dataStore.edit {
            if (hash == null) {
                it.remove(KEY_PASSWORD_HASH)
            } else {
                it[KEY_PASSWORD_HASH] = hash
            }
        }
    }

    // -------------------------------------------------------------
    // Parental Controls Preferences
    // -------------------------------------------------------------

    /** When true, Shorts tabs and vertical video players are blocked/hidden. */
    val parentalShortsDisabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_SHORTS_DISABLED] ?: false }

    suspend fun setParentalShortsDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_PARENTAL_SHORTS_DISABLED] = disabled }
    }

    /** When true, search functionality is hidden/disabled across the app. */
    val parentalSearchDisabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_SEARCH_DISABLED] ?: false }

    suspend fun setParentalSearchDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_PARENTAL_SEARCH_DISABLED] = disabled }
    }

    /** Daily screen time limit in minutes. 0 means unlimited. */
    val parentalDailyLimitMinutes: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_DAILY_LIMIT_MINUTES] ?: 0 }

    suspend fun setParentalDailyLimitMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_PARENTAL_DAILY_LIMIT_MINUTES] = minutes }
    }

    /** Bedtime restriction toggle. */
    val parentalBedtimeEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_BEDTIME_ENABLED] ?: false }

    suspend fun setParentalBedtimeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PARENTAL_BEDTIME_ENABLED] = enabled }
    }

    /** Bedtime start hour (0-23, default 21 = 9 PM). */
    val parentalBedtimeStartHour: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_BEDTIME_START_HOUR] ?: 21 }

    suspend fun setParentalBedtimeStartHour(hour: Int) {
        context.dataStore.edit { it[KEY_PARENTAL_BEDTIME_START_HOUR] = hour }
    }

    /** Bedtime end hour (0-23, default 7 = 7 AM). */
    val parentalBedtimeEndHour: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_PARENTAL_BEDTIME_END_HOUR] ?: 7 }

    suspend fun setParentalBedtimeEndHour(hour: Int) {
        context.dataStore.edit { it[KEY_PARENTAL_BEDTIME_END_HOUR] = hour }
    }
}
