package com.selecttube.app.ui.player

import androidx.lifecycle.ViewModel
import com.selecttube.app.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
) : ViewModel() {

    val uiState = playerManager.playbackState
    val player = playerManager.player

    fun playVideo(
        urlOrId: String,
        title: String? = null,
        channelName: String? = null,
        thumbnailUrl: String? = null,
    ) {
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
        playerManager.stop()
    }

    fun setQuality(quality: String) {
        playerManager.setQuality(quality)
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
