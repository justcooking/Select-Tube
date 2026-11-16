package com.selecttube.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    /** All channels (active + pending), newest first. */
    val channels: StateFlow<List<Channel>> = repository.channels
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Remove an active channel — no password, no wait. */
    fun removeChannel(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }

    /**
     * Cancel a pending channel add.
     * Functionally identical to remove — just a differently labelled action in the UI.
     */
    fun cancelPending(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }
}
