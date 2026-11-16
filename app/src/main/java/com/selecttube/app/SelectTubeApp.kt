package com.selecttube.app

import android.app.Application
import com.selecttube.app.data.youtube.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

@HiltAndroidApp
class SelectTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipe Extractor with custom OkHttp downloader and default localization
        NewPipe.init(DownloaderImpl.create(), Localization.DEFAULT)
    }
}
