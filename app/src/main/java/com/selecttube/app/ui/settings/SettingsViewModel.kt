package com.selecttube.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ChannelItemUiState(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    val isActive: Boolean,
    val remainingTimeFormatted: String?,
)

data class PlaylistItemUiState(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videoCount: Long,
    val isActive: Boolean,
    val remainingTimeFormatted: String?,
)

data class SettingsUiState(
    val channels: List<ChannelItemUiState> = emptyList(),
    val playlists: List<PlaylistItemUiState> = emptyList(),
    val appMode: AppMode = AppMode.ADULT,
    val hasPassword: Boolean = false,
    val parentalShortsDisabled: Boolean = false,
    val parentalDailyLimitMinutes: Int = 0,
    val parentalBedtimeEnabled: Boolean = false,
    // Parent authentication state in Child Mode
    val isParentUnlocked: Boolean = false,
    val unlockPasswordInput: String = "",
    val unlockError: String? = null,
    // Switch to Adult Mode confirmation dialog
    val showConfirmSwitchToAdultDialog: Boolean = false,
    // Switch to Child Mode dialog
    val showSwitchToChildDialog: Boolean = false,
    val childPasswordInput: String = "",
    val childConfirmPasswordInput: String = "",
    val childPasswordError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeChannels()
        observePlaylists()
        observePreferences()
    }

    private fun observeChannels() {
        viewModelScope.launch {
            combine(repository.channels, ticker) { channels, now ->
                channels.map { channel ->
                    val isActive = channel.activationTimestamp == null || now >= channel.activationTimestamp
                    val msUntilActive = if (channel.activationTimestamp == null) 0L else maxOf(0L, channel.activationTimestamp - now)
                    val remainingFormatted = if (!isActive) {
                        val hours = TimeUnit.MILLISECONDS.toHours(msUntilActive)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(msUntilActive) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(msUntilActive) % 60
                        when {
                            hours > 0 -> "Active in ${hours}h ${minutes}m"
                            minutes > 0 -> "Active in ${minutes}m ${seconds}s"
                            else -> "Active in ${seconds}s"
                        }
                    } else null

                    ChannelItemUiState(
                        channelId = channel.channelId,
                        name = channel.name,
                        thumbnailUrl = channel.thumbnailUrl,
                        isActive = isActive,
                        remainingTimeFormatted = remainingFormatted,
                    )
                }
            }.collect { channelItems ->
                _uiState.update { it.copy(channels = channelItems) }
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            combine(repository.playlists, ticker) { playlists, now ->
                playlists.map { playlist ->
                    val isActive = playlist.activationTimestamp == null || now >= playlist.activationTimestamp
                    val msUntilActive = if (playlist.activationTimestamp == null) 0L else maxOf(0L, playlist.activationTimestamp - now)
                    val remainingFormatted = if (!isActive) {
                        val hours = TimeUnit.MILLISECONDS.toHours(msUntilActive)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(msUntilActive) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(msUntilActive) % 60
                        when {
                            hours > 0 -> "Active in ${hours}h ${minutes}m"
                            minutes > 0 -> "Active in ${minutes}m ${seconds}s"
                            else -> "Active in ${seconds}s"
                        }
                    } else null

                    PlaylistItemUiState(
                        playlistId = playlist.playlistId,
                        name = playlist.name,
                        thumbnailUrl = playlist.thumbnailUrl,
                        uploaderName = playlist.uploaderName,
                        videoCount = playlist.videoCount,
                        isActive = isActive,
                        remainingTimeFormatted = remainingFormatted,
                    )
                }
            }.collect { playlistItems ->
                _uiState.update { it.copy(playlists = playlistItems) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                repository.appMode,
                repository.passwordHash,
                repository.parentalShortsDisabled,
                repository.parentalDailyLimitMinutes,
                repository.parentalBedtimeEnabled,
            ) { mode, passHash, shortsDis, dailyLim, bedtimeEn ->
                _uiState.update {
                    it.copy(
                        appMode = mode,
                        hasPassword = passHash != null,
                        parentalShortsDisabled = shortsDis,
                        parentalDailyLimitMinutes = dailyLim,
                        parentalBedtimeEnabled = bedtimeEn,
                        // In Adult mode, automatically unlocked; in Child mode, keep locked unless parent entered password
                        isParentUnlocked = if (mode == AppMode.ADULT) true else it.isParentUnlocked,
                    )
                }
            }.collect {}
        }
    }

    // ── Parental Controls Toggles ──────────────────────────────────────────────
    fun setParentalShortsDisabled(disabled: Boolean) {
        viewModelScope.launch { repository.setParentalShortsDisabled(disabled) }
    }

    fun setParentalDailyLimitMinutes(minutes: Int) {
        viewModelScope.launch { repository.setParentalDailyLimitMinutes(minutes) }
    }

    fun setParentalBedtimeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setParentalBedtimeEnabled(enabled) }
    }

    // ── Parent Unlock in Child Mode ───────────────────────────────────────────
    fun lockSettings() {
        if (_uiState.value.appMode == AppMode.CHILD) {
            _uiState.update {
                it.copy(
                    isParentUnlocked = false,
                    unlockPasswordInput = "",
                    unlockError = null,
                    showConfirmSwitchToAdultDialog = false,
                )
            }
        }
    }

    fun onUnlockPasswordChange(input: String) {
        _uiState.update { it.copy(unlockPasswordInput = input, unlockError = null) }
    }

    fun submitUnlockPassword() {
        viewModelScope.launch {
            val pass = _uiState.value.unlockPasswordInput
            val stored = repository.getPasswordHash()
            if (stored != null && PasswordSecurity.verify(pass, stored)) {
                _uiState.update {
                    it.copy(
                        isParentUnlocked = true,
                        unlockPasswordInput = "",
                        unlockError = null,
                    )
                }
            } else {
                _uiState.update { it.copy(unlockError = "Incorrect parent password") }
            }
        }
    }

    // ── Mode Switching ─────────────────────────────────────────────────────────
    fun requestSwitchToAdultMode() {
        _uiState.update { it.copy(showConfirmSwitchToAdultDialog = true) }
    }

    fun dismissConfirmSwitchToAdultDialog() {
        _uiState.update { it.copy(showConfirmSwitchToAdultDialog = false) }
    }

    fun confirmSwitchToAdultMode() {
        viewModelScope.launch {
            repository.setAppMode(AppMode.ADULT)
            _uiState.update {
                it.copy(
                    appMode = AppMode.ADULT,
                    isParentUnlocked = true,
                    showConfirmSwitchToAdultDialog = false,
                )
            }
        }
    }

    fun requestSwitchToChildMode() {
        _uiState.update {
            it.copy(
                showSwitchToChildDialog = true,
                childPasswordInput = "",
                childConfirmPasswordInput = "",
                childPasswordError = null,
            )
        }
    }

    fun dismissSwitchToChildDialog() {
        _uiState.update { it.copy(showSwitchToChildDialog = false, childPasswordError = null) }
    }

    fun onChildPasswordChange(input: String) {
        _uiState.update { it.copy(childPasswordInput = input, childPasswordError = null) }
    }

    fun onChildConfirmPasswordChange(input: String) {
        _uiState.update { it.copy(childConfirmPasswordInput = input, childPasswordError = null) }
    }

    fun submitSwitchToChildMode() {
        val state = _uiState.value
        val pass = state.childPasswordInput
        val confirm = state.childConfirmPasswordInput

        if (pass.isBlank()) {
            _uiState.update { it.copy(childPasswordError = "Please enter a password") }
            return
        }
        if (pass.length < 4) {
            _uiState.update { it.copy(childPasswordError = "Password must be at least 4 characters") }
            return
        }
        if (pass != confirm) {
            _uiState.update { it.copy(childPasswordError = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            val hash = PasswordSecurity.hash(pass)
            repository.setPasswordHash(hash)
            repository.setAppMode(AppMode.CHILD)
            repository.setParentalShortsDisabled(true)
            _uiState.update {
                it.copy(
                    appMode = AppMode.CHILD,
                    hasPassword = true,
                    isParentUnlocked = true,
                    showSwitchToChildDialog = false,
                    childPasswordError = null,
                )
            }
        }
    }

    // ── Channel Operations ─────────────────────────────────────────────────────
    fun removeChannel(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }

    fun cancelPending(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }

    // ── Playlist Operations ────────────────────────────────────────────────────
    fun removePlaylist(playlistId: String) {
        viewModelScope.launch { repository.removePlaylist(playlistId) }
    }

    fun cancelPendingPlaylist(playlistId: String) {
        viewModelScope.launch { repository.removePlaylist(playlistId) }
    }
}
