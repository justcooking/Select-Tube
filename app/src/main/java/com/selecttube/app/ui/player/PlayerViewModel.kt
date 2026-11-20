package com.selecttube.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.playback.PlayerManager
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ParentalPlaybackRestriction(
    val isBedtimeRestricted: Boolean = false,
    val isScreenTimeRestricted: Boolean = false,
    val showUnlockDialog: Boolean = false,
    val unlockPasswordInput: String = "",
    val unlockError: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val uiState = playerManager.playbackState
    val player = playerManager.player

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _parentalRestriction = MutableStateFlow(ParentalPlaybackRestriction())
    val parentalRestriction: StateFlow<ParentalPlaybackRestriction> = _parentalRestriction.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                channelRepository.appMode,
                channelRepository.parentalBedtimeEnabled,
                channelRepository.parentalBedtimeStartHour,
                channelRepository.parentalBedtimeEndHour,
            ) { mode, bedtimeEnabled, startHour, endHour ->
                if (mode == AppMode.CHILD && bedtimeEnabled) {
                    val cal = Calendar.getInstance()
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val isBedtime = if (startHour > endHour) {
                        hour >= startHour || hour < endHour
                    } else {
                        hour in startHour until endHour
                    }
                    isBedtime
                } else {
                    false
                }
            }.collect { isRestricted ->
                if (isRestricted && !_parentalRestriction.value.isBedtimeRestricted) {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
                _parentalRestriction.update { it.copy(isBedtimeRestricted = isRestricted) }
            }
        }
    }

    fun requestParentalUnlock() {
        _parentalRestriction.update {
            it.copy(showUnlockDialog = true, unlockPasswordInput = "", unlockError = null)
        }
    }

    fun dismissParentalUnlockDialog() {
        _parentalRestriction.update { it.copy(showUnlockDialog = false, unlockError = null) }
    }

    fun onParentalUnlockPasswordChange(input: String) {
        _parentalRestriction.update { it.copy(unlockPasswordInput = input, unlockError = null) }
    }

    fun submitParentalUnlock() {
        viewModelScope.launch {
            val pass = _parentalRestriction.value.unlockPasswordInput
            val stored = channelRepository.getPasswordHash()
            if (stored != null && PasswordSecurity.verify(pass, stored)) {
                _parentalRestriction.update {
                    it.copy(
                        isBedtimeRestricted = false,
                        isScreenTimeRestricted = false,
                        showUnlockDialog = false,
                        unlockError = null,
                    )
                }
            } else {
                _parentalRestriction.update { it.copy(unlockError = "Incorrect parent password") }
            }
        }
    }

    fun expandPlayer() {
        _isPlayerExpanded.value = true
    }

    fun collapsePlayer() {
        _isPlayerExpanded.value = false
    }

    fun playVideo(
        urlOrId: String,
        title: String? = null,
        channelName: String? = null,
        thumbnailUrl: String? = null,
    ) {
        _isPlayerExpanded.value = true
        playerManager.playVideo(urlOrId, title, channelName, thumbnailUrl)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun seekForward() {
        playerManager.seekForward()
    }

    fun seekBackward() {
        playerManager.seekBackward()
    }

    fun retry() {
        playerManager.retry()
    }

    fun stop() {
        _isPlayerExpanded.value = false
        playerManager.stop()
    }

    fun setQuality(quality: String) {
        playerManager.setQuality(quality)
    }

    fun setAudioTrack(trackName: String) {
        playerManager.setAudioTrack(trackName)
    }

    fun setSubtitle(subtitleName: String?) {
        playerManager.setSubtitle(subtitleName)
    }

    fun setBackgroundOnly(enabled: Boolean) {
        playerManager.setBackgroundOnly(enabled)
    }

    fun resumeFromBackground() {
        playerManager.setBackgroundOnly(false)
        if (!player.isPlaying) {
            player.play()
        }
    }
}
