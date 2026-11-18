package com.selecttube.app.ui.player

import androidx.lifecycle.ViewModel
import com.selecttube.app.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
) : ViewModel() {

    val uiState = playerManager.playbackState
    val player = playerManager.player

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

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
