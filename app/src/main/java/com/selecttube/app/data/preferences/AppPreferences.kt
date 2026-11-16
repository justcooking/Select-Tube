package com.selecttube.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * Thin wrapper around DataStore<Preferences>.
 *
 * Currently stores:
 *   - [passwordHash]  BCrypt hash of the user's one-time password.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
    }

    /** Emits the stored BCrypt hash, or null if no password has been set yet. */
    val passwordHash: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_PASSWORD_HASH] }

    suspend fun setPasswordHash(hash: String) {
        context.dataStore.edit { it[KEY_PASSWORD_HASH] = hash }
    }
}
