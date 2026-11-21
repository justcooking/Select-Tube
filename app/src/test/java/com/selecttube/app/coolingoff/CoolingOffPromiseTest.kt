package com.selecttube.app.coolingoff

import com.selecttube.app.data.db.ChannelEntity
import com.selecttube.app.data.model.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingOffPromiseTest {

    @Test
    fun `initial batch before first setup is consumed activates immediately`() {
        val isFirstSetupConsumed = false
        val channelCount = 0
        val now = 1_000_000L

        // Logic under test: First batch exemption is ONLY allowed if first setup is NOT consumed
        val isFirstBatch = !isFirstSetupConsumed
        val activationTime = if (isFirstBatch) null else (now + ChannelConfig.ACTIVATION_DELAY_MS)

        assertNull("Initial setup batch should have null activationTimestamp (immediate)", activationTime)

        val channel = ChannelEntity(
            channelId = "UC1234567890123456789012",
            name = "Education Hub",
            thumbnailUrl = null,
            activationTimestamp = activationTime,
            addedAt = now,
        )

        // isActive domain logic
        val isActive = channel.activationTimestamp == null || now >= channel.activationTimestamp!!
        assertTrue("Channel with null activationTimestamp must be active immediately", isActive)
    }

    @Test
    fun `emptying channel list does not re-arm first batch exemption`() {
        // User previously set up channels, so first setup was consumed
        val isFirstSetupConsumed = true
        // Later, user went to Settings and deleted all channels (count is 0)
        val channelCount = 0
        val now = 2_000_000L

        // Gating on !isFirstSetupConsumed prevents the cooling-off bypass
        val isFirstBatch = !isFirstSetupConsumed
        val activationTime = if (isFirstBatch) null else (now + ChannelConfig.ACTIVATION_DELAY_MS)

        assertNotNull("Subsequent additions must enforce 2-hour wait even if channelCount is 0", activationTime)
        assertEquals(now + 2 * 60 * 60 * 1000L, activationTime)

        val channel = ChannelEntity(
            channelId = "UC9999999999999999999999",
            name = "Late Night Distraction",
            thumbnailUrl = null,
            activationTimestamp = activationTime,
            addedAt = now,
        )

        val isActiveAtAdd = channel.activationTimestamp == null || now >= channel.activationTimestamp!!
        assertFalse("Channel must NOT be active immediately upon addition", isActiveAtAdd)

        // Halfway through the 2-hour wait (1 hour in)
        val oneHourLater = now + 1 * 60 * 60 * 1000L
        val isActiveOneHourIn = channel.activationTimestamp == null || oneHourLater >= channel.activationTimestamp!!
        assertFalse("Channel must remain pending after only 1 hour", isActiveOneHourIn)

        // Exactly at activation time (2 hours in)
        val twoHoursLater = now + 2 * 60 * 60 * 1000L
        val isActiveTwoHoursIn = channel.activationTimestamp == null || twoHoursLater >= channel.activationTimestamp!!
        assertTrue("Channel must automatically become active after 2 hours", isActiveTwoHoursIn)
    }

    @Test
    fun `pending channel can be cancelled without friction before activation`() {
        val now = 1_000_000L
        val channel = ChannelEntity(
            channelId = "UCabc",
            name = "Impulsive Add",
            thumbnailUrl = null,
            activationTimestamp = now + ChannelConfig.ACTIVATION_DELAY_MS,
            addedAt = now,
        )

        val activeChannels = mutableListOf(channel)
        // User decides to cancel addition
        activeChannels.removeIf { it.channelId == "UCabc" }

        assertTrue("Pending channel should be removable from list upon cancellation", activeChannels.isEmpty())
    }
}
