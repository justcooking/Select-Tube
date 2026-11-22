package com.selecttube.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.db.LocalPlaylistWithCount
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.repository.LocalPlaylistRepository
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.domain.Channel
import com.selecttube.app.ui.common.VideoActionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val activeChannels: List<Channel> = emptyList(),
    val selectedChannelId: String? = null, // null means "All Channels"
    val results: List<VideoFeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val youtubeRepository: YouTubeRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : ViewModel() {

    val localPlaylists: StateFlow<List<LocalPlaylistWithCount>> = localPlaylistRepository
        .observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createLocalPlaylist(name: String, initialVideo: VideoActionTarget? = null) {
        viewModelScope.launch {
            val id = localPlaylistRepository.createPlaylist(name)
            if (initialVideo != null) {
                localPlaylistRepository.addVideoToPlaylist(
                    playlistId = id,
                    videoId = initialVideo.videoId,
                    title = initialVideo.title,
                    channelName = initialVideo.channelName,
                    channelId = initialVideo.channelId,
                    thumbnailUrl = initialVideo.thumbnailUrl,
                    durationSeconds = initialVideo.durationSeconds,
                )
            }
        }
    }

    fun toggleVideoInLocalPlaylist(playlistId: String, video: VideoActionTarget) {
        viewModelScope.launch {
            localPlaylistRepository.toggleVideoInPlaylist(
                playlistId = playlistId,
                videoId = video.videoId,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
            )
        }
    }

    fun observeAssignedPlaylistsForVideo(videoId: String) =
        localPlaylistRepository.observePlaylistIdsForVideo(videoId)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    init {
        // Observe channels from DB in real time
        viewModelScope.launch {
            channelRepository.channels.collect { allChannels ->
                val active = allChannels.filter { it.isActive }
                _uiState.update { it.copy(activeChannels = active) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        val trimmed = newQuery.trim()
        debounceJob?.cancel()
        searchJob?.cancel()

        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    query = newQuery,
                    results = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null,
                )
            }
            return
        }

        // When user enters a new search term, clear old results and show loading indicator
        _uiState.update {
            it.copy(
                query = newQuery,
                results = emptyList(),
                isLoading = true,
                hasSearched = false,
                errorMessage = null,
            )
        }

        // Debounce automatic search while typing
        debounceJob = viewModelScope.launch {
            delay(500L)
            executeSearch()
        }
    }

    fun onSearchSubmitted() {
        debounceJob?.cancel()
        executeSearch()
    }

    fun onChannelFilterSelected(channelId: String?) {
        if (_uiState.value.selectedChannelId == channelId) return

        _uiState.update { it.copy(selectedChannelId = channelId) }

        if (_uiState.value.query.isNotBlank()) {
            debounceJob?.cancel()
            executeSearch()
        }
    }

    fun clearQuery() {
        debounceJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                isLoading = false,
                hasSearched = false,
                errorMessage = null,
            )
        }
    }

    private fun executeSearch() {
        val currentQuery = _uiState.value.query.trim()
        val activeChannels = _uiState.value.activeChannels

        if (currentQuery.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (activeChannels.isEmpty()) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        isLoading = false,
                        hasSearched = true,
                        errorMessage = "No active channels to search within. Add channels from Settings.",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasSearched = true,
                    errorMessage = null,
                )
            }

            try {
                val found = youtubeRepository.searchCuratedVideos(
                    query = currentQuery,
                    allowedChannels = activeChannels,
                    targetChannelId = _uiState.value.selectedChannelId,
                )
                _uiState.update {
                    it.copy(
                        results = found,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to perform search. Please check connection.",
                    )
                }
            }
        }
    }
}
