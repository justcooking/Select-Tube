package com.selecttube.app.ui.addchannel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StagedChannel(
    val channelId: String,
    val displayName: String,
)

data class AddChannelUiState(
    val isFirstBatch: Boolean = true,
    val channelInput: String = "",
    val channelName: String = "",
    val stagedChannels: List<StagedChannel> = emptyList(),
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class AddChannelViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddChannelUiState())
    val uiState: StateFlow<AddChannelUiState> = _uiState.asStateFlow()

    init {
        checkIfFirstBatch()
    }

    private fun checkIfFirstBatch() {
        viewModelScope.launch {
            val hasPassword = repository.hasPassword()
            _uiState.update { it.copy(isFirstBatch = !hasPassword) }
        }
    }

    fun onChannelInputChange(input: String) {
        _uiState.update { it.copy(channelInput = input, errorMessage = null) }
    }

    fun onChannelNameChange(name: String) {
        _uiState.update { it.copy(channelName = name, errorMessage = null) }
    }

    fun onPasswordInputChange(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun onConfirmPasswordInputChange(confirm: String) {
        _uiState.update { it.copy(confirmPasswordInput = confirm, errorMessage = null) }
    }

    fun stageChannel() {
        val state = _uiState.value
        val rawInput = state.channelInput.trim()

        if (rawInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a channel ID or URL") }
            return
        }

        val channelId = parseChannelIdentifier(rawInput)
        val displayName = state.channelName.trim().ifBlank { channelId }

        // Check if already in staged list
        if (state.stagedChannels.any { it.channelId.equals(channelId, ignoreCase = true) }) {
            _uiState.update { it.copy(errorMessage = "Channel is already in the review list") }
            return
        }

        viewModelScope.launch {
            // Check if already saved in database
            val existing = repository.getChannel(channelId)
            if (existing != null) {
                _uiState.update {
                    it.copy(errorMessage = "This channel is already subscribed in your channel list")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    stagedChannels = it.stagedChannels + StagedChannel(channelId, displayName),
                    channelInput = "",
                    channelName = "",
                    errorMessage = null,
                )
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
            // If user typed something in input without clicking "Add to list", prompt them
            if (state.channelInput.isNotBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Tap 'Add to List' first to include your entered channel")
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Please add at least one channel to the review list")
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            if (state.isFirstBatch) {
                // First-ever batch setup: requires setting a password with confirmation
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

                // Hash and store password
                val hash = PasswordSecurity.hash(password)
                repository.setPasswordHash(hash)

                // First-ever batch: all channels are active immediately (activationTimestamp = null)
                val now = System.currentTimeMillis()
                val entities = staged.map { item ->
                    ChannelEntity(
                        channelId = item.channelId,
                        name = item.displayName,
                        thumbnailUrl = null,
                        activationTimestamp = null,
                        addedAt = now,
                    )
                }
                repository.addChannels(entities)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                // Subsequent batch: requires existing password verification
                val password = state.passwordInput
                if (password.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Please enter your password")
                    }
                    return@launch
                }

                val storedHash = repository.getPasswordHash()
                if (storedHash == null || !PasswordSecurity.verify(password, storedHash)) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Incorrect password")
                    }
                    return@launch
                }

                // Shared activation timestamp for the whole batch
                val now = System.currentTimeMillis()
                val batchActivationTime = now + ChannelConfig.ACTIVATION_DELAY_MS
                val entities = staged.map { item ->
                    ChannelEntity(
                        channelId = item.channelId,
                        name = item.displayName,
                        thumbnailUrl = null,
                        activationTimestamp = batchActivationTime,
                        addedAt = now,
                    )
                }
                repository.addChannels(entities)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
        }
    }

    private fun parseChannelIdentifier(input: String): String {
        var trimmed = input.trim()
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val channelIdx = trimmed.indexOf("/channel/")
            if (channelIdx != -1) {
                val after = trimmed.substring(channelIdx + "/channel/".length)
                return after.substringBefore("/").substringBefore("?")
            }

            val atIdx = trimmed.indexOf("/@")
            if (atIdx != -1) {
                val after = trimmed.substring(atIdx + 1)
                return after.substringBefore("/").substringBefore("?")
            }

            val lastSegment = trimmed.substringAfterLast("/")
            if (lastSegment.isNotBlank()) {
                return lastSegment.substringBefore("?")
            }
        }

        return trimmed
    }
}
