package com.selecttube.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.selecttube.app.data.youtube.PlayableStream
import com.selecttube.app.data.youtube.ResolvedPlaybackInfo
import com.selecttube.app.data.youtube.StreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val LOG_TAG = "SelectTubeMedia"
private const val NET_LOG_TAG = "SelectTubeNet"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

data class PlayQueueItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long = 0L,
)

data class SleepTimerInfo(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val isEndOfVideo: Boolean = false,
)

data class PlaybackState(
    val playbackInfo: ResolvedPlaybackInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isResolving: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val videoQuality: String? = null,
    val audioTrack: String? = null,
    val selectedSubtitle: String? = null,
    val isBackgroundOnly: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/**
 * Custom MediaSource.Factory that resolves YouTube video streams via an in-memory map.
 *
 * For any MediaItem whose mediaId exists in [streamMap], it builds:
 * - [MergingMediaSource] combining video and audio for [PlayableStream.Adaptive]
 * - [ProgressiveMediaSource] for [PlayableStream.Progressive]
 * Each child source is created from that same [MediaItem].
 * Any attached [MediaItem.subtitleConfigurations] are parsed and merged via [SubtitleExtractor].
 * Any other MediaItem is delegated to [DefaultMediaSourceFactory].
 */
private class CustomMediaSourceFactory(
    private val defaultFactory: DefaultMediaSourceFactory,
    private val progressiveFactory: ProgressiveMediaSource.Factory,
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
    private val streamMap: Map<String, PlayableStream>,
) : MediaSource.Factory by defaultFactory {

    private val subtitleParserFactory = DefaultSubtitleParserFactory()

    private fun createSubtitleMediaSource(config: MediaItem.SubtitleConfiguration): MediaSource {
        val format = Format.Builder()
            .setSampleMimeType(config.mimeType)
            .setLanguage(config.language)
            .setSelectionFlags(config.selectionFlags)
            .setRoleFlags(config.roleFlags)
            .setLabel(config.label)
            .setId(config.id)
            .build()

        val extractorsFactory = ExtractorsFactory {
            arrayOf(
                if (subtitleParserFactory.supportsFormat(format)) {
                    SubtitleExtractor(subtitleParserFactory.create(format), format)
                } else {
                    object : Extractor {
                        override fun init(output: ExtractorOutput) {}
                        override fun sniff(input: ExtractorInput): Boolean = false
                        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
                        override fun seek(position: Long, timeUs: Long) {}
                        override fun release() {}
                    }
                }
            )
        }

        return ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
            .createMediaSource(MediaItem.fromUri(config.uri))
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val stream = streamMap[mediaItem.mediaId]
            ?: return defaultFactory.createMediaSource(mediaItem)

        val subtitleConfigs = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
        val subtitleSources = subtitleConfigs.map { createSubtitleMediaSource(it) }

        return when (stream) {
            is PlayableStream.Adaptive -> {
                val videoItem = mediaItem.buildUpon()
                    .setUri(Uri.parse(stream.videoUrl))
                    .setSubtitleConfigurations(emptyList())
                    .build()
                val audioItem = mediaItem.buildUpon()
                    .setUri(Uri.parse(stream.audioUrl))
                    .setSubtitleConfigurations(emptyList())
                    .build()
                val videoSource = progressiveFactory.createMediaSource(videoItem)
                val audioSource = progressiveFactory.createMediaSource(audioItem)
                if (subtitleSources.isEmpty()) {
                    MergingMediaSource(videoSource, audioSource)
                } else {
                    MergingMediaSource(
                        videoSource,
                        audioSource,
                        *subtitleSources.toTypedArray(),
                    )
                }
            }
            is PlayableStream.Progressive -> {
                val progressiveItem = mediaItem.buildUpon()
                    .setUri(Uri.parse(stream.streamUrl))
                    .setSubtitleConfigurations(emptyList())
                    .build()
                val progressiveSource = progressiveFactory.createMediaSource(progressiveItem)
                if (subtitleSources.isEmpty()) {
                    progressiveSource
                } else {
                    MergingMediaSource(
                        progressiveSource,
                        *subtitleSources.toTypedArray(),
                    )
                }
            }
        }
    }
}

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamResolver: StreamResolver,
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // In-memory map of videoId -> PlayableStream
    private val streamMap = ConcurrentHashMap<String, PlayableStream>()

    // Request counter for logging the first three data requests of each video
    private val netRequestCount = AtomicInteger(0)

    private val baseHttpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setKeepPostFor302Redirects(true)

    private val youtubeDataSourceFactory = YoutubeChunkedDataSourceFactory(
        baseHttpDataSourceFactory = baseHttpDataSourceFactory,
        onRequestLogged = { host, statusCode, ttfbMs ->
            val count = netRequestCount.incrementAndGet()
            if (count <= 3) {
                Log.d(NET_LOG_TAG, "Request #$count: host=$host, status=$statusCode, TTFB=${ttfbMs}ms")
            }
        },
    )

    private val progressiveFactory = ProgressiveMediaSource.Factory(youtubeDataSourceFactory)
    private val defaultMediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(youtubeDataSourceFactory)

    private val customFactory = CustomMediaSourceFactory(
        defaultFactory = defaultMediaSourceFactory,
        progressiveFactory = progressiveFactory,
        httpDataSourceFactory = baseHttpDataSourceFactory,
        streamMap = streamMap,
    )

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 1_500,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000,
        )
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>,
            ) {
                val renderer = TextRenderer(output, outputLooper)
                @Suppress("DEPRECATION")
                renderer.experimentalSetLegacyDecodingEnabled(true)
                out.add(renderer)
            }
        },
    )
        .setMediaSourceFactory(customFactory)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(10_000L)
        .build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())
    val queue: StateFlow<List<PlayQueueItem>> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<PlayQueueItem>>(emptyList())
    val history: StateFlow<List<PlayQueueItem>> = _history.asStateFlow()

    private val _sleepTimer = MutableStateFlow(SleepTimerInfo())
    val sleepTimer: StateFlow<SleepTimerInfo> = _sleepTimer.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var currentPlaybackInfo: ResolvedPlaybackInfo? = null
    private var lastUrlOrId: String? = null
    private var hasRetriedExpiration = false
    private var resolveJob: Job? = null

    // Persistent MediaController connection to PlaybackService so MediaSessionService
    // registers the session and drives the system media notification.
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        ensureMediaControllerConnected()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
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
                // Log when READY to verify metadata on the current MediaItem
                if (playbackState == Player.STATE_READY) {
                    Log.d(
                        LOG_TAG,
                        "STATE_READY — player.currentMediaItem?.mediaMetadata?.title='${player.currentMediaItem?.mediaMetadata?.title}' " +
                            "mediaMetadata.title='${player.mediaMetadata.title}' " +
                            "currentMediaItem?.mediaId='${player.currentMediaItem?.mediaId}'",
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    if (_sleepTimer.value.isActive && _sleepTimer.value.isEndOfVideo) {
                        cancelSleepTimer()
                        player.pause()
                    } else if (_queue.value.isNotEmpty()) {
                        scope.launch {
                            playNextFromQueue()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _playbackState.update {
                        it.copy(
                            videoWidth = videoSize.width,
                            videoHeight = videoSize.height,
                        )
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val size = player.videoSize
                if (size.width > 0 && size.height > 0) {
                    _playbackState.update {
                        it.copy(
                            videoWidth = size.width,
                            videoHeight = size.height,
                        )
                    }
                }
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

    fun enqueue(item: PlayQueueItem, playNext: Boolean = false) {
        // If nothing is currently loaded or playing, start playing immediately
        if (_playbackState.value.playbackInfo == null && !_playbackState.value.isResolving) {
            playVideo(
                urlOrId = item.videoId,
                initialTitle = item.title,
                initialChannel = item.channelName,
                initialThumbnail = item.thumbnailUrl,
            )
            return
        }

        _queue.update { current ->
            if (playNext) {
                listOf(item) + current
            } else {
                current + item
            }
        }
    }

    fun playQueue(items: List<PlayQueueItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val target = items.getOrNull(startIndex) ?: items.first()
        val remainder = items.filterIndexed { index, _ -> index != startIndex }
        _queue.value = remainder
        playVideo(
            urlOrId = target.videoId,
            initialTitle = target.title,
            initialChannel = target.channelName,
            initialThumbnail = target.thumbnailUrl,
        )
    }

    fun playNextFromQueue() {
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return
        val nextItem = currentQueue.first()
        _queue.value = currentQueue.drop(1)
        playVideo(
            urlOrId = nextItem.videoId,
            initialTitle = nextItem.title,
            initialChannel = nextItem.channelName,
            initialThumbnail = nextItem.thumbnailUrl,
        )
    }

    fun playPrevious() {
        // If played more than 3 seconds, restart current video
        if (player.currentPosition > 3_000L) {
            seekTo(0L)
            if (!player.isPlaying) {
                player.play()
            }
            return
        }

        val historyList = _history.value
        if (historyList.isEmpty()) {
            seekTo(0L)
            return
        }

        val prevItem = historyList.first()
        _history.value = historyList.drop(1)

        // Put current video back at the front of the queue so user can skip forward again
        currentPlaybackInfo?.let { curr ->
            val currItem = PlayQueueItem(
                videoId = curr.videoId,
                title = curr.title,
                channelName = curr.channelName,
                thumbnailUrl = curr.thumbnailUrl,
                durationSeconds = curr.durationSeconds,
            )
            _queue.update { listOf(currItem) + it }
        }

        playVideo(
            urlOrId = prevItem.videoId,
            initialTitle = prevItem.title,
            initialChannel = prevItem.channelName,
            initialThumbnail = prevItem.thumbnailUrl,
        )
    }

    fun playQueueIndex(index: Int) {
        val currentQueue = _queue.value
        if (index !in currentQueue.indices) return
        val target = currentQueue[index]
        _queue.value = currentQueue.filterIndexed { i, _ -> i != index }
        playVideo(
            urlOrId = target.videoId,
            initialTitle = target.title,
            initialChannel = target.channelName,
            initialThumbnail = target.thumbnailUrl,
        )
    }

    fun removeQueueItem(index: Int) {
        _queue.update { current ->
            current.filterIndexed { i, _ -> i != index }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun shuffleQueue() {
        _queue.update { current ->
            if (current.size <= 1) current
            else current.shuffled()
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val totalSec = minutes * 60L
        _sleepTimer.value = SleepTimerInfo(
            isActive = true,
            remainingSeconds = totalSec,
            isEndOfVideo = false,
        )
        sleepTimerJob = scope.launch {
            var remaining = totalSec
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _sleepTimer.update { it.copy(remainingSeconds = remaining) }
            }
            if (isActive) {
                player.pause()
                _sleepTimer.value = SleepTimerInfo()
            }
        }
    }

    fun setSleepTimerEndOfVideo() {
        sleepTimerJob?.cancel()
        _sleepTimer.value = SleepTimerInfo(
            isActive = true,
            remainingSeconds = 0L,
            isEndOfVideo = true,
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimer.value = SleepTimerInfo()
    }

    fun playVideo(
        urlOrId: String,
        initialTitle: String? = null,
        initialChannel: String? = null,
        initialThumbnail: String? = null,
        startPositionMs: Long = 0L,
    ) {
        netRequestCount.set(0)
        // If the same video is already loaded and no error, just ensure playing
        if (lastUrlOrId == urlOrId && currentPlaybackInfo != null && _playbackState.value.error == null) {
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
            if (!player.isPlaying) {
                player.play()
            }
            startPlaybackService()
            return
        }

        // Save previous video to history
        currentPlaybackInfo?.let { prev ->
            if (prev.videoId.isNotBlank() && prev.videoId != urlOrId) {
                val prevItem = PlayQueueItem(
                    videoId = prev.videoId,
                    title = prev.title,
                    channelName = prev.channelName,
                    thumbnailUrl = prev.thumbnailUrl,
                    durationSeconds = prev.durationSeconds,
                )
                _history.update { current ->
                    (listOf(prevItem) + current.filter { it.videoId != prev.videoId }).take(50)
                }
            }
        }

        lastUrlOrId = urlOrId
        hasRetriedExpiration = false
        resolveJob?.cancel()

        if (_playbackState.value.isBackgroundOnly) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }

        player.stop()
        player.clearMediaItems()

        val initialMetadata = MediaMetadata.Builder()
            .setTitle(initialTitle ?: currentPlaybackInfo?.title ?: "Loading...")
            .setArtist(initialChannel ?: currentPlaybackInfo?.channelName ?: "")
            .setArtworkUri(
                initialThumbnail?.let { Uri.parse(it) }
                    ?: currentPlaybackInfo?.thumbnailUrl?.let { Uri.parse(it) },
            )
            .build()
        player.setPlaylistMetadata(initialMetadata)

        val placeholderInfo = currentPlaybackInfo?.copy(
            videoId = urlOrId,
            title = initialTitle ?: currentPlaybackInfo?.title.orEmpty(),
            channelName = initialChannel ?: currentPlaybackInfo?.channelName.orEmpty(),
            thumbnailUrl = initialThumbnail ?: currentPlaybackInfo?.thumbnailUrl,
        ) ?: ResolvedPlaybackInfo(
            videoId = urlOrId,
            title = initialTitle.orEmpty().ifBlank { "Loading..." },
            channelName = initialChannel.orEmpty(),
            channelId = "",
            thumbnailUrl = initialThumbnail,
            durationSeconds = 0L,
            stream = PlayableStream.Progressive("", ""),
            originalUrl = urlOrId,
        )

        _playbackState.update {
            PlaybackState(
                isResolving = true,
                error = null,
                playbackInfo = placeholderInfo,
            )
        }

        startPlaybackService()

        resolveJob = scope.launch {
            try {
                val info = streamResolver.resolveStream(urlOrId)
                currentPlaybackInfo = info
                val quality = when (val s = info.stream) {
                    is PlayableStream.Adaptive -> s.videoQuality
                    is PlayableStream.Progressive -> s.quality
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(info.title)
                    .setArtist(info.channelName)
                    .setArtworkUri(info.thumbnailUrl?.let { Uri.parse(it) })
                    .build()
                player.setPlaylistMetadata(metadata)

                // 1. Put the PlayableStream in the map
                streamMap[info.videoId] = info.stream

                // 2. Build the MediaItem with videoId as mediaId, watch URL as URI, and metadata
                val mediaItem = MediaItem.Builder()
                    .setMediaId(info.videoId)
                    .setUri(Uri.parse(info.originalUrl))
                    .setMediaMetadata(metadata)
                    .build()

                // Reset subtitles to off on new video
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()

                // 3. Set MediaItem, prepare, and play (standard Media3 pattern)
                player.setMediaItem(mediaItem)
                if (startPositionMs > 0L) {
                    player.seekTo(startPositionMs)
                }
                player.prepare()
                player.play()

                val audioTrackLabel = when (info.stream) {
                    is PlayableStream.Adaptive -> info.stream.audioTrack ?: info.audioTrack
                    is PlayableStream.Progressive -> info.audioTrack
                }

                _playbackState.update {
                    it.copy(
                        playbackInfo = info,
                        isResolving = false,
                        error = null,
                        videoQuality = quality,
                        audioTrack = audioTrackLabel,
                        selectedSubtitle = null,
                        durationMs = (info.durationSeconds * 1000L).coerceAtLeast(0L),
                        videoWidth = info.videoWidth,
                        videoHeight = info.videoHeight,
                    )
                }
            } catch (e: CancellationException) {
                // Do not surface coroutine cancellation as an error
                throw e
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
            startPlaybackService()
        }
    }

    fun stop() {
        cancelSleepTimer()
        resolveJob?.cancel()
        if (_playbackState.value.isBackgroundOnly) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }
        player.stop()
        player.clearMediaItems()
        currentPlaybackInfo = null
        lastUrlOrId = null
        _playbackState.update { PlaybackState() }
    }

    fun setBackgroundOnly(enabled: Boolean) {
        if (_playbackState.value.isBackgroundOnly == enabled) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
            .build()
        _playbackState.update { it.copy(isBackgroundOnly = enabled) }
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

    fun setQuality(quality: String) {
        val currentInfo = currentPlaybackInfo ?: return
        val currentAudio = _playbackState.value.audioTrack
        val newStream = streamResolver.resolveQuality(currentInfo.originalUrl, quality, currentAudio) ?: return

        val qualityLabel = when (newStream) {
            is PlayableStream.Adaptive -> newStream.videoQuality
            is PlayableStream.Progressive -> newStream.quality
        }
        val audioLabel = when (newStream) {
            is PlayableStream.Adaptive -> newStream.audioTrack ?: currentAudio
            is PlayableStream.Progressive -> currentAudio
        }

        val newInfo = currentInfo.copy(stream = newStream, audioTrack = audioLabel)
        currentPlaybackInfo = newInfo
        streamMap[newInfo.videoId] = newStream

        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying

        val metadata = MediaMetadata.Builder()
            .setTitle(newInfo.title)
            .setArtist(newInfo.channelName)
            .setArtworkUri(newInfo.thumbnailUrl?.let { Uri.parse(it) })
            .build()
        player.setPlaylistMetadata(metadata)

        val selectedSub = _playbackState.value.selectedSubtitle?.let { name ->
            newInfo.availableSubtitles.firstOrNull { it.displayName == name }
        }
        val subtitleConfigs = if (selectedSub != null) {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(selectedSub.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(selectedSub.languageCode)
                    .setLabel(selectedSub.displayName)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        } else {
            emptyList()
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(newInfo.videoId)
            .setUri(Uri.parse(newInfo.originalUrl))
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        player.setMediaItem(mediaItem, currentPos)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }

        _playbackState.update {
            it.copy(
                playbackInfo = newInfo,
                videoQuality = qualityLabel,
                audioTrack = audioLabel,
            )
        }
    }

    fun setAudioTrack(trackName: String) {
        val currentInfo = currentPlaybackInfo ?: return
        val currentQuality = _playbackState.value.videoQuality
        val newStream = streamResolver.resolveAudioTrack(currentInfo.originalUrl, trackName, currentQuality) ?: return

        val newInfo = currentInfo.copy(stream = newStream, audioTrack = trackName)
        currentPlaybackInfo = newInfo
        streamMap[newInfo.videoId] = newStream

        val qualityLabel = when (newStream) {
            is PlayableStream.Adaptive -> newStream.videoQuality
            is PlayableStream.Progressive -> newStream.quality
        }

        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying

        val metadata = MediaMetadata.Builder()
            .setTitle(newInfo.title)
            .setArtist(newInfo.channelName)
            .setArtworkUri(newInfo.thumbnailUrl?.let { Uri.parse(it) })
            .build()
        player.setPlaylistMetadata(metadata)

        val selectedSub = _playbackState.value.selectedSubtitle?.let { name ->
            newInfo.availableSubtitles.firstOrNull { it.displayName == name }
        }
        val subtitleConfigs = if (selectedSub != null) {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(selectedSub.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(selectedSub.languageCode)
                    .setLabel(selectedSub.displayName)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        } else {
            emptyList()
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(newInfo.videoId)
            .setUri(Uri.parse(newInfo.originalUrl))
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        player.setMediaItem(mediaItem, currentPos)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }

        _playbackState.update {
            it.copy(
                playbackInfo = newInfo,
                videoQuality = qualityLabel,
                audioTrack = trackName,
            )
        }
    }

    fun setSubtitle(subtitleName: String?) {
        val currentInfo = currentPlaybackInfo ?: return
        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying
        val metadata = player.mediaMetadata

        if (subtitleName == null) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(currentInfo.videoId)
                .setUri(Uri.parse(currentInfo.originalUrl))
                .setMediaMetadata(metadata)
                .setSubtitleConfigurations(emptyList())
                .build()

            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()

            player.setMediaItem(mediaItem, currentPos)
            player.prepare()
            if (wasPlaying) {
                player.play()
            }

            _playbackState.update { it.copy(selectedSubtitle = null, error = null) }
            return
        }

        val targetSub = currentInfo.availableSubtitles.firstOrNull { it.displayName == subtitleName } ?: return

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(targetSub.url))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(targetSub.languageCode)
            .setLabel(targetSub.displayName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(currentInfo.videoId)
            .setUri(Uri.parse(currentInfo.originalUrl))
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .apply {
                if (targetSub.languageCode.isNotBlank()) {
                    setPreferredTextLanguage(targetSub.languageCode)
                }
            }
            .build()

        player.setMediaItem(mediaItem, currentPos)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }

        _playbackState.update { it.copy(selectedSubtitle = subtitleName, error = null) }
    }

    private fun handlePlayerError(error: PlaybackException) {
        Log.e(LOG_TAG, "ExoPlayer error: ${error.message}, errorCode=${error.errorCode}", error)
        val cause = error.cause

        val isSubtitleError = (error is ExoPlaybackException && (
            error.type == ExoPlaybackException.TYPE_RENDERER &&
            error.rendererFormat?.sampleMimeType?.contains("vtt", ignoreCase = true) == true
        )) || (
            _playbackState.value.selectedSubtitle != null && (
                error.message?.contains("subtitle", ignoreCase = true) == true ||
                error.message?.contains("Legacy decoding", ignoreCase = true) == true
            )
        )

        if (isSubtitleError && _playbackState.value.selectedSubtitle != null) {
            Log.w(LOG_TAG, "Subtitle track failed to decode; turning off subtitle so video continues smoothly.")
            setSubtitle(null)
            return
        }

        val is403 = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403 ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.message?.contains("403") == true ||
            cause?.message?.contains("403") == true

        if (is403 && !hasRetriedExpiration && currentPlaybackInfo != null) {
            hasRetriedExpiration = true
            val resumePosition = player.currentPosition
            netRequestCount.set(0)
            scope.launch {
                try {
                    _playbackState.update { it.copy(isResolving = true, error = null) }
                    val freshInfo = streamResolver.reResolve(currentPlaybackInfo!!)
                    currentPlaybackInfo = freshInfo
                    val metadata = MediaMetadata.Builder()
                        .setTitle(freshInfo.title)
                        .setArtist(freshInfo.channelName)
                        .setArtworkUri(freshInfo.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                    player.setPlaylistMetadata(metadata)

                    // Update map entry
                    streamMap[freshInfo.videoId] = freshInfo.stream

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(freshInfo.videoId)
                        .setUri(Uri.parse(freshInfo.originalUrl))
                        .setMediaMetadata(metadata)
                        .build()

                    player.setMediaItem(mediaItem, resumePosition)
                    player.seekTo(resumePosition)
                    player.prepare()
                    player.play()

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
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                    "Network connection lost. Please check your internet connection."
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

    private fun ensureMediaControllerConnected() {
        if (controllerFuture == null) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    mediaController = future.get()
                    Log.d(LOG_TAG, "MediaController connected: $mediaController")
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to connect MediaController", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun startPlaybackService() {
        ensureMediaControllerConnected()
        try {
            val intent = Intent(context, PlaybackService::class.java)
            // startForegroundService is required so the service can call startForeground()
            // within Android's 10-second window. Using plain startService() leaves the
            // service in the background, so the media notification is never posted.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            // Android 12+ restricts FGS start from the background.
            // This call is always triggered by a foreground user tap, so this should not
            // happen, but catch defensively.
        } catch (e: Exception) {
            // Catch other unexpected platform security/runtime exceptions.
        }
    }
}
