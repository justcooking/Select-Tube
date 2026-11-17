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

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    // Track whether we have called startForeground at least once this service lifecycle.
    private var isForegroundStarted = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = playerManager.player
            // When user dismisses the notification while paused, Media3 calls stop() → STATE_IDLE.
            if (playbackState == Player.STATE_IDLE && !player.playWhenReady) {
                Log.d(TAG, "Player IDLE and not playing → stopping service")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Channel is already created in SelectTubeApp.onCreate(); calling it again is a no-op.
        // We call it here too as a safety net for direct-launch scenarios in tests.
        ensureNotificationChannel()

        // Tell Media3 which channel to use for the notification.
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
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
        Log.d(TAG, "onCreate: binding MediaSession to player ${sharedPlayer.hashCode()}")

        sharedPlayer.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, sharedPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Let MediaSessionService handle its own command processing first.
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: playWhenReady=${playerManager.player.playWhenReady}, " +
            "playbackState=${playerManager.player.playbackState}, " +
            "isForegroundStarted=$isForegroundStarted")

        // On Android 14+ a service started with startForegroundService must call
        // startForeground within 10 s or be killed with a ForegroundServiceDidNotStartInTimeException.
        // MediaSessionService normally calls startForeground only when a MediaController connects,
        // but this app intentionally does not create one. We therefore call it here directly.
        if (!isForegroundStarted) {
            promoteForeground()
        }

        return START_NOT_STICKY
    }

    /**
     * MediaSessionService calls this whenever the player state or metadata changes.
     * We intercept it to ensure startForeground is called (or re-called after a stopForeground),
     * and to inject playlistMetadata into the notification when currentMediaItem.mediaMetadata
     * is empty (which happens with MergingMediaSource).
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Log.d(TAG, "onUpdateNotification: startInForegroundRequired=$startInForegroundRequired, " +
            "mediaMetadata='${session.player.mediaMetadata.title}', " +
            "playlistMetadata='${session.player.playlistMetadata.title}'")

        // When the player uses MergingMediaSource, player.mediaMetadata (which Media3's
        // DefaultMediaNotificationProvider reads) is EMPTY because there is no top-level
        // MediaItem wrapping the merged source. player.playlistMetadata IS populated because
        // PlayerManager explicitly calls player.setPlaylistMetadata(). We propagate it onto
        // player.mediaMetadata by temporarily overriding it via the MediaSession so the
        // notification provider can read a meaningful title and artist.
        //
        // The correct way to do this in Media3 is to call session.player.replaceCurrentMediaItem()
        // but that is not valid for MediaSource-based playback. Instead we fall back to
        // building the notification ourselves when mediaMetadata is blank.
        val player = session.player
        val effectiveMetadata = if (player.mediaMetadata.title.isNullOrEmpty()) {
            player.playlistMetadata
        } else {
            player.mediaMetadata
        }

        if (effectiveMetadata.title.isNullOrEmpty()) {
            // No metadata at all yet (stream still resolving). Still promote to foreground
            // with a bare notification so we don't get killed.
            if (startInForegroundRequired || !isForegroundStarted) {
                promoteForeground()
            }
            return
        }

        // Let the default provider build the notification with controls; it reads mediaMetadata.
        // If mediaMetadata was empty we can't fix that without modifying the player state directly,
        // so we let it build a minimal notification and promote anyway.
        super.onUpdateNotification(session, startInForegroundRequired)

        // Guarantee foreground even if the super call didn't promote.
        if (!isForegroundStarted) {
            promoteForeground()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playerManager.player
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            Log.d(TAG, "onTaskRemoved: not playing, stopping service")
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        playerManager.player.removeListener(playerListener)
        mediaSession?.run {
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
     * Builds a minimal foreground notification and calls startForeground so the platform
     * is satisfied. Media3's DefaultMediaNotificationProvider will replace it immediately
     * with a proper media notification containing controls.
     */
    private fun promoteForeground() {
        val notification = buildLoadingNotification()
        startForeground(NOTIFICATION_ID, notification)
        isForegroundStarted = true
        Log.d(TAG, "promoteForeground: startForeground called")
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
