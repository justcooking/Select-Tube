package com.selecttube.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    channelRepository: ChannelRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean?> = channelRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appMode: StateFlow<AppMode> = channelRepository.appMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.ADULT)
}
