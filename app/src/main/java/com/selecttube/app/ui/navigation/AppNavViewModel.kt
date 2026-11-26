package com.selecttube.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selecttube.app.config.ChannelConfig
import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.preferences.AppMode
import com.selecttube.app.data.repository.ChannelRepository
import com.selecttube.app.data.youtube.ResolvedPlaybackInfo
import com.selecttube.app.data.youtube.StreamResolver
import com.selecttube.app.data.youtube.YouTubeRepository
import com.selecttube.app.deeplink.DeepLinkManager
import com.selecttube.app.util.PasswordSecurity
import com.selecttube.app.util.YouTubeLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavCommand {
    data class PlayVideo(
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String?,
    ) : NavCommand()

    data class OpenPlaylist(val playlistId: String) : NavCommand()

    data class OpenChannel(val channelId: String, val channelName: String) : NavCommand()
}

sealed class IncomingLinkDialogState {
    object None : IncomingLinkDialogState()
    data class Resolving(val message: String = "Resolving link...") : IncomingLinkDialogState()

    data class ChannelNotAllowed(
        val videoId: String?,
        val channelId: String,
        val channelName: String,
        val channelThumbnailUrl: String?,
        val videoTitle: String?,
        val isResolvingAdd: Boolean = false,
        val passwordInput: String = "",
        val errorMessage: String? = null,
    ) : IncomingLinkDialogState()

    data class MessageDialog(
        val title: String,
        val message: String,
    ) : IncomingLinkDialogState()
}

@HiltViewModel
class AppNavViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val deepLinkManager: DeepLinkManager,
    private val streamResolver: StreamResolver,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean?> = channelRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appMode: StateFlow<AppMode> = channelRepository.appMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.ADULT)

    private val _navCommands = Channel<NavCommand>(Channel.BUFFERED)
    val navCommands = _navCommands.receiveAsFlow()

    private val _dialogState = MutableStateFlow<IncomingLinkDialogState>(IncomingLinkDialogState.None)
    val dialogState: StateFlow<IncomingLinkDialogState> = _dialogState.asStateFlow()

    init {
        observeDeepLinks()
    }

    private fun observeDeepLinks() {
        viewModelScope.launch {
            deepLinkManager.events.collect { link ->
                deepLinkManager.clearEvent()
                handleIncomingLink(link)
            }
        }
    }

    private fun handleIncomingLink(link: YouTubeLink) {
        viewModelScope.launch {
            when (link) {
                is YouTubeLink.Video -> processVideoLink(link.videoId)
                is YouTubeLink.Playlist -> _navCommands.send(NavCommand.OpenPlaylist(link.playlistId))
                is YouTubeLink.Channel -> processChannelLink(link.channelQueryOrUrl)
            }
        }
    }

    private suspend fun processVideoLink(videoId: String) {
        _dialogState.value = IncomingLinkDialogState.Resolving("Checking video & channel...")
        try {
            val playbackInfo = streamResolver.resolveStream(videoId)
            val channel = channelRepository.getChannel(playbackInfo.channelId)

            if (channel != null && channel.isActive) {
                _dialogState.value = IncomingLinkDialogState.None
                _navCommands.send(
                    NavCommand.PlayVideo(
                        videoId = playbackInfo.videoId,
                        title = playbackInfo.title,
                        channelName = playbackInfo.channelName,
                        thumbnailUrl = playbackInfo.thumbnailUrl,
                    )
                )
            } else {
                _dialogState.value = IncomingLinkDialogState.ChannelNotAllowed(
                    videoId = playbackInfo.videoId,
                    channelId = playbackInfo.channelId,
                    channelName = playbackInfo.channelName,
                    channelThumbnailUrl = playbackInfo.thumbnailUrl,
                    videoTitle = playbackInfo.title,
                )
            }
        } catch (e: Exception) {
            _dialogState.value = IncomingLinkDialogState.MessageDialog(
                title = "Unable to Load Video",
                message = e.localizedMessage ?: "Could not resolve video information.",
            )
        }
    }

    private suspend fun processChannelLink(queryOrUrl: String) {
        _dialogState.value = IncomingLinkDialogState.Resolving("Searching channel...")
        try {
            val results = youTubeRepository.searchChannels(queryOrUrl)
            val channel = results.firstOrNull()
            if (channel == null) {
                _dialogState.value = IncomingLinkDialogState.MessageDialog(
                    title = "Channel Not Found",
                    message = "Could not find any YouTube channel matching: $queryOrUrl",
                )
                return
            }

            val existing = channelRepository.getChannel(channel.channelId)
            if (existing != null && existing.isActive) {
                _dialogState.value = IncomingLinkDialogState.None
                _navCommands.send(NavCommand.OpenChannel(channel.channelId, channel.name))
            } else {
                _dialogState.value = IncomingLinkDialogState.ChannelNotAllowed(
                    videoId = null,
                    channelId = channel.channelId,
                    channelName = channel.name,
                    channelThumbnailUrl = channel.thumbnailUrl,
                    videoTitle = null,
                )
            }
        } catch (e: Exception) {
            _dialogState.value = IncomingLinkDialogState.MessageDialog(
                title = "Error",
                message = e.localizedMessage ?: "Failed to find channel.",
            )
        }
    }

    fun dismissDialog() {
        _dialogState.value = IncomingLinkDialogState.None
    }

    fun onPasswordChange(input: String) {
        val current = _dialogState.value as? IncomingLinkDialogState.ChannelNotAllowed ?: return
        _dialogState.value = current.copy(passwordInput = input, errorMessage = null)
    }

    fun confirmAddChannel() {
        val current = _dialogState.value as? IncomingLinkDialogState.ChannelNotAllowed ?: return

        viewModelScope.launch {
            _dialogState.update {
                if (it is IncomingLinkDialogState.ChannelNotAllowed) it.copy(isResolvingAdd = true, errorMessage = null) else it
            }

            val mode = appMode.value
            if (mode == AppMode.CHILD) {
                val stored = channelRepository.getPasswordHash()
                if (stored == null || !PasswordSecurity.verify(current.passwordInput, stored)) {
                    _dialogState.update {
                        if (it is IncomingLinkDialogState.ChannelNotAllowed) {
                            it.copy(isResolvingAdd = false, errorMessage = "Incorrect parent password.")
                        } else it
                    }
                    return@launch
                }
            }

            try {
                val now = System.currentTimeMillis()
                // In Adult Mode, delay is applied; in Child Mode with valid password, activated immediately
                val activationTime = if (mode == AppMode.ADULT) {
                    now + ChannelConfig.ACTIVATION_DELAY_MS
                } else {
                    null
                }

                val entity = ChannelEntity(
                    channelId = current.channelId,
                    name = current.channelName,
                    thumbnailUrl = current.channelThumbnailUrl,
                    activationTimestamp = activationTime,
                    addedAt = now,
                )

                channelRepository.addChannel(entity)

                if (activationTime == null) {
                    // Active immediately
                    _dialogState.value = IncomingLinkDialogState.None
                    if (current.videoId != null) {
                        _navCommands.send(
                            NavCommand.PlayVideo(
                                videoId = current.videoId,
                                title = current.videoTitle ?: "",
                                channelName = current.channelName,
                                thumbnailUrl = current.channelThumbnailUrl,
                            )
                        )
                    } else {
                        _navCommands.send(NavCommand.OpenChannel(current.channelId, current.channelName))
                    }
                } else {
                    // Cooling down
                    _dialogState.value = IncomingLinkDialogState.MessageDialog(
                        title = "Channel Added",
                        message = "\"${current.channelName}\" has been added and is currently cooling down. Videos will be available in 24 hours.",
                    )
                }
            } catch (e: Exception) {
                _dialogState.update {
                    if (it is IncomingLinkDialogState.ChannelNotAllowed) {
                        it.copy(isResolvingAdd = false, errorMessage = "Failed to add channel: ${e.message}")
                    } else it
                }
            }
        }
    }
}
