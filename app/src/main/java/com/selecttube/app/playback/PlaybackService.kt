package com.selecttube.app.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.selecttube.app.MainActivity
import com.selecttube.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val LOG_TAG = "SelectTubeMedia"

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    // Set to true once we have called startForeground() this service lifecycle.
    private var isForegroundStarted = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = playerManager.player
            // When the user dismisses the notification while paused, Media3 stops the player
            // (STATE_IDLE). We stop the service so it doesn't linger as a background process.
            if (playbackState == Player.STATE_IDLE && !player.playWhenReady) {
                Log.d(LOG_TAG, "PlaybackService: player IDLE and not playing → stopSelf()")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Channel is already registered in SelectTubeApp.onCreate(); this is a safety net.
        ensureNotificationChannel()

        // The notification provider MUST use the same NOTIFICATION_ID and CHANNEL_ID as the
        // placeholder we post in promoteForeground(). If the IDs differ, Media3 posts a second,
        // separate notification instead of replacing ours, and the placeholder stays forever.
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)   // must match the ID used in startForeground()
            .setChannelId(CHANNEL_ID)              // must match the channel used in startForeground()
            .setChannelName(R.string.playback_notification_channel_name)
            .build()
        setMediaNotificationProvider(notificationProvider)

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val sharedPlayer = playerManager.player
        Log.d(LOG_TAG, "PlaybackService.onCreate: binding MediaSession to player ${sharedPlayer.hashCode()}")

        sharedPlayer.addListener(playerListener)

        val session = MediaSession.Builder(this, sharedPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        mediaSession = session
        addSession(session)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Let MediaSessionService handle its own bookkeeping first.
        super.onStartCommand(intent, flags, startId)
        Log.d(
            LOG_TAG,
            "PlaybackService.onStartCommand: playWhenReady=${playerManager.player.playWhenReady}, " +
                "playbackState=${playerManager.player.playbackState}, " +
                "isForegroundStarted=$isForegroundStarted",
        )

        // On Android 8+ a service started with startForegroundService must call startForeground()
        // within 5–10 seconds. MediaSessionService normally only calls it when a MediaController
        // connects, which this app never creates. We call it here directly with a placeholder
        // notification so the platform constraint is met immediately.
        if (!isForegroundStarted) {
            promoteForeground()
        }

        return START_NOT_STICKY
    }

    /**
     * MediaSessionService calls this whenever player state or metadata changes.
     * We always call super so Media3 can build and post the proper notification with
     * title, artist, artwork, and transport controls.
     * We never return early here — doing so blocks the update and leaves the placeholder visible.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Log.d(
            LOG_TAG,
            "PlaybackService.onUpdateNotification called with title: '${session.player.mediaMetadata.title}', " +
                "startInForegroundRequired=$startInForegroundRequired",
        )

        // Always let the default provider run. It will call startForeground() internally when
        // startInForegroundRequired is true, using NOTIFICATION_ID and CHANNEL_ID that we
        // configured above — the same IDs the placeholder uses, so it replaces rather than
        // adding a second notification.
        super.onUpdateNotification(session, startInForegroundRequired)

        // If we promoted with a placeholder but super did not yet set isForegroundStarted
        // (because we track it ourselves), mark it now so we don't post the placeholder again.
        isForegroundStarted = true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(LOG_TAG, "PlaybackService.onGetSession called: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playerManager.player
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            Log.d(LOG_TAG, "PlaybackService.onTaskRemoved: not playing → stopSelf()")
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "PlaybackService.onDestroy")
        playerManager.player.removeListener(playerListener)
        mediaSession?.run {
            removeSession(this)
            // Do NOT release the player — it is a @Singleton managed by PlayerManager.
            release()
            mediaSession = null
        }
        isForegroundStarted = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Posts a minimal notification and calls startForeground to satisfy Android's
     * ForegroundServiceDidNotStartInTimeException window. Media3 replaces this notification
     * via onUpdateNotification() as soon as the player has metadata, because both use
     * NOTIFICATION_ID / CHANNEL_ID.
     */
    private fun promoteForeground() {
        val notification = buildLoadingNotification()
        startForeground(NOTIFICATION_ID, notification)
        isForegroundStarted = true
        Log.d(LOG_TAG, "PlaybackService.promoteForeground: startForeground(id=$NOTIFICATION_ID) called")
    }

    private fun buildLoadingNotification(): Notification {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Loading…")
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_notification_channel_name),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Media controls for video and audio playback"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CHANNEL_ID = "select_tube_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
