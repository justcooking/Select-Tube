package com.selecttube.app.util

import android.net.Uri

sealed class YouTubeLink {
    data class Video(val videoId: String, val playlistId: String? = null) : YouTubeLink()
    data class Playlist(val playlistId: String) : YouTubeLink()
    data class Channel(val channelQueryOrUrl: String) : YouTubeLink()
}

object YouTubeUrlParser {

    private val URL_REGEX = Regex("https?://[^\\s<>\"'\\]\\)]+")

    fun parse(rawText: String?): YouTubeLink? {
        if (rawText.isNullOrBlank()) return null

        val match = URL_REGEX.find(rawText) ?: return null
        val url = match.value

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return null
        }

        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')

        if (!host.contains("youtube.com") && !host.contains("youtu.be")) {
            return null
        }

        // 1. youtu.be short links (e.g., https://youtu.be/dQw4w9WgXcQ?si=...)
        if (host.contains("youtu.be")) {
            val videoId = path.trimStart('/')
            if (videoId.isNotBlank()) {
                val playlistId = uri.getQueryParameter("list")
                return YouTubeLink.Video(videoId = videoId, playlistId = playlistId)
            }
        }

        // 2. Standard youtube.com / m.youtube.com / music.youtube.com links
        if (host.contains("youtube.com")) {
            when {
                // Video: /watch?v=... (or /watch?list=... without v)
                path == "/watch" -> {
                    val videoId = uri.getQueryParameter("v")
                    val playlistId = uri.getQueryParameter("list")
                    if (!videoId.isNullOrBlank()) {
                        return YouTubeLink.Video(videoId = videoId, playlistId = playlistId)
                    } else if (!playlistId.isNullOrBlank()) {
                        return YouTubeLink.Playlist(playlistId = playlistId)
                    }
                }

                // Playlist: /playlist?list=... or root /?list=...
                path == "/playlist" || (path.isEmpty() && !uri.getQueryParameter("list").isNullOrBlank()) -> {
                    val playlistId = uri.getQueryParameter("list")
                    if (!playlistId.isNullOrBlank()) {
                        return YouTubeLink.Playlist(playlistId = playlistId)
                    }
                }

                // Shorts: /shorts/...
                path.startsWith("/shorts/") -> {
                    val videoId = path.removePrefix("/shorts/").substringBefore("/")
                    if (videoId.isNotBlank()) {
                        return YouTubeLink.Video(videoId = videoId)
                    }
                }

                // Embed or /v/: /embed/...
                path.startsWith("/embed/") || path.startsWith("/v/") -> {
                    val videoId = path.substringAfterLast("/").substringBefore("?")
                    if (videoId.isNotBlank()) {
                        return YouTubeLink.Video(videoId = videoId)
                    }
                }

                // Channels: /@handle, /channel/..., /c/..., /user/...
                path.startsWith("/@") ||
                path.startsWith("/channel/") ||
                path.startsWith("/c/") ||
                path.startsWith("/user/") -> {
                    return YouTubeLink.Channel(channelQueryOrUrl = url)
                }
            }
        }

        return null
    }
}
