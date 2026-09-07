package com.example.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = createDefaultClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var dismissedTag: String? = null
    private var hasPolledOnStartup = false

    companion object {
        private const val TAG = "ReadMateUpdateManager"
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/faizan786-cu/Readmate/releases/latest"

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    /**
     * Non-blocking startup check for latest GitHub release.
     * Silent and safe: failures are caught and logged without disrupting reading experience.
     */
    fun checkSilentlyOnLaunch() {
        if (hasPolledOnStartup) return
        hasPolledOnStartup = true
        checkForUpdates(force = false)
    }

    /**
     * Executes GitHub release query. If [force] is true, ignores dismissed tags.
     */
    fun checkForUpdates(force: Boolean = false) {
        val currentState = _updateState.value
        if (currentState is UpdateState.Checking ||
            (currentState is UpdateState.UpdateAvailable && currentState.isDownloading)
        ) {
            return
        }

        _updateState.value = UpdateState.Checking

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ReadMate-App")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    Log.w(TAG, "GitHub release check returned HTTP ${response.code}")
                    _updateState.value = if (force) {
                        UpdateState.Error("Could not check for updates (HTTP ${response.code})")
                    } else {
                        UpdateState.Idle
                    }
                    return@launch
                }

                val releaseJson = JSONObject(responseBody)
                val tagName = releaseJson.optString("tag_name", "").trim()
                val body = releaseJson.optString("body", "").trim()
                val assets = releaseJson.optJSONArray("assets")

                var apkDownloadUrl: String? = null
                var apkSize: Long = 0L
                var apkFileName: String = "readmate_update.apk"

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val assetObj = assets.optJSONObject(i) ?: continue
                        val assetName = assetObj.optString("name", "")
                        val downloadUrl = assetObj.optString("browser_download_url", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            apkDownloadUrl = downloadUrl
                            apkSize = assetObj.optLong("size", 0L)
                            apkFileName = assetName
                            break
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME

                if (tagName.isNotBlank() &&
                    !apkDownloadUrl.isNullOrBlank() &&
                    VersionComparator.isNewer(tagName, currentVersion)
                ) {
                    val updateInfo = UpdateInfo(
                        currentVersion = currentVersion,
                        newVersion = tagName,
                        downloadUrl = apkDownloadUrl,
                        releaseNotes = body,
                        apkSize = apkSize,
                        apkFileName = apkFileName
                    )

                    if (dismissedTag == tagName && !force) {
                        Log.d(TAG, "Update $tagName already dismissed by user this session")
                        _updateState.value = UpdateState.Idle
                    } else {
                        Log.i(TAG, "Newer update discovered: $tagName (Current: $currentVersion)")
                        _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                    }
                } else {
                    Log.d(TAG, "App is up to date (Current: $currentVersion, Remote: $tagName)")
                    _updateState.value = if (force) {
                        UpdateState.UpToDate(currentVersion)
                    } else {
                        UpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                _updateState.value = if (force) {
                    UpdateState.Error(e.message ?: "Failed to query update server")
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    /**
     * Streams the APK file from GitHub release assets to internal cache with live progress.
     */
    fun startDownload(updateInfo: UpdateInfo) {
        val currentState = _updateState.value
        if (currentState is UpdateState.UpdateAvailable && currentState.isDownloading) {
            return
        }

        _updateState.update {
            if (it is UpdateState.UpdateAvailable) {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    downloadedBytes = 0L,
                    totalBytes = updateInfo.apkSize,
                    errorMessage = null
                )
            } else {
                UpdateState.UpdateAvailable(
                    updateInfo = updateInfo,
                    isDownloading = true,
                    downloadProgress = 0,
                    downloadedBytes = 0L,
                    totalBytes = updateInfo.apkSize
                )
            }
        }

        scope.launch(Dispatchers.IO) {
            val apksDir = File(context.cacheDir, "apks")
            if (!apksDir.exists()) {
                apksDir.mkdirs()
            }
            val apkFile = File(apksDir, "readmate_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            try {
                val request = Request.Builder()
                    .url(updateInfo.downloadUrl)
                    .header("User-Agent", "ReadMate-App")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    throw IOException("Download failed with HTTP ${response.code}")
                }

                val contentLength = body.contentLength().let {
                    if (it > 0) it else updateInfo.apkSize
                }

                var bytesReadTotal = 0L
                val buffer = ByteArray(16384)
                var lastReportedPercent = -1

                apkFile.outputStream().use { fos ->
                    body.byteStream().use { bis ->
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            bytesReadTotal += read

                            val percent = if (contentLength > 0) {
                                ((bytesReadTotal * 100) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }

                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                _updateState.update { cur ->
                                    if (cur is UpdateState.UpdateAvailable) {
                                        cur.copy(
                                            isDownloading = true,
                                            downloadProgress = percent,
                                            downloadedBytes = bytesReadTotal,
                                            totalBytes = contentLength
                                        )
                                    } else cur
                                }
                            }
                        }
                        fos.flush()
                    }
                }

                if (!apkFile.exists() || apkFile.length() == 0L) {
                    throw IOException("Downloaded APK file is empty or missing")
                }

                Log.i(TAG, "APK download complete: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

                _updateState.update { cur ->
                    if (cur is UpdateState.UpdateAvailable) {
                        cur.copy(
                            isDownloading = false,
                            downloadProgress = 100,
                            downloadedBytes = bytesReadTotal,
                            totalBytes = bytesReadTotal,
                            downloadedFile = apkFile
                        )
                    } else cur
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading update APK", e)
                _updateState.update { cur ->
                    if (cur is UpdateState.UpdateAvailable) {
                        cur.copy(
                            isDownloading = false,
                            errorMessage = e.message ?: "Failed downloading update"
                        )
                    } else cur
                }
            }
        }
    }

    /**
     * Launches Android's native package installer via FileProvider.
     */
    fun installUpdate(apkFile: File? = null) {
        val targetFile = apkFile ?: run {
            val fallback = File(context.cacheDir, "apks/readmate_update.apk")
            if (fallback.exists()) fallback else null
        }

        if (targetFile == null || !targetFile.exists()) {
            Log.e(TAG, "Install requested but APK file does not exist")
            return
        }

        try {
            // Check unknown sources permission on Android 8.0+ if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                }
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _updateState.update { cur ->
                if (cur is UpdateState.UpdateAvailable) {
                    cur.copy(errorMessage = "Could not launch installer: ${e.message}")
                } else cur
            }
        }
    }

    /**
     * Dismisses the update dialog for this session.
     */
    fun dismissDialog() {
        val current = _updateState.value
        if (current is UpdateState.UpdateAvailable) {
            dismissedTag = current.updateInfo.newVersion
        }
        _updateState.value = UpdateState.Idle
    }

    /**
     * Resets state back to Idle.
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
