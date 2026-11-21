package com.selecttube.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.selecttube.app.deeplink.DeepLinkManager
import com.selecttube.app.ui.navigation.AppNavigation
import com.selecttube.app.ui.theme.SelectTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkManager: DeepLinkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        enableEdgeToEdge()
        deepLinkManager.handleIntent(intent)
        setContent {
            SelectTubeTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkManager.handleIntent(intent)
    }

    /**
     * Unlocks maximum available display refresh rate (120Hz / 90Hz / 144Hz) on supported devices.
     * Prevents OEMs (Samsung, Xiaomi, OnePlus, Pixel, etc.) from capping the app at 60Hz.
     */
    private fun enableHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display
                val modes = display?.supportedModes.orEmpty()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    params.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val modes = window.windowManager.defaultDisplay?.supportedModes.orEmpty()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    params.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = params
                }
            }
        } catch (_: Throwable) {
            // Non-fatal if device doesn't support display mode customization
        }
    }
}
