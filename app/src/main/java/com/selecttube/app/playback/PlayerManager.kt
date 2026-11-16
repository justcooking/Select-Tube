package com.selecttube.app.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.selecttube.app.data.youtube.PlayableStream
import com.selecttube.app.data.youtube.ResolvedPlaybackInfo
import com.selecttube.app.data.youtube.StreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val playbackInfo: ResolvedPlaybackInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isResolving: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val videoQuality: String? = null,
)

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamResolver: StreamResolver,
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaybackInfo: ResolvedPlaybackInfo? = null
    private var lastUrlOrId: String? = null
    private var hasRetriedExpiration = false
    private var resolveJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L),
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _playbackState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = player.duration.coerceAtLeast(0L),
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }
        })

        // Periodic ticker for current position
        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun playVideo(
        urlOrId: String,
        initialTitle: String? = null,
        initialChannel: String? = null,
        initialThumbnail: String? = null,
    ) {
        // If the same video is already loaded and no error, just ensure playing
        if (lastUrlOrId == urlOrId && currentPlaybackInfo != null && _playbackState.value.error == null) {
            if (!player.isPlaying) {
                player.play()
            }
            return
        }

        lastUrlOrId = urlOrId
        hasRetriedExpiration = false
        resolveJob?.cancel()

        player.stop()
        player.clearMediaItems()

        _playbackState.update {
            PlaybackState(
                isResolving = true,
                error = null,
                playbackInfo = currentPlaybackInfo?.copy(
                    title = initialTitle ?: currentPlaybackInfo?.title.orEmpty(),
                    channelName = initialChannel ?: currentPlaybackInfo?.channelName.orEmpty(),
                    thumbnailUrl = initialThumbnail ?: currentPlaybackInfo?.thumbnailUrl,
                ),
            )
        }

        startForegroundService()

        resolveJob = scope.launch {
            try {
                val info = streamResolver.resolveStream(urlOrId)
                currentPlaybackInfo = info
                val quality = when (val s = info.stream) {
                    is PlayableStream.Adaptive -> s.videoQuality
                    is PlayableStream.Progressive -> s.quality
                }

                val mediaSource = buildMediaSource(info)
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true

                _playbackState.update {
                    it.copy(
                        playbackInfo = info,
                        isResolving = false,
                        error = null,
                        videoQuality = quality,
                        durationMs = (info.durationSeconds * 1000L).coerceAtLeast(0L),
                    )
                }
            } catch (e: Exception) {
                _playbackState.update {
                    it.copy(
                        isResolving = false,
                        error = e.localizedMessage ?: "Failed to resolve video stream.",
                    )
                }
            }
        }
    }

    fun retry() {
        val target = lastUrlOrId ?: return
        hasRetriedExpiration = false
        playVideo(target)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val newPos = (player.currentPosition + deltaMs).coerceAtMost(player.duration.coerceAtLeast(0L))
        seekTo(newPos)
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val newPos = (player.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(newPos)
    }

    private fun handlePlayerError(error: PlaybackException) {
        val cause = error.cause
        val is403 = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403 ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.message?.contains("403") == true ||
            cause?.message?.contains("403") == true

        if (is403 && !hasRetriedExpiration && currentPlaybackInfo != null) {
            hasRetriedExpiration = true
            val resumePosition = player.currentPosition
            scope.launch {
                try {
                    _playbackState.update { it.copy(isResolving = true, error = null) }
                    val freshInfo = streamResolver.reResolve(currentPlaybackInfo!!)
                    currentPlaybackInfo = freshInfo
                    val mediaSource = buildMediaSource(freshInfo)
                    player.setMediaSource(mediaSource, resumePosition)
                    player.prepare()
                    player.playWhenReady = true
                    _playbackState.update {
                        it.copy(
                            playbackInfo = freshInfo,
                            isResolving = false,
                            error = null,
                        )
                    }
                } catch (e: Exception) {
                    _playbackState.update {
                        it.copy(
                            isResolving = false,
                            error = "Video stream expired and could not be renewed. Tap Retry.",
                        )
                    }
                }
            }
        } else {
            val userFriendlyError = when {
                is403 -> "Stream expired. Tap Retry to reload."
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection lost. Please check your internet connection."
                else -> error.localizedMessage ?: "Playback error"
            }
            _playbackState.update {
                it.copy(
                    error = userFriendlyError,
                    isResolving = false,
                )
            }
        }
    }

    private fun buildMediaSource(playbackInfo: ResolvedPlaybackInfo): MediaSource {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val progressiveFactory = ProgressiveMediaSource.Factory(httpDataSourceFactory)

        val metadata = MediaMetadata.Builder()
            .setTitle(playbackInfo.title)
            .setArtist(playbackInfo.channelName)
            .setArtworkUri(playbackInfo.thumbnailUrl?.let { Uri.parse(it) })
            .build()

        return when (val stream = playbackInfo.stream) {
            is PlayableStream.Adaptive -> {
                val videoItem = MediaItem.Builder()
                    .setUri(Uri.parse(stream.videoUrl))
                    .setMediaMetadata(metadata)
                    .build()
                val audioItem = MediaItem.Builder()
                    .setUri(Uri.parse(stream.audioUrl))
                    .setMediaMetadata(metadata)
                    .build()
                val videoSource = progressiveFactory.createMediaSource(videoItem)
                val audioSource = progressiveFactory.createMediaSource(audioItem)
                MergingMediaSource(videoSource, audioSource)
            }
            is PlayableStream.Progressive -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(stream.streamUrl))
                    .setMediaMetadata(metadata)
                    .build()
                progressiveFactory.createMediaSource(mediaItem)
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(context, PlaybackService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Service will be managed directly
        }
    }
}
