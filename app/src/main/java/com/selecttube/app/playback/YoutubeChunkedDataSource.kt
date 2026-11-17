package com.selecttube.app.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * Custom DataSource for YouTube googlevideo.com streams.
 *
 * Reads streams in chunks of ~1 MB, adding `&range=start-end` as a URL query parameter
 * to bypass YouTube's server-side bandwidth throttling on raw HTTP stream requests.
 * Automatically moves to the next chunk when the current chunk finishes.
 * Non-googlevideo requests are delegated directly to the underlying HttpDataSource.
 */
class YoutubeChunkedDataSource(
    private val baseHttpDataSourceFactory: HttpDataSource.Factory,
    private val onRequestLogged: ((host: String, statusCode: Int, ttfbMs: Long) -> Unit)? = null,
) : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        const val CHUNK_SIZE = 1024L * 1024L // ~1 MB chunk size
    }

    private var currentDataSpec: DataSpec? = null
    private var isGoogleVideo: Boolean = false

    // State for non-googlevideo delegation
    private var delegateDataSource: DataSource? = null

    // State for googlevideo chunked playback
    private var currentChunkSource: HttpDataSource? = null
    private var currentPosition: Long = 0L
    private var totalBytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var bytesRemainingInCurrentChunk: Long = 0L
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        this.currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val host = uri.host.orEmpty()
        isGoogleVideo = host.contains("googlevideo.com")

        if (!isGoogleVideo) {
            val delegate = baseHttpDataSourceFactory.createDataSource()
            delegateDataSource = delegate
            val startTime = SystemClock.elapsedRealtime()
            try {
                val bytes = delegate.open(dataSpec)
                val ttfbMs = SystemClock.elapsedRealtime() - startTime
                val statusCode = (delegate as? HttpDataSource)?.responseCode ?: 200
                onRequestLogged?.invoke(host, statusCode, ttfbMs)
                opened = true
                transferStarted(dataSpec)
                return bytes
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                val ttfbMs = SystemClock.elapsedRealtime() - startTime
                onRequestLogged?.invoke(host, e.responseCode, ttfbMs)
                throw e
            } catch (e: Exception) {
                val ttfbMs = SystemClock.elapsedRealtime() - startTime
                onRequestLogged?.invoke(host, -1, ttfbMs)
                throw e
            }
        }

        currentPosition = dataSpec.position
        totalBytesRemaining = dataSpec.length

        // Open the first chunk
        openNextChunk(isFirstChunk = true)

        opened = true
        transferStarted(dataSpec)

        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    private fun openNextChunk(isFirstChunk: Boolean): Boolean {
        val originalDataSpec = currentDataSpec ?: return false
        val originalUri = originalDataSpec.uri

        if (totalBytesRemaining != C.LENGTH_UNSET.toLong() && totalBytesRemaining <= 0L) {
            return false
        }

        val chunkStart = currentPosition
        val chunkEnd = if (totalBytesRemaining != C.LENGTH_UNSET.toLong()) {
            val bytesToRequest = minOf(CHUNK_SIZE, totalBytesRemaining)
            if (bytesToRequest <= 0L) return false
            chunkStart + bytesToRequest - 1
        } else {
            chunkStart + CHUNK_SIZE - 1
        }

        val chunkUri = buildChunkUri(originalUri, chunkStart, chunkEnd)

        currentChunkSource?.close()
        val source = baseHttpDataSourceFactory.createDataSource()
        currentChunkSource = source

        val chunkDataSpec = originalDataSpec.buildUpon()
            .setUri(chunkUri)
            .setPosition(0)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        val startTime = SystemClock.elapsedRealtime()
        try {
            val openResult = source.open(chunkDataSpec)
            val ttfbMs = SystemClock.elapsedRealtime() - startTime
            val statusCode = source.responseCode
            val host = chunkUri.host.orEmpty()
            onRequestLogged?.invoke(host, statusCode, ttfbMs)

            bytesRemainingInCurrentChunk = if (openResult != C.LENGTH_UNSET.toLong() && openResult > 0) {
                openResult
            } else {
                chunkEnd - chunkStart + 1
            }
            return true
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            val ttfbMs = SystemClock.elapsedRealtime() - startTime
            val host = chunkUri.host.orEmpty()
            onRequestLogged?.invoke(host, e.responseCode, ttfbMs)

            // If we get 416 (Range Not Satisfiable) on a subsequent chunk, we reached EOF
            if (e.responseCode == 416 && !isFirstChunk) {
                bytesRemainingInCurrentChunk = 0
                return false
            }
            throw e
        } catch (e: Exception) {
            val ttfbMs = SystemClock.elapsedRealtime() - startTime
            val host = chunkUri.host.orEmpty()
            onRequestLogged?.invoke(host, -1, ttfbMs)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        if (!isGoogleVideo) {
            val bytesRead = delegateDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
            if (bytesRead > 0) {
                bytesTransferred(bytesRead)
            }
            return bytesRead
        }

        if (totalBytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        // Move to the next chunk if current chunk is exhausted
        if (bytesRemainingInCurrentChunk <= 0L) {
            val opened = openNextChunk(isFirstChunk = false)
            if (!opened || bytesRemainingInCurrentChunk <= 0L) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val source = currentChunkSource ?: return C.RESULT_END_OF_INPUT
        val maxToRead = if (totalBytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemainingInCurrentChunk, totalBytesRemaining).toInt()
        } else {
            minOf(length.toLong(), bytesRemainingInCurrentChunk).toInt()
        }

        val bytesRead = source.read(buffer, offset, maxToRead)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            bytesRemainingInCurrentChunk = 0L
            return C.RESULT_END_OF_INPUT
        }

        bytesRemainingInCurrentChunk -= bytesRead
        currentPosition += bytesRead
        if (totalBytesRemaining != C.LENGTH_UNSET.toLong()) {
            totalBytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return if (isGoogleVideo) {
            currentChunkSource?.uri ?: currentDataSpec?.uri
        } else {
            delegateDataSource?.uri ?: currentDataSpec?.uri
        }
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return if (isGoogleVideo) {
            currentChunkSource?.responseHeaders ?: emptyMap()
        } else {
            delegateDataSource?.responseHeaders ?: emptyMap()
        }
    }

    override fun close() {
        if (!isGoogleVideo) {
            delegateDataSource?.close()
            delegateDataSource = null
        } else {
            currentChunkSource?.close()
            currentChunkSource = null
        }
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun buildChunkUri(baseUri: Uri, start: Long, end: Long): Uri {
        var uriString = baseUri.toString()
            .replace(Regex("([?&])range=[^&]*(&|$)")) { match ->
                if (match.groupValues[1] == "?" && match.groupValues[2] == "&") "?"
                else if (match.groupValues[2] == "&") "&"
                else ""
            }
        if (uriString.endsWith("?") || uriString.endsWith("&")) {
            uriString = uriString.dropLast(1)
        }
        val separator = if (uriString.contains("?")) "&" else "?"
        return Uri.parse("$uriString${separator}range=$start-$end")
    }
}

/**
 * Factory for creating [YoutubeChunkedDataSource] instances.
 */
class YoutubeChunkedDataSourceFactory(
    private val baseHttpDataSourceFactory: HttpDataSource.Factory,
    private val onRequestLogged: ((host: String, statusCode: Int, ttfbMs: Long) -> Unit)? = null,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return YoutubeChunkedDataSource(baseHttpDataSourceFactory, onRequestLogged)
    }
}
