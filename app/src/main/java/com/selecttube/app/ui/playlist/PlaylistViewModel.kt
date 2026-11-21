package com.selecttube.app.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.PlaylistEntity
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.youtube.ResolvedPlaylist
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.data.youtube.YouTubeRepository
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
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val playlist: ResolvedPlaylist? = null,
    val videos: List<VideoFeedItem> = emptyList(),
    val isAllowed: Boolean = false,
    val isActive: Boolean = false,
    val remainingCountdown: String? = null,
    val appMode: AppMode = AppMode.ADULT,
    val showAddDialog: Boolean = false,
    val passwordInput: String = "",
    val addError: String? = null,
    val isAdding: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youTubeRepository: YouTubeRepository,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val playlistIdParam: String = try {
        val raw = savedStateHandle.get<String>("playlistId").orEmpty()
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("playlistId").orEmpty()
    }

    val playlistNameParam: String = try {
        val raw = savedStateHandle.get<String>("playlistName").orEmpty()
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("playlistName").orEmpty()
    }

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    init {
        observePlaylistCuration()
        observeAppMode()
        loadPlaylist(isRefresh = false)
    }

    private fun observeAppMode() {
        viewModelScope.launch {
            channelRepository.appMode.collect { mode ->
                _uiState.update { it.copy(appMode = mode) }
            }
        }
    }

    private fun observePlaylistCuration() {
        viewModelScope.launch {
            combine(channelRepository.playlists, ticker) { playlists, now ->
                val matching = playlists.firstOrNull { it.playlistId == playlistIdParam }
                val isAllowed = matching != null
                val isActive = matching != null && (matching.activationTimestamp == null || now >= matching.activationTimestamp)
                val msUntilActive = if (matching?.activationTimestamp == null) 0L else maxOf(0L, matching.activationTimestamp - now)

                val countdown = if (matching != null && !isActive) {
                    val hours = TimeUnit.MILLISECONDS.toHours(msUntilActive)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(msUntilActive) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(msUntilActive) % 60
                    when {
                        hours > 0 -> "Active in ${hours}h ${minutes}m"
                        minutes > 0 -> "Active in ${minutes}m ${seconds}s"
                        else -> "Active in ${seconds}s"
                    }
                } else null

                Triple(isAllowed, isActive, countdown)
            }.collect { (isAllowed, isActive, countdown) ->
                _uiState.update {
                    it.copy(
                        isAllowed = isAllowed,
                        isActive = isActive,
                        remainingCountdown = countdown,
                    )
                }
            }
        }
    }

    fun loadPlaylist(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null)
            }

            try {
                val (resolved, videos) = youTubeRepository.fetchPlaylistVideos(playlistIdParam)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        playlist = resolved,
                        videos = videos,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Failed to load playlist: ${e.message ?: "Network error"}",
                    )
                }
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, passwordInput = "", addError = null) }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, addError = null) }
    }

    fun onPasswordChange(input: String) {
        _uiState.update { it.copy(passwordInput = input, addError = null) }
    }

    fun confirmAddPlaylist() {
        val state = _uiState.value
        val playlist = state.playlist ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true, addError = null) }

            if (state.appMode == AppMode.CHILD) {
                val stored = channelRepository.getPasswordHash()
                if (stored == null || !PasswordSecurity.verify(state.passwordInput, stored)) {
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            addError = "Incorrect parent password. Please re-enter.",
                        )
                    }
                    return@launch
                }
            }

            try {
                val now = System.currentTimeMillis()
                val activationTime = if (state.appMode == AppMode.ADULT) {
                    now + ChannelConfig.ACTIVATION_DELAY_MS
                } else {
                    null // Activated immediately upon parent entry in Child Mode
                }

                val entity = PlaylistEntity(
                    playlistId = playlist.playlistId,
                    name = playlist.name,
                    thumbnailUrl = playlist.thumbnailUrl,
                    uploaderName = playlist.uploaderName,
                    videoCount = playlist.videoCount,
                    activationTimestamp = activationTime,
                    addedAt = now,
                )

                channelRepository.addPlaylist(entity)
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        showAddDialog = false,
                        addError = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        addError = "Failed to add playlist: ${e.message}",
                    )
                }
            }
        }
    }
}
