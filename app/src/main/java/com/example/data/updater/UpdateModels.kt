package com.example.data.updater

import java.io.File

/**
 * Metadata representing an available remote release on GitHub.
 */
data class UpdateInfo(
    val currentVersion: String,
    val newVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val apkSize: Long = 0L,
    val apkFileName: String = "readmate_update.apk"
)

/**
 * UI State for the auto-update workflow.
 */
sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(
        val updateInfo: UpdateInfo,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val downloadedFile: File? = null,
        val errorMessage: String? = null
    ) : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
