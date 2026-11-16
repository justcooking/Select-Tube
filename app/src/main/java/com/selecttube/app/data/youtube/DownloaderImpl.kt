package com.selecttube.app.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom OkHttp-based Downloader implementation for NewPipe Extractor.
 *
 * NewPipe Extractor is network-agnostic and relies on this class to execute
 * all HTTP GET/POST calls to YouTube endpoints.
 */
class DownloaderImpl private constructor(
    private val client: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = when {
            dataToSend != null -> dataToSend.toRequestBody(null)
            httpMethod.equals("POST", ignoreCase = true) -> ByteArray(0).toRequestBody(null)
            else -> null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)

        for ((key, values) in headers) {
            for (value in values) {
                requestBuilder.addHeader(key, value)
            }
        }

        // Add standard User-Agent header if not explicitly set
        if (!headers.containsKey("User-Agent")) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { okHttpResponse ->
                val responseBody = okHttpResponse.body?.string().orEmpty()
                return Response(
                    okHttpResponse.code,
                    okHttpResponse.message,
                    okHttpResponse.headers.toMultimap(),
                    responseBody,
                    okHttpResponse.request.url.toString(),
                )
            }
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }

    companion object {
        fun create(): DownloaderImpl {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return DownloaderImpl(client)
        }
    }
}
