package com.selecttube.app.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictChannelMatchingTest {

    data class ParsedUploader(
        val canonicalId: String?,
        val handle: String?,
        val normalizedUploaderName: String,
    )

    data class ChannelTargetProfile(
        val canonicalId: String,
        val handle: String?,
        val normalizedName: String,
    )

    /**
     * Replicates the fail-closed matching algorithm in YouTubeRepository.
     */
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

        // 3. Fail-Closed Guarantee: Never match purely on display name
        return false
    }

    @Test
    fun `exact canonical channel ID matches successfully`() {
        val uploader = ParsedUploader(
            canonicalId = "UCsXVk37bltHxD1rDPwtNM8Q",
            handle = "kurzgesagt",
            normalizedUploaderName = "kurzgesagt – in a nutshell",
        )
        val profile = ChannelTargetProfile(
            canonicalId = "UCsXVk37bltHxD1rDPwtNM8Q",
            handle = "kurzgesagt",
            normalizedName = "kurzgesagt – in a nutshell",
        )

        assertTrue(matchesChannelStrictly(uploader, profile))
    }

    @Test
    fun `exact handle matches successfully when canonical ID is missing`() {
        val uploader = ParsedUploader(
            canonicalId = null,
            handle = "veritasium",
            normalizedUploaderName = "veritasium",
        )
        val profile = ChannelTargetProfile(
            canonicalId = "UCHnyfMqiRRG1u-2MsSQLbXA",
            handle = "veritasium",
            normalizedName = "veritasium",
        )

        assertTrue(matchesChannelStrictly(uploader, profile))
    }

    @Test
    fun `spoofed display name fails closed when canonical ID differs`() {
        val rogueUploader = ParsedUploader(
            canonicalId = "UC_ROGUE_CHANNEL_ID_9999",
            handle = "imposter_channel",
            normalizedUploaderName = "veritasium", // Spoofed display name!
        )
        val profile = ChannelTargetProfile(
            canonicalId = "UCHnyfMqiRRG1u-2MsSQLbXA",
            handle = "veritasium",
            normalizedName = "veritasium",
        )

        assertFalse(
            "Rogue channel with spoofed display name MUST be rejected",
            matchesChannelStrictly(rogueUploader, profile)
        )
    }

    @Test
    fun `matching display name alone fails closed when uploader ID and handle are missing`() {
        val unverifiedUploader = ParsedUploader(
            canonicalId = null,
            handle = null,
            normalizedUploaderName = "veritasium",
        )
        val profile = ChannelTargetProfile(
            canonicalId = "UCHnyfMqiRRG1u-2MsSQLbXA",
            handle = "veritasium",
            normalizedName = "veritasium",
        )

        assertFalse(
            "Unverified uploader without ID or handle must fail closed despite matching name",
            matchesChannelStrictly(unverifiedUploader, profile)
        )
    }
}
