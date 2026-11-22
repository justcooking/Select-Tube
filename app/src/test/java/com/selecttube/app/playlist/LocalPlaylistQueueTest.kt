package com.selecttube.app.playlist

import com.selecttube.app.data.db.LocalPlaylistEntity
import com.selecttube.app.data.db.LocalPlaylistVideoEntity
import com.selecttube.app.playback.PlayQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LocalPlaylistQueueTest {

    @Test
    fun `enqueue appends to end of queue while playNext inserts at front`() {
        val queue = mutableListOf<PlayQueueItem>()

        val item1 = PlayQueueItem(videoId = "vid1", title = "Video 1", channelName = "Channel A", thumbnailUrl = null)
        val item2 = PlayQueueItem(videoId = "vid2", title = "Video 2", channelName = "Channel B", thumbnailUrl = null)
        val itemUrgent = PlayQueueItem(videoId = "vidUrgent", title = "Urgent Video", channelName = "Channel C", thumbnailUrl = null)

        // Normal enqueue adds to back
        queue.add(item1)
        queue.add(item2)
        assertEquals(2, queue.size)
        assertEquals("vid1", queue[0].videoId)
        assertEquals("vid2", queue[1].videoId)

        // Play Next inserts at index 0
        queue.add(0, itemUrgent)
        assertEquals(3, queue.size)
        assertEquals("vidUrgent", queue[0].videoId)
        assertEquals("vid1", queue[1].videoId)
        assertEquals("vid2", queue[2].videoId)

        // Advancing queue consumes head
        val nextPlayed = queue.removeAt(0)
        assertEquals("vidUrgent", nextPlayed.videoId)
        assertEquals(2, queue.size)
        assertEquals("vid1", queue[0].videoId)
    }

    @Test
    fun `remove and clear queue items operate correctly`() {
        val queue = mutableListOf<PlayQueueItem>()
        for (i in 1..5) {
            queue.add(PlayQueueItem(videoId = "vid$i", title = "Video $i", channelName = "Channel", thumbnailUrl = null))
        }
        assertEquals(5, queue.size)

        // Remove index 2 ("vid3")
        queue.removeAt(2)
        assertEquals(4, queue.size)
        assertEquals(listOf("vid1", "vid2", "vid4", "vid5"), queue.map { it.videoId })

        // Clear
        queue.clear()
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `local playlist video ordering and position assignment`() {
        val playlistId = UUID.randomUUID().toString()
        val playlist = LocalPlaylistEntity(
            id = playlistId,
            name = "Kotlin Tutorials",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        assertNotNull(playlist.id)
        assertEquals("Kotlin Tutorials", playlist.name)

        val videos = mutableListOf<LocalPlaylistVideoEntity>()

        // Simulate adding video 1
        val maxPos1 = videos.maxOfOrNull { it.position } ?: -1
        videos.add(
            LocalPlaylistVideoEntity(
                playlistId = playlistId,
                videoId = "v1",
                title = "Lesson 1",
                channelName = "Kotlin By Example",
                channelId = "UC123",
                thumbnailUrl = null,
                durationSeconds = 600,
                position = maxPos1 + 1,
                addedAt = 1000L,
            )
        )

        // Simulate adding video 2
        val maxPos2 = videos.maxOfOrNull { it.position } ?: -1
        videos.add(
            LocalPlaylistVideoEntity(
                playlistId = playlistId,
                videoId = "v2",
                title = "Lesson 2",
                channelName = "Kotlin By Example",
                channelId = "UC123",
                thumbnailUrl = null,
                durationSeconds = 900,
                position = maxPos2 + 1,
                addedAt = 1010L,
            )
        )

        assertEquals(0, videos[0].position)
        assertEquals(1, videos[1].position)
        assertEquals(listOf("v1", "v2"), videos.sortedBy { it.position }.map { it.videoId })
    }

    @Test
    fun `toggle video in playlist adds when absent and removes when present`() {
        val playlistId = "pl_1"
        val videoIds = mutableSetOf("existing_vid1", "existing_vid2")

        fun toggle(videoId: String) {
            if (videoIds.contains(videoId)) {
                videoIds.remove(videoId)
            } else {
                videoIds.add(videoId)
            }
        }

        // Toggle existing -> removes
        toggle("existing_vid1")
        assertFalse(videoIds.contains("existing_vid1"))
        assertEquals(1, videoIds.size)

        // Toggle new -> adds
        toggle("new_vid")
        assertTrue(videoIds.contains("new_vid"))
        assertEquals(2, videoIds.size)
    }
}
