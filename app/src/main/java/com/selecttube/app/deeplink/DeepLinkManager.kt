package com.selecttube.app.deeplink

import android.content.Intent
import com.selecttube.app.util.YouTubeLink
import com.selecttube.app.util.YouTubeUrlParser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkManager @Inject constructor() {

    private val _events = MutableSharedFlow<YouTubeLink>(
        replay = 1,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<YouTubeLink> = _events.asSharedFlow()

    fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false

        val textToParse = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            }
            else -> null
        }

        val link = YouTubeUrlParser.parse(textToParse)
        if (link != null) {
            _events.tryEmit(link)
            return true
        }
        return false
    }

    fun clearEvent() {
        _events.resetReplayCache()
    }
}
