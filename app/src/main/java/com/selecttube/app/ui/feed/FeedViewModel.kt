package com.selecttube.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.db.LocalPlaylistWithCount
import com.selecttube.app.data.db.WatchHistoryEntity
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.repository.LocalPlaylistRepository
import com.selecttube.app.data.repository.WatchHistoryRepository
import com.selecttube.app.data.update.AppReleaseInfo
import com.selecttube.app.data.update.AppUpdateManager
import com.selecttube.app.data.update.UpdateState
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.domain.Channel
import com.selecttube.app.ui.common.VideoActionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PlaylistItemUiState(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videoCount: Long,
    val isActive: Boolean,
    val remainingCountdown: String?,
)

data class FeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val videos: List<VideoFeedItem> = emptyList(),
    val playlists: List<PlaylistItemUiState> = emptyList(),
    val channelErrors: Map<String, String> = emptyMap(),
    val hasActiveChannels: Boolean = false,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val youTubeRepository: YouTubeRepository,
    private val appUpdateManager: AppUpdateManager,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : ViewModel() {

    val localPlaylists: StateFlow<List<LocalPlaylistWithCount>> = localPlaylistRepository
        .observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = watchHistoryRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    private val channelVideosMap = Collections.synchronizedMap(mutableMapOf<String, List<VideoFeedItem>>())
    private val channelErrorsMap = Collections.synchronizedMap(mutableMapOf<String, String>())

    @Volatile
    private var activeChannelsList: List<Channel> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(channelRepository.channels, ticker) { channels, now ->
                channels.filter { it.activationTimestamp == null || now >= it.activationTimestamp }
            }
                .distinctUntilChanged { old, new ->
                    old.map { it.channelId } == new.map { it.channelId }
                }
                .collect { activeList ->
                    onActiveChannelsChanged(activeList)
                }
        }

        viewModelScope.launch {
            combine(channelRepository.playlists, ticker) { playlists, now ->
                playlists.map { p ->
                    val isActive = p.activationTimestamp == null || now >= p.activationTimestamp
                    val msUntilActive = if (p.activationTimestamp == null) 0L else maxOf(0L, p.activationTimestamp - now)
                    val countdown = if (!isActive) {
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
                        playlistId = p.playlistId,
                        name = p.name,
                        thumbnailUrl = p.thumbnailUrl,
                        uploaderName = p.uploaderName,
                        videoCount = p.videoCount,
                        isActive = isActive,
                        remainingCountdown = countdown,
                    )
                }
            }.collect { playlistItems ->
                _uiState.update { it.copy(playlists = playlistItems) }
            }
        }

        // Check for app updates silently in background on launch
        viewModelScope.launch {
            try {
                val result = appUpdateManager.checkForUpdate()
                result.getOrNull()?.let { release ->
                    _updateState.value = UpdateState.UpdateAvailable(release)
                }
            } catch (_: Exception) {
                // Background update check should never disrupt user experience
            }
        }
    }

    fun downloadUpdate(release: AppReleaseInfo) {
        viewModelScope.launch {
            appUpdateManager.downloadApk(release).collect { state ->
                _updateState.value = state
            }
        }
    }

    fun installUpdate(apkFile: java.io.File) {
        appUpdateManager.installApk(apkFile)
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun retryUpdate() {
        val current = _updateState.value
        if (current is UpdateState.Error) {
            viewModelScope.launch {
                val result = appUpdateManager.checkForUpdate()
                result.getOrNull()?.let { release ->
                    downloadUpdate(release)
                } ?: run {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    private fun onActiveChannelsChanged(activeList: List<Channel>) {
        activeChannelsList = activeList
        val activeIds = activeList.map { it.channelId }.toSet()

        channelVideosMap.keys.retainAll(activeIds)
        channelErrorsMap.keys.retainAll(activeIds)

        if (activeList.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    videos = emptyList(),
                    channelErrors = emptyMap(),
                    hasActiveChannels = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                hasActiveChannels = true,
                videos = getMergedSortedVideos(),
                channelErrors = getErrorDisplayMap(),
            )
        }

        val missingChannels = activeList.filter {
            !channelVideosMap.containsKey(it.channelId) && !channelErrorsMap.containsKey(it.channelId)
        }

        if (missingChannels.isNotEmpty()) {
            fetchChannels(missingChannels, isPullToRefresh = false)
        }
    }

    fun refresh() {
        val currentActive = activeChannelsList
        if (currentActive.isEmpty()) {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }
        fetchChannels(currentActive, isPullToRefresh = true)
    }

    private fun fetchChannels(channels: List<Channel>, isPullToRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (isPullToRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.update { it.copy(isLoading = channelVideosMap.isEmpty()) }
            }

            channels.map { channel ->
                async {
                    try {
                        val videos = youTubeRepository.fetchChannelVideos(channel.channelId, maxVideos = 15)
                        channelVideosMap[channel.channelId] = videos
                        channelErrorsMap.remove(channel.channelId)
                    } catch (e: Exception) {
                        channelErrorsMap[channel.channelId] = e.message ?: "Failed to load channel videos"
                        if (!channelVideosMap.containsKey(channel.channelId)) {
                            channelVideosMap[channel.channelId] = emptyList()
                        }
                    }
                }
            }.awaitAll()

            val activeIds = activeChannelsList.map { it.channelId }.toSet()
            channelVideosMap.keys.retainAll(activeIds)
            channelErrorsMap.keys.retainAll(activeIds)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    videos = getMergedSortedVideos(),
                    channelErrors = getErrorDisplayMap(),
                )
            }
        }
    }

    private fun getMergedSortedVideos(): List<VideoFeedItem> {
        val activeIds = activeChannelsList.map { it.channelId }.toSet()
        val all = mutableListOf<VideoFeedItem>()
        synchronized(channelVideosMap) {
            for ((id, videos) in channelVideosMap) {
                if (id in activeIds) {
                    all.addAll(videos)
                }
            }
        }
        return all.sortedByDescending { it.uploadTimestamp }
    }

    private fun getErrorDisplayMap(): Map<String, String> {
        val errorMap = mutableMapOf<String, String>()
        synchronized(channelErrorsMap) {
            for ((id, err) in channelErrorsMap) {
                val name = activeChannelsList.firstOrNull { it.channelId == id }?.name ?: id
                errorMap[name] = err
            }
        }
        return errorMap
    }

    // ── Watch History Operations ───────────────────────────────────────────────
    fun deleteHistoryItem(videoId: String) {
        viewModelScope.launch {
            watchHistoryRepository.deleteItem(videoId)
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchHistoryRepository.clearHistory()
        }
    }

    // ── Local Playlist Operations ─────────────────────────────────────────────
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

    fun observeAssignedPlaylistsForVideo(videoId: String): kotlinx.coroutines.flow.Flow<List<String>> =
        localPlaylistRepository.observePlaylistIdsForVideo(videoId)

    fun deleteLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            localPlaylistRepository.deletePlaylist(playlistId)
        }
    }
}
