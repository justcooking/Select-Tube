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
)

class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves YouTube video URLs into playable streams using NewPipe Extractor.
 * Completely free of Android UI and Media3 dependencies so it can be reused in web/desktop targets.
 */
@Singleton
class StreamResolver @Inject constructor() {

    private val youtubeService = ServiceList.YouTube

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

        val videoId = streamInfo.url?.let { extractVideoId(it) } ?: extractVideoId(fullUrl)
        val title = streamInfo.name.orEmpty().ifBlank { "Untitled Video" }
        val channelName = streamInfo.uploaderName.orEmpty()
        val channelId = streamInfo.uploaderUrl?.substringAfterLast("/").orEmpty()
        val thumbnailUrl = streamInfo.thumbnails?.lastOrNull()?.url ?: streamInfo.thumbnails?.firstOrNull()?.url
        val durationSeconds = streamInfo.duration

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
        )
    }

    /**
     * Re-resolves a previously resolved stream to obtain fresh playback URLs if expired.
     */
    suspend fun reResolve(previous: ResolvedPlaybackInfo): ResolvedPlaybackInfo {
        return resolveStream(previous.originalUrl)
    }

    /**
     * Selects the best stream:
     * 1. Best video-only stream <= 720p + best audio-only stream.
     * 2. Fallback: best progressive stream <= 720p (or best progressive available).
     */
    private fun choosePlayableStream(streamInfo: StreamInfo): PlayableStream? {
        val videoOnlyStreams = streamInfo.videoOnlyStreams.orEmpty()
        val audioStreams = streamInfo.audioStreams.orEmpty()

        // 1. Try best video-only up to 720p + best audio
        val candidateVideos = videoOnlyStreams
            .filter { it.height > 0 && it.height <= 720 }
            .sortedByDescending { it.height }
            .ifEmpty {
                videoOnlyStreams.sortedBy { it.height }
            }

        val bestVideo = candidateVideos.firstOrNull()
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }

        if (bestVideo != null && bestAudio != null) {
            val videoUrl = bestVideo.url.orEmpty()
            val audioUrl = bestAudio.url.orEmpty()
            if (videoUrl.isNotBlank() && audioUrl.isNotBlank()) {
                val quality = bestVideo.resolution ?: "${bestVideo.height}p"
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
        val bestProgressive = progressiveStreams
            .filter { it.height > 0 && it.height <= 720 }
            .maxByOrNull { it.height }
            ?: progressiveStreams.maxByOrNull { it.height }
            ?: progressiveStreams.firstOrNull()

        if (bestProgressive != null && !bestProgressive.url.isNullOrBlank()) {
            val quality = bestProgressive.resolution ?: "${bestProgressive.height}p"
            return PlayableStream.Progressive(
                streamUrl = bestProgressive.url,
                quality = quality,
            )
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
