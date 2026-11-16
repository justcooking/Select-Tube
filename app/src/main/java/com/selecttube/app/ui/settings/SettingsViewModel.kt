package com.selecttube.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ChannelItemUiState(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    val isActive: Boolean,
    val remainingTimeFormatted: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    /**
     * All channels (active + pending), newest first.
     * Continuously evaluated against the current clock so countdowns
     * update every second and automatically transition to active.
     */
    val channels: StateFlow<List<ChannelItemUiState>> = combine(repository.channels, ticker) { channels, now ->
        channels.map { channel ->
            val isActive = channel.activationTimestamp == null || now >= channel.activationTimestamp
            val msUntilActive = if (channel.activationTimestamp == null) 0L else maxOf(0L, channel.activationTimestamp - now)
            val remainingFormatted = if (!isActive) {
                val hours = TimeUnit.MILLISECONDS.toHours(msUntilActive)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(msUntilActive) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(msUntilActive) % 60
                when {
                    hours > 0 -> "Active in ${hours}h ${minutes}m"
                    minutes > 0 -> "Active in ${minutes}m ${seconds}s"
                    else -> "Active in ${seconds}s"
                }
            } else null

            ChannelItemUiState(
                channelId = channel.channelId,
                name = channel.name,
                thumbnailUrl = channel.thumbnailUrl,
                isActive = isActive,
                remainingTimeFormatted = remainingFormatted,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Remove an active channel — no password, no wait. */
    fun removeChannel(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }

    /**
     * Cancel a pending channel add.
     * Functionally removes the pending channel without waiting or password.
     */
    fun cancelPending(channelId: String) {
        viewModelScope.launch { repository.removeChannel(channelId) }
    }
}
