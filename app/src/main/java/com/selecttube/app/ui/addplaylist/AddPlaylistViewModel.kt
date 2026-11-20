package com.selecttube.app.ui.addplaylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.PlaylistEntity
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.youtube.PlaylistNotFoundException
import com.selecttube.app.data.youtube.ResolvedPlaylist
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddPlaylistTab {
    LINK_PASTE,
    SEARCH,
}

data class AddPlaylistUiState(
    val appMode: AppMode = AppMode.ADULT,
    val selectedTab: AddPlaylistTab = AddPlaylistTab.LINK_PASTE,
    val linkInput: String = "",
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isResolving: Boolean = false,
    val searchResults: List<ResolvedPlaylist> = emptyList(),
    val stagedPlaylists: List<ResolvedPlaylist> = emptyList(),
    val passwordInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaylistUiState())
    val uiState: StateFlow<AddPlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val mode = channelRepository.getAppMode()
            _uiState.update { it.copy(appMode = mode) }
        }
    }

    fun onTabSelected(tab: AddPlaylistTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
    }

    fun onLinkInputChange(input: String) {
        _uiState.update { it.copy(linkInput = input, errorMessage = null) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun resolveDirectLink() {
        val input = _uiState.value.linkInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a playlist URL or ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, errorMessage = null) }
            try {
                val resolved = youTubeRepository.resolvePlaylist(input)
                stagePlaylist(resolved)
                _uiState.update { it.copy(isResolving = false, linkInput = "") }
            } catch (e: PlaylistNotFoundException) {
                _uiState.update { it.copy(isResolving = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        errorMessage = "Failed to resolve playlist. Verify the URL or try searching."
                    )
                }
            }
        }
    }

    fun searchPlaylists() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = youTubeRepository.searchPlaylists(query)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results,
                        errorMessage = if (results.isEmpty()) "No playlists found for '$query'" else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search failed. Check network connection."
                    )
                }
            }
        }
    }

    fun stagePlaylist(playlist: ResolvedPlaylist) {
        _uiState.update { current ->
            if (current.stagedPlaylists.any { it.playlistId == playlist.playlistId }) {
                current
            } else {
                current.copy(
                    stagedPlaylists = current.stagedPlaylists + playlist,
                    errorMessage = null,
                )
            }
        }
    }

    fun unstagePlaylist(playlistId: String) {
        _uiState.update { current ->
            current.copy(stagedPlaylists = current.stagedPlaylists.filter { it.playlistId != playlistId })
        }
    }

    fun saveStagedPlaylists() {
        val state = _uiState.value
        val staged = state.stagedPlaylists
        if (staged.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No playlists selected to add") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            // In Child Mode, verify Parent Master Password
            if (state.appMode == AppMode.CHILD) {
                val stored = channelRepository.getPasswordHash()
                if (stored == null || !PasswordSecurity.verify(state.passwordInput, stored)) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Incorrect parent password. Please re-enter.",
                        )
                    }
                    return@launch
                }
            }

            try {
                val now = System.currentTimeMillis()
                // In Adult Mode, playlists ALWAYS enforce the 2-hour delay (initial launch is channels only)
                val activationTime = if (state.appMode == AppMode.ADULT) {
                    now + ChannelConfig.ACTIVATION_DELAY_MS
                } else {
                    null
                }

                val entities = staged.map { p ->
                    PlaylistEntity(
                        playlistId = p.playlistId,
                        name = p.name,
                        thumbnailUrl = p.thumbnailUrl,
                        uploaderName = p.uploaderName,
                        videoCount = p.videoCount,
                        activationTimestamp = activationTime,
                        addedAt = now,
                    )
                }

                entities.forEach { channelRepository.addPlaylist(it) }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSuccess = true,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to save playlists: ${e.message}",
                    )
                }
            }
        }
    }
}
