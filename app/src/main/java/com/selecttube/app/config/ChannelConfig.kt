package com.selecttube.app.config

import java.util.concurrent.TimeUnit

object ChannelConfig {
    /**
     * Wait duration required before a newly added channel (after the first one)
     * transitions from Pending to Active.
     *
     * Change this to TimeUnit.MINUTES.toMillis(1) for testing.
     */
    val ACTIVATION_DELAY_MS: Long = TimeUnit.HOURS.toMillis(2)
}
