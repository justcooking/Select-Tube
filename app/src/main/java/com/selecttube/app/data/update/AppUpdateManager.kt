package com.selecttube.app.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateState
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val release: AppReleaseInfo,
    ) : UpdateState
    data class ReadyToInstall(
        val apkFile: File,
        val release: AppReleaseInfo,
    ) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/justcooking/Select-Tube/releases/latest"
    }

    /**
     * Returns the current installed app version name (e.g., "1.0").
     */
    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Checks GitHub Releases API for a newer version than currently installed.
     * Returns AppReleaseInfo if an update with an APK is available, or null if up to date.
     */
    suspend fun checkForUpdate(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "SelectTube-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // No releases published yet
                    return@withContext Result.success(null)
                }
                return@withContext Result.failure(
                    IOException("Server returned HTTP ${response.code} ${response.message}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.success(null)
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "")

            val currentVersion = getCurrentVersionName()
            if (!isVersionNewer(tagName, currentVersion)) {
                return@withContext Result.success(null)
            }

            // Find APK asset
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize: Long = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                // Update exists but no APK asset has been attached yet
                return@withContext Result.success(null)
            }

            val release = AppReleaseInfo(
                versionName = tagName.removePrefix("v").removePrefix("V"),
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes.ifBlank { "Performance improvements and bug fixes." },
                apkDownloadUrl = apkUrl,
                apkFileName = apkName ?: "SelectTube_v$tagName.apk",
                apkSizeBytes = apkSize,
                htmlUrl = htmlUrl,
            )

            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file and emits download progress states.
     * When complete, emits ReadyToInstall.
     */
    fun downloadApk(release: AppReleaseInfo): Flow<UpdateState> = flow {
        emit(UpdateState.Downloading(0, 0L, release.apkSizeBytes, release))

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        // Clean up previous downloaded apks
        updatesDir.listFiles()?.forEach { it.delete() }

        val targetFile = File(updatesDir, release.apkFileName)

        try {
            val request = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("User-Agent", "SelectTube-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw IOException("Empty response body from server")
            val totalBytes = if (responseBody.contentLength() > 0) responseBody.contentLength() else release.apkSizeBytes

            responseBody.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastEmitTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime > 150 || (totalBytes > 0 && totalRead == totalBytes)) {
                            lastEmitTime = now
                            val percent = if (totalBytes > 0) {
                                ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            emit(UpdateState.Downloading(percent, totalRead, totalBytes, release))
                        }
                    }
                    output.flush()
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IOException("Downloaded APK file is empty or missing")
            }

            emit(UpdateState.ReadyToInstall(targetFile, release))
        } catch (e: Exception) {
            targetFile.delete()
            emit(UpdateState.Error(e.message ?: "Failed to download update."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun installApk(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "Update file is missing or corrupted.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check Unknown Apps permission on Android 8.0+ (API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context,
                    "Please allow Select Tube to install updates",
                    Toast.LENGTH_LONG
                ).show()
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.1" > "1.0", "1.0.1" > "1.0").
     */
    fun isVersionNewer(latest: String, current: String): Boolean {
        fun parseParts(v: String): List<Int> {
            val clean = v.trim().lowercase().removePrefix("v")
            val base = clean.substringBefore("-").substringBefore("+")
            return base.split(".").mapNotNull { it.toIntOrNull() }
        }

        val latestParts = parseParts(latest)
        val currentParts = parseParts(current)
        val maxLen = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
