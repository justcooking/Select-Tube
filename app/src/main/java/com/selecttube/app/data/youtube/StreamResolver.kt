package com.selecttube.app.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlayableStream {
    data class Adaptive(
        val videoUrl: String,
        val audioUrl: String,
        val videoQuality: String,
        val audioBitrate: Int,
    ) : PlayableStream()

    data class Progressive(
        val streamUrl: String,
        val quality: String,
    ) : PlayableStream()
}

data class ResolvedPlaybackInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val stream: PlayableStream,
    val originalUrl: String,
    val viewCount: Long = -1L,
    val likeCount: Long = -1L,
    val subscriberCount: Long = -1L,
    val channelAvatarUrl: String? = null,
    val availableQualities: List<String> = emptyList(),
)

class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves YouTube video URLs into playable streams using NewPipe Extractor.
 * Completely free of Android UI and Media3 dependencies so it can be reused in web/desktop targets.
 */
@Singleton
class StreamResolver @Inject constructor() {

    private val youtubeService = ServiceList.YouTube
    private val streamInfoCache = java.util.concurrent.ConcurrentHashMap<String, StreamInfo>()

    /**
     * Resolves a video to playable streams.
     * Prefers the best video-only stream up to 720p merged with the best audio stream.
     * Falls back to the best progressive stream if adaptive streams are unavailable.
     */
    suspend fun resolveStream(urlOrVideoId: String): ResolvedPlaybackInfo = withContext(Dispatchers.IO) {
        val fullUrl = normalizeToWatchUrl(urlOrVideoId)

        val streamInfo = try {
            StreamInfo.getInfo(youtubeService, fullUrl)
        } catch (e: ExtractionException) {
            throw StreamResolutionException(
                "Cannot play this video: ${e.message ?: "it may be private, age-restricted, or removed"}.",
                e,
            )
        } catch (e: IOException) {
            throw StreamResolutionException("Network error while loading video stream.", e)
        } catch (e: Exception) {
            throw StreamResolutionException("Failed to resolve video: ${e.localizedMessage ?: "unknown error"}", e)
        }

        streamInfoCache[fullUrl] = streamInfo

        val videoId = extractVideoId(fullUrl)
        val title = streamInfo.name.orEmpty().ifBlank { "Untitled Video" }
        val channelName = streamInfo.uploaderName.orEmpty()
        val channelId = streamInfo.uploaderUrl?.substringAfterLast("/").orEmpty()
        val thumbnailUrl = streamInfo.thumbnails?.lastOrNull()?.url ?: streamInfo.thumbnails?.firstOrNull()?.url
        val durationSeconds = streamInfo.duration

        val viewCount = streamInfo.viewCount
        val likeCount = streamInfo.likeCount
        val subscriberCount = streamInfo.uploaderSubscriberCount
        val channelAvatarUrl = streamInfo.uploaderAvatars?.lastOrNull()?.url

        val availableQualities = (streamInfo.videoOnlyStreams.orEmpty() + streamInfo.videoStreams.orEmpty())
            .mapNotNull { if (it.height > 0) "${it.height}p" else null }
            .distinct()
            .sortedByDescending { it.removeSuffix("p").toIntOrNull() ?: 0 }

        val playableStream = choosePlayableStream(streamInfo)
            ?: throw StreamResolutionException("No playable streams found for this video.")

        ResolvedPlaybackInfo(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            stream = playableStream,
            originalUrl = fullUrl,
            viewCount = viewCount,
            likeCount = likeCount,
            subscriberCount = subscriberCount,
            channelAvatarUrl = channelAvatarUrl,
            availableQualities = availableQualities,
        )
    }

    /**
     * Re-resolves a previously resolved stream to obtain fresh playback URLs if expired.
     */
    suspend fun reResolve(previous: ResolvedPlaybackInfo): ResolvedPlaybackInfo {
        return resolveStream(previous.originalUrl)
    }

    /**
     * Obtains a PlayableStream for a specific target quality using cached StreamInfo.
     */
    fun resolveQuality(originalUrl: String, quality: String): PlayableStream? {
        val streamInfo = streamInfoCache[originalUrl] ?: return null
        return choosePlayableStream(streamInfo, quality)
    }

    /**
     * Selects the best stream:
     * 1. Best video-only stream <= 720p (or targetQuality) + best audio-only stream.
     * 2. Fallback: best progressive stream <= 720p (or best progressive available).
     */
    fun choosePlayableStream(streamInfo: StreamInfo, targetQuality: String? = null): PlayableStream? {
        val videoOnlyStreams = streamInfo.videoOnlyStreams.orEmpty()
            .filter { it.isUrl && !it.content.isNullOrBlank() }
        val audioStreams = streamInfo.audioStreams.orEmpty()
            .filter { it.isUrl && !it.content.isNullOrBlank() }

        val targetHeight = targetQuality?.removeSuffix("p")?.toIntOrNull()

        // 1. Try best video-only stream
        val candidateVideos = if (targetHeight != null) {
            videoOnlyStreams.filter { it.height == targetHeight }
                .ifEmpty {
                    videoOnlyStreams.filter { it.height <= targetHeight }.sortedByDescending { it.height }
                }
        } else {
            videoOnlyStreams
                .filter { it.height in 1..720 }
                .sortedByDescending { it.height }
                .ifEmpty {
                    videoOnlyStreams.sortedBy { it.height }
                }
        }

        val bestVideo = candidateVideos.firstOrNull() ?: videoOnlyStreams.maxByOrNull { it.height }
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

        if (bestVideo != null && bestAudio != null) {
            val videoUrl = bestVideo.content
            val audioUrl = bestAudio.content
            if (!videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                val quality = if (bestVideo.height > 0) "${bestVideo.height}p" else "720p"
                return PlayableStream.Adaptive(
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    videoQuality = quality,
                    audioBitrate = bestAudio.averageBitrate,
                )
            }
        }

        // 2. Fallback to progressive streams (video + audio combined)
        val progressiveStreams = streamInfo.videoStreams.orEmpty()
            .filter { it.isUrl && !it.content.isNullOrBlank() }

        val bestProgressive = if (targetHeight != null) {
            progressiveStreams.firstOrNull { it.height == targetHeight }
                ?: progressiveStreams.maxByOrNull { it.height }
        } else {
            progressiveStreams
                .filter { it.height in 1..720 }
                .maxByOrNull { it.height }
                ?: progressiveStreams.firstOrNull()
        }

        if (bestProgressive != null) {
            val streamUrl = bestProgressive.content
            if (!streamUrl.isNullOrBlank()) {
                val quality = if (bestProgressive.height > 0) "${bestProgressive.height}p" else "360p"
                return PlayableStream.Progressive(
                    streamUrl = streamUrl,
                    quality = quality,
                )
            }
        }

        return null
    }

    private fun normalizeToWatchUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://www.youtube.com/watch?v=$trimmed"
        }
    }

    private fun extractVideoId(url: String): String {
        return url.substringAfterLast("v=").substringBefore("&")
            .ifBlank { url.substringAfterLast("/") }
    }
}
