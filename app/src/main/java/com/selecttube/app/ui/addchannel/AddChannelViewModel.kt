package com.selecttube.app.ui.addchannel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.youtube.ChannelNotFoundException
import com.selecttube.app.data.youtube.ResolvedChannel
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddChannelTab {
    SEARCH,
    DIRECT_INPUT,
}

data class StagedChannel(
    val channelId: String,
    val displayName: String,
    val thumbnailUrl: String?,
)

data class AddChannelUiState(
    val isFirstBatch: Boolean = true,
    val selectedTab: AddChannelTab = AddChannelTab.SEARCH,
    // Search tab
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<ResolvedChannel> = emptyList(),
    // Direct input tab
    val directInput: String = "",
    val isResolving: Boolean = false,
    // Staged review list
    val stagedChannels: List<StagedChannel> = emptyList(),
    // Password
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class AddChannelViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddChannelUiState())
    val uiState: StateFlow<AddChannelUiState> = _uiState.asStateFlow()

    init {
        checkIfFirstBatch()
    }

    private fun checkIfFirstBatch() {
        viewModelScope.launch {
            val hasPassword = channelRepository.hasPassword()
            _uiState.update { it.copy(isFirstBatch = !hasPassword) }
        }
    }

    fun onTabSelected(tab: AddChannelTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun onDirectInputChange(input: String) {
        _uiState.update { it.copy(directInput = input, errorMessage = null) }
    }

    fun onPasswordInputChange(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun onConfirmPasswordInputChange(confirm: String) {
        _uiState.update { it.copy(confirmPasswordInput = confirm, errorMessage = null) }
    }

    fun searchChannels() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a search query") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = youTubeRepository.searchChannels(query)
                if (results.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = emptyList(),
                            errorMessage = "No channels found matching '$query'",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = results,
                            errorMessage = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search failed: ${e.message ?: "network error"}",
                    )
                }
            }
        }
    }

    fun addResolvedChannelToStaged(channel: ResolvedChannel) {
        val state = _uiState.value

        // Check if already in staged list
        if (state.stagedChannels.any { it.channelId.equals(channel.channelId, ignoreCase = true) }) {
            _uiState.update { it.copy(errorMessage = "'${channel.name}' is already in your review list") }
            return
        }

        viewModelScope.launch {
            // Check if already in database
            val existing = channelRepository.getChannel(channel.channelId)
            if (existing != null) {
                _uiState.update {
                    it.copy(errorMessage = "'${channel.name}' is already subscribed in your channel list")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    stagedChannels = it.stagedChannels + StagedChannel(
                        channelId = channel.channelId,
                        displayName = channel.name,
                        thumbnailUrl = channel.thumbnailUrl,
                    ),
                    errorMessage = null,
                )
            }
        }
    }

    fun resolveAndStageDirectInput() {
        val input = _uiState.value.directInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a channel ID, URL, or handle") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true, errorMessage = null) }
            try {
                val resolved = youTubeRepository.resolveChannel(input)
                addResolvedChannelToStaged(resolved)
                _uiState.update { it.copy(isResolving = false, directInput = "") }
            } catch (e: ChannelNotFoundException) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        errorMessage = e.message ?: "Channel could not be found.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        errorMessage = "Failed to resolve channel: ${e.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }

    fun removeStagedChannel(channelId: String) {
        _uiState.update { state ->
            state.copy(
                stagedChannels = state.stagedChannels.filterNot { it.channelId == channelId },
                errorMessage = null,
            )
        }
    }

    fun confirmBatch() {
        val state = _uiState.value
        val staged = state.stagedChannels

        if (staged.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Please add at least one channel to the review list")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            if (state.isFirstBatch) {
                // First-ever batch setup: password entry twice
                val password = state.passwordInput
                val confirmPassword = state.confirmPasswordInput

                if (password.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Please set a password")
                    }
                    return@launch
                }
                if (password != confirmPassword) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Passwords do not match")
                    }
                    return@launch
                }

                val hash = PasswordSecurity.hash(password)
                channelRepository.setPasswordHash(hash)

                // First-ever batch: active immediately
                val now = System.currentTimeMillis()
                val entities = staged.map { item ->
                    ChannelEntity(
                        channelId = item.channelId,
                        name = item.displayName,
                        thumbnailUrl = item.thumbnailUrl,
                        activationTimestamp = null,
                        addedAt = now,
                    )
                }
                channelRepository.addChannels(entities)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                // Subsequent batch: verify existing password
                val password = state.passwordInput
                if (password.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Please enter your password")
                    }
                    return@launch
                }

                val storedHash = channelRepository.getPasswordHash()
                if (storedHash == null || !PasswordSecurity.verify(password, storedHash)) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Incorrect password")
                    }
                    return@launch
                }

                // Subsequent batch: shared 2-hour wait
                val now = System.currentTimeMillis()
                val batchActivationTime = now + ChannelConfig.ACTIVATION_DELAY_MS
                val entities = staged.map { item ->
                    ChannelEntity(
                        channelId = item.channelId,
                        name = item.displayName,
                        thumbnailUrl = item.thumbnailUrl,
                        activationTimestamp = batchActivationTime,
                        addedAt = now,
                    )
                }
                channelRepository.addChannels(entities)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
        }
    }
}
