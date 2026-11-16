package com.selecttube.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    repository: ChannelRepository,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    /**
     * Only the channels that are currently active.
     * Evaluated against the clock so pending channels that reach their activation
     * timestamp automatically become active in the feed.
     */
    val activeChannels: StateFlow<List<Channel>> = combine(repository.channels, ticker) { channels, now ->
        channels.filter { it.activationTimestamp == null || now >= it.activationTimestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
