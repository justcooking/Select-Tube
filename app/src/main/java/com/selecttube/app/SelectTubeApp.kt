package com.selecttube.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.selecttube.app.data.youtube.DownloaderImpl
import com.selecttube.app.playback.PlaybackService
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

@HiltAndroidApp
class SelectTubeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipe Extractor with custom OkHttp downloader and default localization
        NewPipe.init(DownloaderImpl.create(), Localization.DEFAULT)
        // Register the playback notification channel at app start so it exists
        // before the service is created and before any notification is posted.
        createPlaybackNotificationChannel()
    }

    /**
     * Provides an optimized Coil ImageLoader with generous memory & disk caching
     * to eliminate GC pauses and ensure butter-smooth 120Hz list scrolling.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
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
