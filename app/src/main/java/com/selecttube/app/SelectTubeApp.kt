package com.selecttube.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.selecttube.app.data.youtube.DownloaderImpl
import com.selecttube.app.playback.PlaybackService
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

@HiltAndroidApp
class SelectTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipe Extractor with custom OkHttp downloader and default localization
        NewPipe.init(DownloaderImpl.create(), Localization.DEFAULT)
        // Register the playback notification channel at app start so it exists
        // before the service is created and before any notification is posted.
        createPlaybackNotificationChannel()
    }

    private fun createPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PlaybackService.CHANNEL_ID,
                getString(R.string.playback_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Media controls for video and audio playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
