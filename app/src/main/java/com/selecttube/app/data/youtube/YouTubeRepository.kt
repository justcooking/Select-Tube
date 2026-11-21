package com.selecttube.app.data.youtube

import com.selecttube.app.domain.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedChannel(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    val description: String? = null,
    val subscriberCount: Long = -1,
    val url: String,
    val bannerUrl: String? = null,
)

data class ResolvedPlaylist(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val uploaderUrl: String? = null,
    val videoCount: Long = 0,
    val description: String? = null,
    val url: String,
)

data class ChannelTabResult(
    val items: List<VideoFeedItem>,
    val playlists: List<ResolvedPlaylist> = emptyList(),
    val hasNextPage: Boolean,
    val nextPage: Page?,
    val tabExtractor: ChannelTabExtractor?,
)

data class ChannelTabPageResult(
    val items: List<VideoFeedItem>,
    val playlists: List<ResolvedPlaylist> = emptyList(),
    val nextPage: Page?,
)

class ChannelNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PlaylistNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

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

            // Extract banner URL from extractor if present
            val bannerUrl = try {
                extractor.banners?.lastOrNull()?.url ?: extractor.banners?.firstOrNull()?.url
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
                bannerUrl = bannerUrl,
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
            val rawId = try {
                youtubeService.channelLHFactory.fromUrl(channelUrl).id
            } catch (e: Throwable) {
                channelUrl.substringAfterLast("/").substringBefore("?")
            }
            val id = rawId.removePrefix("channel/").removePrefix("c/").removePrefix("user/")
            if (id.startsWith("UC") && id.length in 22..26) {
                canonicalIdCache[id] = id
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

    private val canonicalIdCache = ConcurrentHashMap<String, String>()

    private data class ChannelTargetProfile(
        val channel: Channel,
        val canonicalId: String,
        val handle: String?,
        val normalizedName: String,
    )

    private data class ParsedUploader(
        val canonicalId: String?,
        val handle: String?,
        val rawUploaderName: String,
        val normalizedUploaderName: String,
        val uploaderUrl: String,
    )

    private suspend fun resolveCanonicalId(rawIdentifier: String): String {
        val trimmed = rawIdentifier.trim()
        if (trimmed.startsWith("UC") && trimmed.length in 22..26) {
            return trimmed
        }
        val cached = canonicalIdCache[trimmed]
        if (cached != null) return cached

        return try {
            val url = normalizeToYouTubeChannelUrl(trimmed)
            val extractor = youtubeService.getChannelExtractor(url)
            extractor.fetchPage()
            val resolvedId = extractor.id.ifBlank {
                try {
                    youtubeService.channelLHFactory.fromUrl(url).id
                        .removePrefix("channel/").removePrefix("c/").removePrefix("user/")
                } catch (_: Throwable) {
                    trimmed
                }
            }
            if (resolvedId.isNotBlank() && resolvedId.startsWith("UC")) {
                canonicalIdCache[trimmed] = resolvedId
            }
            resolvedId
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun parseUploader(item: StreamInfoItem): ParsedUploader? {
        val uploaderName = item.uploaderName.orEmpty().trim()
        val uploaderUrl = item.uploaderUrl.orEmpty().trim()
        if (uploaderName.isBlank() && uploaderUrl.isBlank()) return null

        var canonicalId: String? = null
        var handle: String? = null

        if (uploaderUrl.isNotBlank()) {
            val cleanUrl = uploaderUrl.removeSuffix("/").substringBefore("?")
            if (cleanUrl.contains("/channel/")) {
                val segment = cleanUrl.substringAfterLast("/channel/").substringBefore("/")
                if (segment.startsWith("UC", ignoreCase = true) && segment.length in 22..26) {
                    canonicalId = segment
                }
            } else if (cleanUrl.contains("/@")) {
                val seg = cleanUrl.substringAfterLast("/@").substringBefore("/").trim()
                if (seg.isNotBlank()) {
                    handle = seg.lowercase()
                }
            }

            // Fallback via NewPipe's channelLHFactory
            if (canonicalId == null && handle == null) {
                try {
                    val rawId = youtubeService.channelLHFactory.fromUrl(cleanUrl).id
                    val cleanId = rawId.removePrefix("channel/").removePrefix("c/").removePrefix("user/")
                    if (cleanId.startsWith("UC", ignoreCase = true) && cleanId.length in 22..26) {
                        canonicalId = cleanId
                    } else if (cleanId.startsWith("@")) {
                        handle = cleanId.removePrefix("@").lowercase()
                    }
                } catch (_: Throwable) {
                    // Ignore parsing error
                }
            }
        }

        return ParsedUploader(
            canonicalId = canonicalId,
            handle = handle,
            rawUploaderName = uploaderName,
            normalizedUploaderName = uploaderName.lowercase(),
            uploaderUrl = uploaderUrl,
        )
    }

    private fun matchesChannelStrictly(
        uploader: ParsedUploader,
        profile: ChannelTargetProfile,
    ): Boolean {
        // 1. Exact Canonical ID Match
        if (!uploader.canonicalId.isNullOrBlank() && profile.canonicalId.startsWith("UC")) {
            if (uploader.canonicalId.equals(profile.canonicalId, ignoreCase = true)) {
                return true
            }
        }

        // 2. Exact Handle Match
        if (!uploader.handle.isNullOrBlank() && !profile.handle.isNullOrBlank()) {
            if (uploader.handle.equals(profile.handle, ignoreCase = true)) {
                return true
            }
        }

        // 3. Fail-Closed Guarantee:
        // Do NOT match based on display name alone. Display names on YouTube are non-unique
        // and allow malicious or uncurated channels to collide with allowed channel names.
        return false
    }

    /**
     * Searches for videos strictly scoped to the user's active allowed channels.
     * Guaranteed zero-leakage: Any video not belonging to an active allowed channel is discarded.
     */
    suspend fun searchCuratedVideos(
        query: String,
        allowedChannels: List<Channel>,
        targetChannelId: String? = null,
    ): List<VideoFeedItem> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() || allowedChannels.isEmpty()) return@withContext emptyList()

        val activeChannels = allowedChannels.filter { it.isActive }
        if (activeChannels.isEmpty()) return@withContext emptyList()

        val filteredTargets = if (!targetChannelId.isNullOrBlank()) {
            activeChannels.filter { it.channelId.equals(targetChannelId, ignoreCase = true) }
        } else {
            activeChannels
        }
        if (filteredTargets.isEmpty()) return@withContext emptyList()

        // 1. Pre-resolve canonical profiles for target channels in parallel
        val targetProfiles: List<ChannelTargetProfile> = coroutineScope {
            filteredTargets.map { channel ->
                async {
                    val canonicalId = resolveCanonicalId(channel.channelId)
                    val handle = when {
                        channel.channelId.startsWith("@") -> channel.channelId.removePrefix("@").lowercase().trim()
                        !channel.channelId.startsWith("UC") -> channel.channelId.removePrefix("@").lowercase().trim()
                        else -> null
                    }
                    ChannelTargetProfile(
                        channel = channel,
                        canonicalId = canonicalId,
                        handle = handle,
                        normalizedName = channel.name.trim().lowercase(),
                    )
                }
            }.awaitAll()
        }

        val results = mutableListOf<VideoFeedItem>()
        val seenVideoIds = mutableSetOf<String>()

        // 2. Parallel multi-angle targeted channel search across target profiles
        coroutineScope {
            val deferreds = targetProfiles.map { profile ->
                async {
                    val profileResults = mutableListOf<VideoFeedItem>()
                    val channelQueries = mutableListOf<String>()

                    // Angle A: Query with exact channel name
                    channelQueries.add("$trimmedQuery \"${profile.channel.name}\"")

                    // Angle B: Query with channel handle if known
                    if (!profile.handle.isNullOrBlank()) {
                        channelQueries.add("$trimmedQuery @${profile.handle}")
                    } else {
                        // Angle C: Query with channel name without quotes
                        channelQueries.add("$trimmedQuery ${profile.channel.name}")
                    }

                    for (tQuery in channelQueries.distinct()) {
                        try {
                            val searchExtractor = youtubeService.getSearchExtractor(
                                tQuery,
                                Collections.singletonList("videos"),
                                null,
                            )
                            searchExtractor.fetchPage()
                            val pageItems = searchExtractor.initialPage?.items?.filterIsInstance<StreamInfoItem>().orEmpty()

                            for (item in pageItems) {
                                val uploader = parseUploader(item) ?: continue
                                if (matchesChannelStrictly(uploader, profile)) {
                                    val video = extractVideoFeedItem(item, profile.channel.channelId)
                                    profileResults.add(video)
                                }
                            }
                        } catch (_: Throwable) {
                            // Non-fatal per-query error
                        }
                    }
                    profileResults
                }
            }

            deferreds.awaitAll().forEach { list ->
                list.forEach { video ->
                    if (seenVideoIds.add(video.videoId)) {
                        results.add(video)
                    }
                }
            }
        }

        // Rank results: exact query in title first, then upload recency
        val lowerQuery = trimmedQuery.lowercase()
        results.sortedWith(
            compareByDescending<VideoFeedItem> { it.title.lowercase().contains(lowerQuery) }
                .thenByDescending { it.uploadTimestamp }
        )
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
        val canonicalId = resolveCanonicalId(channelId)

        val tabExtractor = try {
            youtubeService.getChannelTabExtractorFromId(canonicalId, ChannelTabs.VIDEOS).apply {
                fetchPage()
            }
        } catch (e: Exception) {
            // Fallback: try fetching channel extractor directly and locating videos tab
            val url = normalizeToYouTubeChannelUrl(canonicalId)
            val channelExtractor = youtubeService.getChannelExtractor(url)
            channelExtractor.fetchPage()
            val tabs = channelExtractor.tabs
            val videosTab = tabs?.firstOrNull { tabHandler ->
                tabHandler.contentFilters?.contains(ChannelTabs.VIDEOS) == true ||
                    tabHandler.contentFilters?.any { it.contains("videos", ignoreCase = true) } == true ||
                    tabHandler.id.contains("videos", ignoreCase = true)
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
                    false
                } || item.url.contains("/shorts/", ignoreCase = true)

                // Filter out live streams
                val isLive = try {
                    val streamType = item.streamType
                    streamType == StreamType.LIVE_STREAM ||
                        streamType == StreamType.AUDIO_LIVE_STREAM ||
                        streamType.name.contains("LIVE", ignoreCase = true) ||
                        streamType.name.contains("SHORT", ignoreCase = true)
                } catch (e: Throwable) {
                    false
                }

                !isShort && !isLive
            }
            .take(maxVideos)
            .map { item -> extractVideoFeedItem(item, canonicalId) }
    }

    /**
     * Fetches the first page of a specific channel tab (Videos, Shorts, Live, etc.)
     * and returns the items alongside the tab extractor for continuous pagination.
     */
    suspend fun fetchChannelTab(
        channelId: String,
        tabKey: String,
    ): ChannelTabResult = withContext(Dispatchers.IO) {
        val canonicalId = resolveCanonicalId(channelId)

        try {
            val tabExtractor = youtubeService.getChannelTabExtractorFromId(canonicalId, tabKey).apply {
                fetchPage()
            }
            val page = tabExtractor.initialPage
            val rawItems = page?.items.orEmpty()
            val items = rawItems.filterIsInstance<StreamInfoItem>().map {
                extractVideoFeedItem(it, canonicalId)
            }
            val playlists = rawItems.filterIsInstance<PlaylistInfoItem>().map {
                extractPlaylistItem(it)
            }
            val hasNext = page?.hasNextPage() == true
            val nextPage = if (hasNext) page?.nextPage else null

            ChannelTabResult(
                items = items,
                playlists = playlists,
                hasNextPage = hasNext,
                nextPage = nextPage,
                tabExtractor = tabExtractor,
            )
        } catch (e: Exception) {
            ChannelTabResult(
                items = emptyList(),
                playlists = emptyList(),
                hasNextPage = false,
                nextPage = null,
                tabExtractor = null,
            )
        }
    }

    /**
     * Fetches the next page of videos or playlists for continuous infinite scrolling.
     */
    suspend fun fetchChannelTabPage(
        tabExtractor: ChannelTabExtractor,
        page: Page,
        canonicalId: String,
    ): ChannelTabPageResult = withContext(Dispatchers.IO) {
        try {
            val newPage = tabExtractor.getPage(page)
            val rawItems = newPage?.items.orEmpty()
            val items = rawItems.filterIsInstance<StreamInfoItem>().map {
                extractVideoFeedItem(it, canonicalId)
            }
            val playlists = rawItems.filterIsInstance<PlaylistInfoItem>().map {
                extractPlaylistItem(it)
            }
            val hasNext = newPage?.hasNextPage() == true
            val nextPage = if (hasNext) newPage?.nextPage else null
            ChannelTabPageResult(
                items = items,
                playlists = playlists,
                nextPage = nextPage,
            )
        } catch (e: Exception) {
            ChannelTabPageResult(emptyList(), emptyList(), null)
        }
    }

    private fun extractPlaylistItem(item: PlaylistInfoItem): ResolvedPlaylist {
        val url = item.url.orEmpty()
        val id = url.substringAfter("list=").substringBefore("&").ifBlank { url }
        val thumb = item.thumbnails?.lastOrNull()?.url ?: item.thumbnails?.firstOrNull()?.url
        return ResolvedPlaylist(
            playlistId = id,
            name = item.name.orEmpty(),
            thumbnailUrl = thumb,
            uploaderName = item.uploaderName,
            uploaderUrl = item.uploaderUrl,
            videoCount = item.streamCount,
            description = item.description?.content,
            url = url,
        )
    }

    private fun extractVideoFeedItem(item: StreamInfoItem, fallbackChannelId: String): VideoFeedItem {
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

        return VideoFeedItem(
            videoId = videoId,
            title = title,
            channelName = uploader,
            channelId = fallbackChannelId,
            thumbnailUrl = thumbnailUrl,
            uploadDateText = uploadDateText,
            uploadTimestamp = uploadTimestamp,
            durationSeconds = durationSeconds,
            url = videoUrl,
        )
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

    fun normalizeToYouTubePlaylistUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            if (trimmed.contains("list=")) {
                val listId = trimmed.substringAfter("list=").substringBefore("&")
                return "https://www.youtube.com/playlist?list=$listId"
            }
            return trimmed
        }
        val clean = trimmed.removePrefix("list=")
        return "https://www.youtube.com/playlist?list=$clean"
    }

    suspend fun resolvePlaylist(input: String): ResolvedPlaylist = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            throw PlaylistNotFoundException("Please provide a valid playlist ID or URL.")
        }

        val url = normalizeToYouTubePlaylistUrl(trimmed)
        try {
            val extractor = youtubeService.getPlaylistExtractor(url)
            extractor.fetchPage()

            val id = runCatching { extractor.id }.getOrNull()?.ifBlank { null } ?: try {
                youtubeService.playlistLHFactory.fromUrl(url).id
            } catch (_: Exception) {
                url.substringAfter("list=").substringBefore("&")
            }

            val thumbUrl = runCatching {
                extractor.thumbnails?.maxByOrNull { it.height * it.width }?.url
            }.getOrNull()

            val name = runCatching { extractor.name }.getOrNull()?.ifBlank { null } ?: "Untitled Playlist"
            val uploaderName = runCatching { extractor.uploaderName }.getOrNull()?.ifBlank { null }
            val uploaderUrl = runCatching { extractor.uploaderUrl }.getOrNull()?.ifBlank { null }
            val streamCount = runCatching { extractor.streamCount }.getOrDefault(0L)
            val description = runCatching { extractor.description?.content }.getOrNull()

            var finalThumbUrl = thumbUrl
            var finalCount = streamCount
            if (finalThumbUrl == null || finalCount <= 0L) {
                val fallbackVideos = extractPlaylistVideosFallback(
                    extractor = extractor,
                    defaultUploader = uploaderName.orEmpty(),
                    defaultChannelId = uploaderUrl.orEmpty(),
                )
                if (finalThumbUrl == null) {
                    finalThumbUrl = fallbackVideos.firstOrNull()?.thumbnailUrl
                }
                if (finalCount <= 0L && fallbackVideos.isNotEmpty()) {
                    finalCount = fallbackVideos.size.toLong()
                }
            }

            ResolvedPlaylist(
                playlistId = id,
                name = name,
                thumbnailUrl = finalThumbUrl,
                uploaderName = uploaderName,
                uploaderUrl = uploaderUrl,
                videoCount = finalCount,
                description = description,
                url = runCatching { extractor.url }.getOrNull()?.ifBlank { null } ?: url,
            )
        } catch (e: Exception) {
            throw PlaylistNotFoundException("Could not resolve playlist from '$input'.", e)
        }
    }

    suspend fun searchPlaylists(query: String): List<ResolvedPlaylist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val searchExtractor = youtubeService.getSearchExtractor(
                trimmed,
                Collections.singletonList("playlists"),
                null,
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage ?: return@withContext emptyList()
            val items = page.items?.filterIsInstance<PlaylistInfoItem>().orEmpty()

            items.map { item ->
                val thumb = item.thumbnails?.maxByOrNull { it.height * it.width }?.url
                val id = try {
                    item.url.substringAfter("list=").substringBefore("&")
                } catch (_: Exception) {
                    ""
                }
                ResolvedPlaylist(
                    playlistId = id,
                    name = item.name.orEmpty(),
                    thumbnailUrl = thumb,
                    uploaderName = item.uploaderName,
                    uploaderUrl = item.uploaderUrl,
                    videoCount = item.streamCount,
                    description = item.description?.content,
                    url = item.url,
                )
            }.filter { it.playlistId.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun fetchPlaylistVideos(playlistIdOrUrl: String): Pair<ResolvedPlaylist, List<VideoFeedItem>> = withContext(Dispatchers.IO) {
        val url = normalizeToYouTubePlaylistUrl(playlistIdOrUrl)
        val extractor = youtubeService.getPlaylistExtractor(url)
        extractor.fetchPage()

        val id = runCatching { extractor.id }.getOrNull()?.ifBlank { null }
            ?: url.substringAfter("list=").substringBefore("&")

        val thumbUrl = runCatching {
            extractor.thumbnails?.maxByOrNull { it.height * it.width }?.url
        }.getOrNull()

        val name = runCatching { extractor.name }.getOrNull()?.ifBlank { null } ?: "Untitled Playlist"
        val uploaderName = runCatching { extractor.uploaderName }.getOrNull()?.ifBlank { null }
        val uploaderUrl = runCatching { extractor.uploaderUrl }.getOrNull()?.ifBlank { null }
        val streamCount = runCatching { extractor.streamCount }.getOrDefault(0L)
        val description = runCatching { extractor.description?.content }.getOrNull()

        var videos = emptyList<VideoFeedItem>()

        // 1. Try standard NewPipe extractor initialPage
        try {
            val page = extractor.initialPage
            val items = page?.items?.filterIsInstance<StreamInfoItem>().orEmpty()
            videos = items.map { item ->
                extractVideoFeedItem(item, uploaderUrl.orEmpty())
            }
        } catch (_: Throwable) {
            // NewPipe Extractor v0.26.5 throws NPE when getPlaylistHeader() is null
        }

        // 2. If standard extractor failed or returned no videos, use robust fallback parser
        if (videos.isEmpty()) {
            videos = extractPlaylistVideosFallback(
                extractor = extractor,
                defaultUploader = uploaderName.orEmpty(),
                defaultChannelId = uploaderUrl.orEmpty(),
            )
        }

        val finalThumbUrl = thumbUrl ?: videos.firstOrNull()?.thumbnailUrl
        val finalCount = if (streamCount > 0L) streamCount else videos.size.toLong()

        val resolved = ResolvedPlaylist(
            playlistId = id,
            name = name,
            thumbnailUrl = finalThumbUrl,
            uploaderName = uploaderName,
            uploaderUrl = uploaderUrl,
            videoCount = finalCount,
            description = description,
            url = runCatching { extractor.url }.getOrNull()?.ifBlank { null } ?: url,
        )

        Pair(resolved, videos)
    }

    /**
     * Fallback extractor for YouTube playlists when NewPipe Extractor v0.26.5's getInitialPage()
     * crashes due to missing playlistHeaderRenderer or fails to parse modern lockupViewModel items.
     * Extracts items from initialBrowseContinuationResponse directly.
     */
    private fun extractPlaylistVideosFallback(
        extractor: Any,
        defaultUploader: String,
        defaultChannelId: String,
    ): List<VideoFeedItem> {
        val continuation = getContinuationMap(extractor) ?: return emptyList()
        val results = mutableListOf<VideoFeedItem>()
        val seenVideoIds = mutableSetOf<String>()

        fun processItem(item: Map<*, *>) {
            var videoId: String? = null
            var title: String? = null
            var uploader: String? = null
            var thumbUrl: String? = null
            var durationSec: Long = -1L

            val lockup = item["lockupViewModel"] as? Map<*, *>
            val pvr = item["playlistVideoRenderer"] as? Map<*, *>

            if (lockup != null) {
                videoId = lockup["contentId"] as? String

                val meta = lockup["metadata"] as? Map<*, *>
                val lockupMeta = meta?.get("lockupMetadataViewModel") as? Map<*, *>
                if (lockupMeta != null) {
                    val titleObj = lockupMeta["title"] as? Map<*, *>
                    title = titleObj?.get("content") as? String

                    val contentMeta = lockupMeta["metadata"] as? Map<*, *>
                    val rowMeta = contentMeta?.get("contentMetadataViewModel") as? Map<*, *>
                    val rows = rowMeta?.get("metadataRows") as? List<*>
                    if (!rows.isNullOrEmpty()) {
                        val firstRow = rows[0] as? Map<*, *>
                        val parts = (firstRow?.get("metadataParts") ?: firstRow?.get("parts")) as? List<*>
                        if (!parts.isNullOrEmpty()) {
                            val firstPart = parts[0] as? Map<*, *>
                            val textObj = firstPart?.get("text") as? Map<*, *>
                            uploader = textObj?.get("content") as? String
                        }
                    }
                }

                val contentImage = lockup["contentImage"] as? Map<*, *>
                val thumbVm = contentImage?.get("thumbnailViewModel") as? Map<*, *>
                if (thumbVm != null) {
                    val image = thumbVm["image"] as? Map<*, *>
                    val sources = image?.get("sources") as? List<*>
                    if (!sources.isNullOrEmpty()) {
                        val lastSource = sources.lastOrNull() as? Map<*, *>
                        thumbUrl = lastSource?.get("url") as? String
                    }
                    val overlays = thumbVm["overlays"] as? List<*>
                    if (!overlays.isNullOrEmpty()) {
                        val firstOverlay = overlays[0] as? Map<*, *>
                        val bottom = firstOverlay?.get("thumbnailBottomOverlayViewModel") as? Map<*, *>
                        val badges = bottom?.get("badges") as? List<*>
                        if (!badges.isNullOrEmpty()) {
                            val firstBadge = badges[0] as? Map<*, *>
                            val badgeVm = firstBadge?.get("thumbnailBadgeViewModel") as? Map<*, *>
                            val durationText = badgeVm?.get("text") as? String
                            if (!durationText.isNullOrBlank() && durationText.contains(":")) {
                                val segs = durationText.split(":")
                                try {
                                    durationSec = when (segs.size) {
                                        2 -> segs[0].toLong() * 60 + segs[1].toLong()
                                        3 -> segs[0].toLong() * 3600 + segs[1].toLong() * 60 + segs[2].toLong()
                                        else -> -1L
                                    }
                                } catch (_: Throwable) {
                                    durationSec = -1L
                                }
                            }
                        }
                    }
                }
            } else if (pvr != null) {
                videoId = pvr["videoId"] as? String

                val titleObj = pvr["title"] as? Map<*, *>
                val runs = titleObj?.get("runs") as? List<*>
                title = if (!runs.isNullOrEmpty()) {
                    (runs[0] as? Map<*, *>)?.get("text") as? String
                } else {
                    titleObj?.get("simpleText") as? String
                }

                val lenSec = pvr["lengthSeconds"]
                durationSec = when (lenSec) {
                    is Number -> lenSec.toLong()
                    is String -> lenSec.toLongOrNull() ?: -1L
                    else -> -1L
                }

                val thumbObj = pvr["thumbnail"] as? Map<*, *>
                val thumbs = thumbObj?.get("thumbnails") as? List<*>
                if (!thumbs.isNullOrEmpty()) {
                    val lastThumb = thumbs.lastOrNull() as? Map<*, *>
                    thumbUrl = lastThumb?.get("url") as? String
                }

                val byline = pvr["shortBylineText"] as? Map<*, *>
                val bylineRuns = byline?.get("runs") as? List<*>
                if (!bylineRuns.isNullOrEmpty()) {
                    uploader = (bylineRuns[0] as? Map<*, *>)?.get("text") as? String
                }
            }

            if (!videoId.isNullOrBlank() && seenVideoIds.add(videoId)) {
                results.add(
                    VideoFeedItem(
                        videoId = videoId,
                        title = title.orEmpty().ifBlank { "Untitled Video" },
                        channelName = uploader.orEmpty().ifBlank { defaultUploader },
                        channelId = defaultChannelId,
                        thumbnailUrl = thumbUrl,
                        uploadDateText = null,
                        uploadTimestamp = 0L,
                        durationSeconds = durationSec,
                        url = "https://www.youtube.com/watch?v=$videoId",
                    )
                )
            }
        }

        // 1. Search in onResponseReceivedActions
        val actions = continuation["onResponseReceivedActions"] as? List<*>
        if (actions != null) {
            for (action in actions) {
                val actMap = action as? Map<*, *> ?: continue
                val appendAction = (actMap["appendContinuationItemsAction"]
                    ?: actMap["reloadContinuationItemsCommand"]) as? Map<*, *>
                val continuationItems = appendAction?.get("continuationItems") as? List<*>
                if (continuationItems != null) {
                    for (item in continuationItems) {
                        val itemMap = item as? Map<*, *> ?: continue
                        processItem(itemMap)
                    }
                }
            }
        }

        // 2. If no items were found, perform recursive traversal on the whole continuation map
        if (results.isEmpty()) {
            fun traverse(value: Any?) {
                when (value) {
                    is Map<*, *> -> {
                        if (value.containsKey("lockupViewModel") || value.containsKey("playlistVideoRenderer")) {
                            processItem(value)
                        } else {
                            for (v in value.values) {
                                traverse(v)
                            }
                        }
                    }
                    is List<*> -> {
                        for (element in value) {
                            traverse(element)
                        }
                    }
                }
            }
            traverse(continuation)
        }

        return results
    }

    private fun getContinuationMap(extractor: Any): Map<*, *>? {
        var clazz: Class<*>? = extractor.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField("initialBrowseContinuationResponse")
                field.isAccessible = true
                val obj = field.get(extractor)
                if (obj is Map<*, *>) return obj
            } catch (_: NoSuchFieldException) {
                // Check superclass
            } catch (_: Throwable) {
                break
            }
            clazz = clazz.superclass
        }
        return null
    }
}
