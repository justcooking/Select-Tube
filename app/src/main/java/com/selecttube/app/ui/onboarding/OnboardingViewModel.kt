package com.selecttube.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.util.PasswordSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedMode: AppMode = AppMode.ADULT,
    val showPasswordDialog: Boolean = false,
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val passwordError: String? = null,
    val isCompleting: Boolean = false,
    val isComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectMode(mode: AppMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onContinueClick() {
        if (_uiState.value.selectedMode == AppMode.CHILD) {
            // Child mode requires setting a Parent Master Password
            _uiState.update {
                it.copy(
                    showPasswordDialog = true,
                    passwordInput = "",
                    confirmPasswordInput = "",
                    passwordError = null,
                )
            }
        } else {
            // Adult mode requires NO password
            completeOnboarding(AppMode.ADULT, null)
        }
    }

    fun onPasswordInputChange(input: String) {
        _uiState.update { it.copy(passwordInput = input, passwordError = null) }
    }

    fun onConfirmPasswordInputChange(input: String) {
        _uiState.update { it.copy(confirmPasswordInput = input, passwordError = null) }
    }

    fun dismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialog = false, passwordError = null) }
    }

    fun submitParentPassword() {
        val state = _uiState.value
        val pass = state.passwordInput
        val confirm = state.confirmPasswordInput

        if (pass.isBlank()) {
            _uiState.update { it.copy(passwordError = "Please enter a parent password") }
            return
        }
        if (pass.length < 4) {
            _uiState.update { it.copy(passwordError = "Password must be at least 4 characters") }
            return
        }
        if (pass != confirm) {
            _uiState.update { it.copy(passwordError = "Passwords do not match") }
            return
        }

        val hash = PasswordSecurity.hash(pass)
        completeOnboarding(AppMode.CHILD, hash)
    }

    private fun completeOnboarding(mode: AppMode, passwordHash: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCompleting = true) }
            channelRepository.setAppMode(mode)
            if (passwordHash != null) {
                channelRepository.setPasswordHash(passwordHash)
                // Default in child mode: block shorts for child safety
                channelRepository.setParentalShortsDisabled(true)
            } else {
                channelRepository.setPasswordHash(null)
            }
            channelRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isCompleting = false, isComplete = true) }
        }
    }
}
