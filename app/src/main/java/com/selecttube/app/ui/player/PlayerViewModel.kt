package com.selecttube.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.repository.LocalPlaylistRepository
import com.selecttube.app.data.repository.WatchHistoryRepository
import com.selecttube.app.playback.PlayerManager
import com.selecttube.app.playback.PlayQueueItem
import com.selecttube.app.playback.SleepTimerInfo
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    private val watchHistoryRepository: WatchHistoryRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : ViewModel() {

    val uiState = playerManager.playbackState
    val player = playerManager.player
    val queue = playerManager.queue
    val history = playerManager.history
    val sleepTimer = playerManager.sleepTimer
    val localPlaylists = localPlaylistRepository.observeAllPlaylists()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _parentalRestriction = MutableStateFlow(ParentalPlaybackRestriction())
    val parentalRestriction: StateFlow<ParentalPlaybackRestriction> = _parentalRestriction.asStateFlow()

    init {
        // Automatically sync playback progress into Watch History
        viewModelScope.launch {
            uiState.collect { state ->
                val info = state.playbackInfo ?: return@collect
                if (state.currentPositionMs > 1000L) {
                    val durationSec = if (state.durationMs > 0L) state.durationMs / 1000L else info.durationSeconds
                    watchHistoryRepository.recordWatch(
                        videoId = info.videoId,
                        title = info.title,
                        channelName = info.channelName,
                        channelId = info.channelId,
                        thumbnailUrl = info.thumbnailUrl,
                        durationSeconds = durationSec,
                        positionMs = state.currentPositionMs,
                    )
                }
            }
        }
        // Active monitoring loop for Bedtime schedule and Daily Screen Time limits
        viewModelScope.launch {
            while (true) {
                delay(5000L)
                val mode = channelRepository.getAppMode()
                if (mode != AppMode.CHILD) continue

                // 1. Bedtime schedule enforcement (evaluated continuously against the clock)
                val bedtimeEnabled = channelRepository.parentalBedtimeEnabled.first()
                if (bedtimeEnabled && !_parentalRestriction.value.isBedtimeRestricted) {
                    val startHour = channelRepository.parentalBedtimeStartHour.first()
                    val endHour = channelRepository.parentalBedtimeEndHour.first()
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val isBedtime = if (startHour > endHour) {
                        hour >= startHour || hour < endHour
                    } else {
                        hour in startHour until endHour
                    }
                    if (isBedtime) {
                        if (player.isPlaying) {
                            player.pause()
                        }
                        _parentalRestriction.update { it.copy(isBedtimeRestricted = true) }
                    }
                }

                // 2. Daily screen time limit enforcement & accumulator
                val dailyLimitMin = channelRepository.parentalDailyLimitMinutes.first()
                if (dailyLimitMin > 0) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val additional = if (player.isPlaying) 5 else 0
                    val totalSec = channelRepository.recordWatchSeconds(additional, today)
                    if (totalSec >= dailyLimitMin * 60 && !_parentalRestriction.value.isScreenTimeRestricted) {
                        if (player.isPlaying) {
                            player.pause()
                        }
                        _parentalRestriction.update { it.copy(isScreenTimeRestricted = true) }
                    }
                }
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
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                channelRepository.resetDailyWatchSeconds(today)
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
        startPositionMs: Long = 0L,
    ) {
        viewModelScope.launch {
            val mode = channelRepository.getAppMode()
            if (mode == AppMode.CHILD) {
                val bedtimeEnabled = channelRepository.parentalBedtimeEnabled.first()
                if (bedtimeEnabled) {
                    val startHour = channelRepository.parentalBedtimeStartHour.first()
                    val endHour = channelRepository.parentalBedtimeEndHour.first()
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val isBedtime = if (startHour > endHour) hour >= startHour || hour < endHour else hour in startHour until endHour
                    if (isBedtime) {
                        _parentalRestriction.update { it.copy(isBedtimeRestricted = true) }
                        _isPlayerExpanded.value = true
                        return@launch
                    }
                }

                val dailyLimitMin = channelRepository.parentalDailyLimitMinutes.first()
                if (dailyLimitMin > 0) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val currentSec = channelRepository.recordWatchSeconds(0, today)
                    if (currentSec >= dailyLimitMin * 60) {
                        _parentalRestriction.update { it.copy(isScreenTimeRestricted = true) }
                        _isPlayerExpanded.value = true
                        return@launch
                    }
                }
            }

            _isPlayerExpanded.value = true
            playerManager.playVideo(urlOrId, title, channelName, thumbnailUrl, startPositionMs)
        }
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

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        playerManager.setSkipSilenceEnabled(enabled)
    }

    fun setBackgroundOnly(enabled: Boolean) {
        playerManager.setBackgroundOnly(enabled)
    }

    fun playInBackground(
        urlOrId: String,
        title: String? = null,
        channelName: String? = null,
        thumbnailUrl: String? = null,
    ) {
        playerManager.setBackgroundOnly(true)
        playVideo(urlOrId, title, channelName, thumbnailUrl)
        _isPlayerExpanded.value = false
    }

    fun enqueue(item: PlayQueueItem, playNext: Boolean = false) {
        playerManager.enqueue(item, playNext)
    }

    fun playQueue(items: List<PlayQueueItem>, startIndex: Int = 0) {
        _isPlayerExpanded.value = true
        playerManager.playQueue(items, startIndex)
    }

    fun playNext() {
        playerManager.playNextFromQueue()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun playQueueIndex(index: Int) {
        playerManager.playQueueIndex(index)
    }

    fun playNextFromQueue() {
        playerManager.playNextFromQueue()
    }

    fun removeQueueItem(index: Int) {
        playerManager.removeQueueItem(index)
    }

    fun clearQueue() {
        playerManager.clearQueue()
    }

    fun shuffleQueue() {
        playerManager.shuffleQueue()
    }

    fun startSleepTimer(minutes: Int) {
        playerManager.startSleepTimer(minutes)
    }

    fun setSleepTimerEndOfVideo() {
        playerManager.setSleepTimerEndOfVideo()
    }

    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
    }

    fun saveQueueAsNewPlaylist(
        name: String,
        includeCurrent: Boolean = true,
        onSuccess: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val itemsToSave = buildList {
                if (includeCurrent) {
                    playerManager.playbackState.value.playbackInfo?.let { curr ->
                        if (curr.videoId.isNotBlank()) {
                            add(
                                PlayQueueItem(
                                    videoId = curr.videoId,
                                    title = curr.title,
                                    channelName = curr.channelName,
                                    thumbnailUrl = curr.thumbnailUrl,
                                    durationSeconds = curr.durationSeconds,
                                )
                            )
                        }
                    }
                }
                addAll(playerManager.queue.value)
            }
            if (itemsToSave.isEmpty()) return@launch
            val id = localPlaylistRepository.createPlaylist(trimmed)
            localPlaylistRepository.addVideosToPlaylist(id, itemsToSave)
            onSuccess(trimmed)
        }
    }

    fun addQueueToExistingPlaylist(
        playlistId: String,
        playlistName: String,
        includeCurrent: Boolean = true,
        onSuccess: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val itemsToSave = buildList {
                if (includeCurrent) {
                    playerManager.playbackState.value.playbackInfo?.let { curr ->
                        if (curr.videoId.isNotBlank()) {
                            add(
                                PlayQueueItem(
                                    videoId = curr.videoId,
                                    title = curr.title,
                                    channelName = curr.channelName,
                                    thumbnailUrl = curr.thumbnailUrl,
                                    durationSeconds = curr.durationSeconds,
                                )
                            )
                        }
                    }
                }
                addAll(playerManager.queue.value)
            }
            if (itemsToSave.isEmpty()) return@launch
            localPlaylistRepository.addVideosToPlaylist(playlistId, itemsToSave)
            onSuccess(playlistName)
        }
    }

    fun resumeFromBackground() {
        playerManager.setBackgroundOnly(false)
        if (!player.isPlaying) {
            player.play()
        }
    }
}
