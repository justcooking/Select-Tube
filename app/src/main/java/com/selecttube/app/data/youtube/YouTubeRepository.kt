package com.selecttube.app.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
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

            // Extract avatar URL from extractor
            val avatarUrl = try {
                extractor.avatarUrl
            } catch (e: Exception) {
                null
            } ?: try {
                extractor.avatars?.firstOrNull()?.url
            } catch (e: Exception) {
                null
            }

            ResolvedChannel(
                channelId = id,
                name = name,
                thumbnailUrl = avatarUrl,
                description = try { extractor.description } catch (e: Exception) { null },
                subscriberCount = try { extractor.subscriberCount } catch (e: Exception) { -1L },
                url = extractor.url.ifBlank { url },
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
            } catch (e: Exception) {
                channelUrl.substringAfterLast("/").substringBefore("?")
            }

            val avatarUrl = try {
                item.thumbnails?.firstOrNull()?.url
            } catch (e: Exception) {
                null
            }

            ResolvedChannel(
                channelId = id,
                name = item.name.orEmpty(),
                thumbnailUrl = avatarUrl,
                description = try { item.description } catch (e: Exception) { null },
                subscriberCount = try { item.subscriberCount } catch (e: Exception) { -1L },
                url = channelUrl,
            )
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
