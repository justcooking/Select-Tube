package com.selecttube.app.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlsEnforcementTest {

    private fun isBedtimeActive(startHour: Int, endHour: Int, currentHour: Int): Boolean {
        return if (startHour > endHour) {
            // Spans overnight across midnight, e.g. 21 (9 PM) to 7 (7 AM)
            currentHour >= startHour || currentHour < endHour
        } else {
            // Intra-day schedule, e.g. 13 (1 PM) to 15 (3 PM)
            currentHour in startHour until endHour
        }
    }

    @Test
    fun `bedtime spanning midnight restricts late night and early morning hours`() {
        val startHour = 21 // 9 PM
        val endHour = 7    // 7 AM

        // 8 PM - Before bedtime
        assertFalse(isBedtimeActive(startHour, endHour, currentHour = 20))

        // 9 PM - Bedtime begins
        assertTrue(isBedtimeActive(startHour, endHour, currentHour = 21))

        // 11 PM - Bedtime
        assertTrue(isBedtimeActive(startHour, endHour, currentHour = 23))

        // 2 AM - Overnight bedtime
        assertTrue(isBedtimeActive(startHour, endHour, currentHour = 2))

        // 6:59 AM (hour 6) - Still bedtime
        assertTrue(isBedtimeActive(startHour, endHour, currentHour = 6))

        // 7 AM - Morning wakeup, bedtime ends
        assertFalse(isBedtimeActive(startHour, endHour, currentHour = 7))

        // 12 PM - Daytime
        assertFalse(isBedtimeActive(startHour, endHour, currentHour = 12))
    }

    @Test
    fun `daily screen time accumulator accumulates time and resets on new date`() {
        var recordedDate = "2026-11-21"
        var accumulatedSeconds = 0

        fun recordWatchSeconds(additionalSeconds: Int, todayDate: String): Int {
            if (recordedDate != todayDate) {
                accumulatedSeconds = 0
                recordedDate = todayDate
            }
            accumulatedSeconds += additionalSeconds
            return accumulatedSeconds
        }

        val limitMinutes = 60
        val limitSeconds = limitMinutes * 60

        // Watch 30 minutes (1800 seconds) on day 1
        val t1 = recordWatchSeconds(1800, "2026-11-21")
        assertEquals(1800, t1)
        assertFalse("30 mins is within 60 min limit", t1 >= limitSeconds)

        // Watch another 30 minutes (1800 seconds) on day 1
        val t2 = recordWatchSeconds(1800, "2026-11-21")
        assertEquals(3600, t2)
        assertTrue("60 mins reached, should restrict playback", t2 >= limitSeconds)

        // New day arrives: 2026-11-22
        val t3 = recordWatchSeconds(300, "2026-11-22")
        assertEquals(300, t3)
        assertFalse("New day resets accumulated seconds, playback allowed", t3 >= limitSeconds)
    }
}
