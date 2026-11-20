package com.selecttube.app.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.youtube.ResolvedChannel
import com.selecttube.app.data.youtube.VideoFeedItem
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.domain.Channel
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class ChannelCategory(val label: String, val tabKey: String) {
    VIDEOS("Videos", ChannelTabs.VIDEOS),
    SHORTS("Shorts", ChannelTabs.SHORTS),
    LIVESTREAMS("Live", ChannelTabs.LIVESTREAMS),
}

data class ChannelUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val channel: ResolvedChannel? = null,
    val selectedCategory: ChannelCategory = ChannelCategory.VIDEOS,
    val videos: List<VideoFeedItem> = emptyList(),
    val isLoadingTab: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val error: String? = null,
    // Database curation status
    val isAllowed: Boolean = false,
    val isActive: Boolean = false,
    val remainingCountdown: String? = null,
    // Password dialog state
    val showPasswordDialog: Boolean = false,
    val isFirstTimePasswordSetup: Boolean = false,
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val passwordError: String? = null,
    val isSubmittingAction: Boolean = false,
    // Remove confirmation dialog
    val showRemoveConfirmDialog: Boolean = false,
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youTubeRepository: YouTubeRepository,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val channelIdParam: String = try {
        val raw = savedStateHandle.get<String>("channelId").orEmpty()
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("channelId").orEmpty()
    }

    val channelNameParam: String = try {
        val raw = savedStateHandle.get<String>("channelName").orEmpty()
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("channelName").orEmpty()
    }

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private var canonicalChannelId: String? = null
    private var activeTabExtractor: ChannelTabExtractor? = null
    private var nextPage: Page? = null
    private var videoLoadJob: Job? = null

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    init {
        observeChannelCurationStatus()
        loadChannelProfile(isRefresh = false)
    }

    private fun matchesChannel(
        ch: Channel,
        resolved: ResolvedChannel?,
        currentCanonicalId: String?,
    ): Boolean {
        // 1. Channel ID param matching
        if (channelIdParam.isNotBlank()) {
            if (ch.channelId.equals(channelIdParam, ignoreCase = true)) return true
            val cleanStored = ch.channelId.removePrefix("@")
            val cleanParam = channelIdParam.removePrefix("@")
            if (cleanStored.equals(cleanParam, ignoreCase = true)) return true
            if (ch.name.trim().equals(channelIdParam.trim(), ignoreCase = true)) return true
        }

        // 2. Channel Name param matching
        if (channelNameParam.isNotBlank()) {
            if (ch.name.trim().equals(channelNameParam.trim(), ignoreCase = true)) return true
            if (ch.channelId.equals(channelNameParam, ignoreCase = true)) return true
        }

        // 3. Canonical ID matching
        if (!currentCanonicalId.isNullOrBlank()) {
            if (ch.channelId.equals(currentCanonicalId, ignoreCase = true)) return true
            if (ch.channelId.removePrefix("@").equals(currentCanonicalId.removePrefix("@"), ignoreCase = true)) return true
        }

        // 4. Resolved channel profile matching (after NewPipe extractor fetches full metadata)
        if (resolved != null) {
            if (ch.channelId.equals(resolved.channelId, ignoreCase = true)) return true
            if (ch.channelId.removePrefix("@").equals(resolved.channelId.removePrefix("@"), ignoreCase = true)) return true
            if (ch.name.trim().equals(resolved.name.trim(), ignoreCase = true)) return true
            if (resolved.url.isNotBlank()) {
                val slug = resolved.url.substringAfterLast("/")
                if (slug.isNotBlank() && ch.channelId.equals(slug, ignoreCase = true)) return true
                if (resolved.url.contains(ch.channelId, ignoreCase = true)) return true
            }
        }

        return false
    }

    private fun observeChannelCurationStatus() {
        viewModelScope.launch {
            combine(channelRepository.channels, ticker) { channels, now ->
                val matching = channels.firstOrNull { ch ->
                    matchesChannel(ch, _uiState.value.channel, canonicalChannelId)
                }

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
                val wasActive = _uiState.value.isActive
                _uiState.update {
                    it.copy(
                        isAllowed = isAllowed,
                        isActive = isActive,
                        remainingCountdown = countdown,
                    )
                }

                // If channel transitioned from pending to active, start loading initial videos
                if (isActive && !wasActive && !_uiState.value.isLoadingTab) {
                    loadCategoryVideos(_uiState.value.selectedCategory, isRefresh = false)
                }
            }
        }
    }

    fun loadChannelProfile(isRefresh: Boolean) {
        val query = channelIdParam.ifBlank { channelNameParam }
        if (query.isBlank()) {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Invalid channel identifier.") }
            return
        }

        viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                // 1. Resolve full channel profile metadata from NewPipe
                val resolvedChannel = youTubeRepository.resolveChannel(query)
                canonicalChannelId = resolvedChannel.channelId

                // 2. Check curation status immediately against current channels list
                val channels = channelRepository.channels.first()
                val now = System.currentTimeMillis()
                val matching = channels.firstOrNull { ch ->
                    matchesChannel(ch, resolvedChannel, canonicalChannelId)
                }
                val isAllowed = matching != null
                val isActive = matching != null && (matching.activationTimestamp == null || now >= matching.activationTimestamp)

                _uiState.update {
                    it.copy(
                        channel = resolvedChannel,
                        isAllowed = isAllowed,
                        isActive = isActive,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }

                // Only load videos if the channel is currently allowed and active
                if (isAllowed && isActive) {
                    loadCategoryVideos(_uiState.value.selectedCategory, isRefresh = isRefresh)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.localizedMessage ?: "Failed to load channel details.",
                    )
                }
            }
        }
    }

    fun onCategorySelected(category: ChannelCategory) {
        if (_uiState.value.selectedCategory == category && _uiState.value.videos.isNotEmpty()) return
        _uiState.update { it.copy(selectedCategory = category) }

        if (_uiState.value.isAllowed && _uiState.value.isActive) {
            loadCategoryVideos(category, isRefresh = false)
        }
    }

    fun loadCategoryVideos(category: ChannelCategory, isRefresh: Boolean) {
        val targetId = canonicalChannelId ?: channelIdParam.ifBlank { channelNameParam }
        if (targetId.isBlank()) return

        videoLoadJob?.cancel()
        videoLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingTab = !isRefresh,
                    isRefreshing = isRefresh,
                    videos = if (!isRefresh) emptyList() else it.videos,
                )
            }

            val result = youTubeRepository.fetchChannelTab(targetId, category.tabKey)
            activeTabExtractor = result.tabExtractor
            nextPage = result.nextPage

            _uiState.update {
                it.copy(
                    videos = result.items,
                    hasNextPage = result.hasNextPage,
                    isLoadingTab = false,
                    isRefreshing = false,
                )
            }
        }
    }

    fun loadMoreVideos() {
        val extractor = activeTabExtractor ?: return
        val currentNext = nextPage ?: return
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasNextPage) return

        val targetId = canonicalChannelId ?: channelIdParam.ifBlank { channelNameParam }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val (newItems, updatedNextPage) = youTubeRepository.fetchChannelTabPage(
                tabExtractor = extractor,
                page = currentNext,
                canonicalId = targetId,
            )

            nextPage = updatedNextPage
            val hasNext = updatedNextPage != null

            _uiState.update {
                val combined = (it.videos + newItems).distinctBy { v -> v.url.ifBlank { v.videoId } }
                it.copy(
                    videos = combined,
                    hasNextPage = hasNext,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun onRefresh() {
        loadChannelProfile(isRefresh = true)
        if (_uiState.value.isAllowed && _uiState.value.isActive) {
            loadCategoryVideos(_uiState.value.selectedCategory, isRefresh = true)
        }
    }

    fun onAllowChannelClick() {
        viewModelScope.launch {
            val hasPassword = channelRepository.hasPassword()
            _uiState.update {
                it.copy(
                    showPasswordDialog = true,
                    isFirstTimePasswordSetup = !hasPassword,
                    passwordInput = "",
                    confirmPasswordInput = "",
                    passwordError = null,
                )
            }
        }
    }

    fun onPasswordInputChange(input: String) {
        _uiState.update { it.copy(passwordInput = input, passwordError = null) }
    }

    fun onConfirmPasswordInputChange(input: String) {
        _uiState.update { it.copy(confirmPasswordInput = input, passwordError = null) }
    }

    fun dismissPasswordDialog() {
        _uiState.update {
            it.copy(
                showPasswordDialog = false,
                passwordInput = "",
                confirmPasswordInput = "",
                passwordError = null,
                isSubmittingAction = false,
            )
        }
    }

    fun onConfirmPasswordSubmit() {
        val state = _uiState.value
        val channel = state.channel
        val targetId = channel?.channelId?.ifBlank { canonicalChannelId ?: channelIdParam }
            ?: (canonicalChannelId ?: channelIdParam)
        val targetName = channel?.name?.ifBlank { channelNameParam } ?: channelNameParam

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingAction = true, passwordError = null) }

            if (state.isFirstTimePasswordSetup) {
                val pass = state.passwordInput
                val confirm = state.confirmPasswordInput

                if (pass.isBlank()) {
                    _uiState.update { it.copy(isSubmittingAction = false, passwordError = "Please enter a password") }
                    return@launch
                }
                if (pass != confirm) {
                    _uiState.update { it.copy(isSubmittingAction = false, passwordError = "Passwords do not match") }
                    return@launch
                }

                val hash = PasswordSecurity.hash(pass)
                channelRepository.setPasswordHash(hash)

                val entity = ChannelEntity(
                    channelId = targetId,
                    name = targetName,
                    thumbnailUrl = channel?.thumbnailUrl,
                    activationTimestamp = null,
                    addedAt = System.currentTimeMillis(),
                )
                channelRepository.addChannel(entity)

                _uiState.update {
                    it.copy(
                        showPasswordDialog = false,
                        isSubmittingAction = false,
                        isAllowed = true,
                        isActive = true,
                    )
                }
                // First batch is active immediately; load videos right away
                loadCategoryVideos(_uiState.value.selectedCategory, isRefresh = false)
            } else {
                val pass = state.passwordInput
                if (pass.isBlank()) {
                    _uiState.update { it.copy(isSubmittingAction = false, passwordError = "Please enter your password") }
                    return@launch
                }

                val storedHash = channelRepository.getPasswordHash()
                if (storedHash == null || !PasswordSecurity.verify(pass, storedHash)) {
                    _uiState.update { it.copy(isSubmittingAction = false, passwordError = "Incorrect password") }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val activationTime = now + ChannelConfig.ACTIVATION_DELAY_MS
                val entity = ChannelEntity(
                    channelId = targetId,
                    name = targetName,
                    thumbnailUrl = channel?.thumbnailUrl,
                    activationTimestamp = activationTime,
                    addedAt = now,
                )
                channelRepository.addChannel(entity)

                _uiState.update {
                    it.copy(
                        showPasswordDialog = false,
                        isSubmittingAction = false,
                        isAllowed = true,
                        isActive = false,
                        videos = emptyList(),
                    )
                }
            }
        }
    }

    fun onRemoveChannelClick() {
        _uiState.update { it.copy(showRemoveConfirmDialog = true) }
    }

    fun dismissRemoveConfirmDialog() {
        _uiState.update { it.copy(showRemoveConfirmDialog = false) }
    }

    fun onConfirmRemove() {
        val channel = _uiState.value.channel

        viewModelScope.launch {
            val channels = channelRepository.channels.first()
            val matching = channels.firstOrNull { ch ->
                matchesChannel(ch, channel, canonicalChannelId)
            }
            val targetId = matching?.channelId
                ?: channel?.channelId?.ifBlank { canonicalChannelId ?: channelIdParam }
                ?: (canonicalChannelId ?: channelIdParam)

            channelRepository.removeChannel(targetId)
            _uiState.update {
                it.copy(
                    showRemoveConfirmDialog = false,
                    isAllowed = false,
                    isActive = false,
                    remainingCountdown = null,
                    videos = emptyList(),
                )
            }
        }
    }
}
