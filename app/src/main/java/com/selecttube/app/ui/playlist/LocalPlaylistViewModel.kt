package com.selecttube.app.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.db.LocalPlaylistEntity
import com.selecttube.app.data.db.LocalPlaylistVideoEntity
import com.selecttube.app.data.repository.LocalPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class LocalPlaylistUiState(
    val playlist: LocalPlaylistEntity? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LocalPlaylistRepository,
) : ViewModel() {

    private val rawPlaylistId: String = checkNotNull(savedStateHandle["playlistId"])
    val playlistId: String = URLDecoder.decode(rawPlaylistId, "UTF-8")
    private val rawName: String? = savedStateHandle["name"]
    val initialName: String = rawName?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()

    private val _uiState = MutableStateFlow(LocalPlaylistUiState())
    val uiState: StateFlow<LocalPlaylistUiState> = _uiState.asStateFlow()

    val videos: StateFlow<List<LocalPlaylistVideoEntity>> = repository
        .observeVideosForPlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            val pl = repository.getPlaylistById(playlistId)
            _uiState.value = LocalPlaylistUiState(
                playlist = pl ?: LocalPlaylistEntity(id = playlistId, name = initialName),
                isLoading = false,
            )
        }
    }

    fun renamePlaylist(newName: String) {
        val name = newName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name)
            _uiState.value = _uiState.value.copy(
                playlist = _uiState.value.playlist?.copy(name = name)
            )
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoId)
        }
    }
}
