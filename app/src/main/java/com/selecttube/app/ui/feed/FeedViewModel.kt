package com.selecttube.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    repository: ChannelRepository,
) : ViewModel() {

    /**
     * Only the channels that are currently active.
     * Status is re-evaluated live from the Flow; no stored enum.
     */
    val activeChannels: StateFlow<List<Channel>> = repository.channels
        .map { all -> all.filter { it.isActive } }
        .stateIn(
            scope             = viewModelScope,
            started           = SharingStarted.WhileSubscribed(5_000),
            initialValue      = emptyList(),
        )
}
