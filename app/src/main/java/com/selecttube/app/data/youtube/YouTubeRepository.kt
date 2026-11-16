package com.selecttube.app.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedChannel(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    val description: String? = null,
    val subscriberCount: Long = -1,
    val url: String,
)

class ChannelNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class YouTubeRepository @Inject constructor() {

    private val youtubeService = ServiceList.YouTube

    /**
     * Resolves a channel by ID, URL, or @handle using NewPipe Extractor.
     * Throws [ChannelNotFoundException] if the channel cannot be resolved.
     */
    suspend fun resolveChannel(input: String): ResolvedChannel = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            throw ChannelNotFoundException("Please provide a valid channel ID, URL, or handle.")
        }

        val url = normalizeToYouTubeChannelUrl(trimmed)

        try {
            val extractor = youtubeService.getChannelExtractor(url)
            extractor.fetchPage()

            val id = extractor.id.ifBlank {
                try {
                    youtubeService.channelLHFactory.fromUrl(url).id
                } catch (e: Exception) {
                    trimmed.substringAfterLast("/").substringBefore("?")
                }
            }

            val name = extractor.name
            if (name.isNullOrBlank()) {
                throw ChannelNotFoundException("Could not find channel details for '$trimmed'.")
            }

            // Extract avatar URL from extractor (NewPipe Extractor v0.26.5 uses avatars)
            val avatarUrl = try {
                extractor.avatars?.firstOrNull()?.url
            } catch (e: Throwable) {
                null
            }

            ResolvedChannel(
                channelId = id,
                name = name,
                thumbnailUrl = avatarUrl,
                description = try { extractor.description } catch (e: Throwable) { null },
                subscriberCount = try { extractor.subscriberCount } catch (e: Throwable) { -1L },
                url = try { extractor.url.ifBlank { url } } catch (e: Throwable) { url },
            )
        } catch (e: Exception) {
            // If direct channel URL failed and input was not an explicit URL or UC-ID, try searching
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("UC")) {
                val searchResults = try {
                    searchChannelsInternal(trimmed)
                } catch (searchEx: Exception) {
                    emptyList()
                }

                val match = searchResults.firstOrNull()
                if (match != null) {
                    return@withContext match
                }
            }

            throw ChannelNotFoundException(
                "Could not find YouTube channel: '$trimmed'. Please verify the channel handle, ID or URL.",
                e,
            )
        }
    }

    /**
     * Searches YouTube for channels matching the given query using NewPipe Extractor.
     * Restricted to channels only (no videos, no playlists).
     */
    suspend fun searchChannels(query: String): List<ResolvedChannel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        searchChannelsInternal(trimmed)
    }

    private fun searchChannelsInternal(query: String): List<ResolvedChannel> {
        val searchExtractor = youtubeService.getSearchExtractor(
            query,
            Collections.singletonList("channels"),
            null,
        )
        searchExtractor.fetchPage()

        val page = searchExtractor.initialPage ?: return emptyList()
        val items = page.items ?: return emptyList()

        return items.filterIsInstance<ChannelInfoItem>().map { item ->
            val channelUrl = item.url.orEmpty()
            val id = try {
                youtubeService.channelLHFactory.fromUrl(channelUrl).id
            } catch (e: Throwable) {
                channelUrl.substringAfterLast("/").substringBefore("?")
            }

            val avatarUrl = try {
                item.thumbnails?.firstOrNull()?.url
            } catch (e: Throwable) {
                null
            }

            ResolvedChannel(
                channelId = id,
                name = item.name.orEmpty(),
                thumbnailUrl = avatarUrl,
                description = try { item.description } catch (e: Throwable) { null },
                subscriberCount = try { item.subscriberCount } catch (e: Throwable) { -1L },
                url = channelUrl,
            )
        }
    }

    /**
     * Fetches up to [maxVideos] recent regular videos from a channel's Videos tab.
     * Excludes Shorts and live streams.
     * Throws on failure so callers can identify per-channel errors.
     */
    suspend fun fetchChannelVideos(
        channelId: String,
        maxVideos: Int = 15,
    ): List<VideoFeedItem> = withContext(Dispatchers.IO) {
        val canonicalId = if (channelId.startsWith("UC") && channelId.length in 22..26) {
            channelId
        } else {
            try {
                val url = normalizeToYouTubeChannelUrl(channelId)
                val extractor = youtubeService.getChannelExtractor(url)
                extractor.fetchPage()
                extractor.id.ifBlank { channelId }
            } catch (e: Exception) {
                channelId
            }
        }

        val tabExtractor = try {
            youtubeService.getChannelTabExtractorFromId(canonicalId, "videos").apply {
                fetchPage()
            }
        } catch (e: Exception) {
            // Fallback: try fetching channel extractor directly and locating videos tab
            val url = normalizeToYouTubeChannelUrl(canonicalId)
            val channelExtractor = youtubeService.getChannelExtractor(url)
            channelExtractor.fetchPage()
            val tabs = channelExtractor.tabs
            val videosTab = tabs?.firstOrNull { tabHandler ->
                val filter = tabHandler.contentFilter?.joinToString() ?: ""
                filter.contains("videos", ignoreCase = true) || tabHandler.id.contains("videos", ignoreCase = true)
            } ?: tabs?.firstOrNull() ?: throw e

            youtubeService.getChannelTabExtractor(videosTab).apply {
                fetchPage()
            }
        }

        val page = tabExtractor.initialPage ?: return@withContext emptyList()
        val items = page.items ?: return@withContext emptyList()

        items
            .filterIsInstance<StreamInfoItem>()
            .filter { item ->
                // Filter out Shorts
                val isShort = try {
                    item.isShortFormContent
                } catch (e: Throwable) {
                    try {
                        val method = item.javaClass.methods.firstOrNull {
                            it.name == "isShortFormContent" && it.parameterCount == 0
                        }
                        method?.invoke(item) as? Boolean ?: false
                    } catch (e2: Throwable) {
                        false
                    }
                } || item.url.contains("/shorts/", ignoreCase = true)

                // Filter out live streams
                val isLive = try {
                    val streamType = item.streamType
                    streamType == StreamType.LIVE_STREAM ||
                        streamType == StreamType.AUDIO_LIVE_STREAM ||
                        streamType == StreamType.POST_LIVE_STREAM ||
                        streamType.name.contains("LIVE", ignoreCase = true) ||
                        streamType.name.contains("SHORT", ignoreCase = true)
                } catch (e: Throwable) {
                    false
                }

                !isShort && !isLive
            }
            .take(maxVideos)
            .map { item ->
                val videoUrl = item.url.orEmpty()
                val videoId = try {
                    val idMethod = item.javaClass.methods.firstOrNull { it.name == "getId" && it.parameterCount == 0 }
                    (idMethod?.invoke(item) as? String)?.takeIf { it.isNotBlank() }
                } catch (e: Throwable) {
                    null
                } ?: videoUrl.substringAfterLast("v=").substringBefore("&").ifBlank { videoUrl }

                val thumbnailUrl = try {
                    item.thumbnails?.lastOrNull()?.url ?: item.thumbnails?.firstOrNull()?.url
                } catch (e: Throwable) {
                    null
                }

                val uploadDateText = try {
                    item.textualUploadDate
                } catch (e: Throwable) {
                    null
                }

                val uploadTimestamp = extractUploadTimestamp(item, uploadDateText)

                val durationSeconds = try {
                    item.duration
                } catch (e: Throwable) {
                    -1L
                }

                val title = try {
                    item.name.orEmpty()
                } catch (e: Throwable) {
                    ""
                }

                val uploader = try {
                    item.uploaderName.orEmpty()
                } catch (e: Throwable) {
                    ""
                }

                VideoFeedItem(
                    videoId = videoId,
                    title = title,
                    channelName = uploader,
                    channelId = canonicalId,
                    thumbnailUrl = thumbnailUrl,
                    uploadDateText = uploadDateText,
                    uploadTimestamp = uploadTimestamp,
                    durationSeconds = durationSeconds,
                    url = videoUrl,
                )
            }
    }

    private fun extractUploadTimestamp(item: StreamInfoItem, textualDate: String?): Long {
        try {
            val dw = item.uploadDate ?: return parseTextualUploadDate(textualDate)
            val method = dw.javaClass.methods.firstOrNull {
                it.name in listOf("offsetDateTime", "getOffsetDateTime", "date", "getDate", "instant", "getInstant") &&
                    it.parameterCount == 0
            }
            val fromMethod = when (val res = method?.invoke(dw)) {
                is java.time.OffsetDateTime -> res.toInstant().toEpochMilli()
                is java.time.Instant -> res.toEpochMilli()
                is java.time.LocalDateTime -> res.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                is java.util.Calendar -> res.timeInMillis
                is java.util.Date -> res.time
                else -> null
            }
            if (fromMethod != null && fromMethod > 0L) return fromMethod

            for (field in dw.javaClass.declaredFields) {
                field.isAccessible = true
                val fVal = field.get(dw)
                val fromField = when (fVal) {
                    is java.time.OffsetDateTime -> fVal.toInstant().toEpochMilli()
                    is java.time.Instant -> fVal.toEpochMilli()
                    is java.time.LocalDateTime -> fVal.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    is java.util.Calendar -> fVal.timeInMillis
                    is java.util.Date -> fVal.time
                    else -> null
                }
                if (fromField != null && fromField > 0L) return fromField
            }
        } catch (e: Throwable) {
            // Fallback to text parsing
        }

        return parseTextualUploadDate(textualDate)
    }

    private fun parseTextualUploadDate(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val clean = text.lowercase().trim()
        val now = System.currentTimeMillis()
        val number = Regex("""\d+""").find(clean)?.value?.toLongOrNull() ?: 1L
        return when {
            clean.contains("second") -> now - number * 1_000L
            clean.contains("minute") -> now - number * 60_000L
            clean.contains("hour") -> now - number * 3_600_000L
            clean.contains("day") -> now - number * 86_400_000L
            clean.contains("week") -> now - number * 7 * 86_400_000L
            clean.contains("month") -> now - number * 30 * 86_400_000L
            clean.contains("year") -> now - number * 365 * 86_400_000L
            else -> 0L
        }
    }

    private fun normalizeToYouTubeChannelUrl(input: String): String {
        var clean = input.trim()
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }

        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("@") -> "https://www.youtube.com/$clean"
            clean.startsWith("UC") && clean.length in 22..26 -> "https://www.youtube.com/channel/$clean"
            else -> "https://www.youtube.com/@$clean"
        }
    }
}
