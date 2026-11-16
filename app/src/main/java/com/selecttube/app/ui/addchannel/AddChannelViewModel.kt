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

data class AddChannelUiState(
    val isFirstChannel: Boolean = true,
    val channelInput: String = "",
    val channelName: String = "",
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
        checkIfFirstChannel()
    }

    private fun checkIfFirstChannel() {
        viewModelScope.launch {
            val hasPassword = repository.hasPassword()
            _uiState.update { it.copy(isFirstChannel = !hasPassword) }
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

    fun addChannel() {
        val state = _uiState.value
        val rawInput = state.channelInput.trim()

        if (rawInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a channel ID or URL") }
            return
        }

        val channelId = parseChannelIdentifier(rawInput)
        val channelName = state.channelName.trim().ifBlank { channelId }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Check if already added
            val existing = repository.getChannel(channelId)
            if (existing != null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "This channel is already added")
                }
                return@launch
            }

            if (state.isFirstChannel) {
                // First channel setup: requires setting a password with confirmation
                val password = state.passwordInput
                val confirmPassword = state.confirmPasswordInput

                if (password.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Please enter a password")
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

                // First channel is active immediately (activationTimestamp = null)
                val entity = ChannelEntity(
                    channelId = channelId,
                    name = channelName,
                    thumbnailUrl = null,
                    activationTimestamp = null,
                    addedAt = System.currentTimeMillis(),
                )
                repository.addChannel(entity)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                // Subsequent channel: requires existing password verification
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

                // Pending channel: activates after configured wait duration
                val activationTime = System.currentTimeMillis() + ChannelConfig.ACTIVATION_DELAY_MS
                val entity = ChannelEntity(
                    channelId = channelId,
                    name = channelName,
                    thumbnailUrl = null,
                    activationTimestamp = activationTime,
                    addedAt = System.currentTimeMillis(),
                )
                repository.addChannel(entity)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
        }
    }

    private fun parseChannelIdentifier(input: String): String {
        var trimmed = input.trim()
        // Strip trailing slash if present
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }

        // If it's a URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // e.g. https://www.youtube.com/channel/UCxxxxxx
            val channelIdx = trimmed.indexOf("/channel/")
            if (channelIdx != -1) {
                val after = trimmed.substring(channelIdx + "/channel/".length)
                return after.substringBefore("/").substringBefore("?")
            }

            // e.g. https://www.youtube.com/@handle
            val atIdx = trimmed.indexOf("/@")
            if (atIdx != -1) {
                val after = trimmed.substring(atIdx + 1) // includes '@'
                return after.substringBefore("/").substringBefore("?")
            }

            // e.g. https://www.youtube.com/c/CustomName or /user/Name
            val lastSegment = trimmed.substringAfterLast("/")
            if (lastSegment.isNotBlank()) {
                return lastSegment.substringBefore("?")
            }
        }

        return trimmed
    }
}
