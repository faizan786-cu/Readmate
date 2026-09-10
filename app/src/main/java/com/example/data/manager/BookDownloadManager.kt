package com.example.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DownloadProgress(
    val bookId: Long,
    val driveFileId: String,
    val progress: Float, // 0.0f to 1.0f
    val statusText: String = "Downloading..."
)

/**
 * Singleton state holder for in-flight book downloads.
 * Connects WorkManager / background operations directly to Compose UI.
 */
object BookDownloadManager {
    // Map of local Book Room ID -> DownloadProgress
    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadProgress>> = _activeDownloads.asStateFlow()

    // Map of Google Drive File ID -> Float progress (for Explore grid lookup)
    private val _driveDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val driveDownloads: StateFlow<Map<String, Float>> = _driveDownloads.asStateFlow()

    // Transient UI toast / snackbar notification events
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    fun updateProgress(bookId: Long, driveFileId: String, progress: Float, statusText: String = "Downloading...") {
        _activeDownloads.update { current ->
            current + (bookId to DownloadProgress(bookId, driveFileId, progress.coerceIn(0f, 1f), statusText))
        }
        _driveDownloads.update { current ->
            current + (driveFileId to progress.coerceIn(0f, 1f))
        }
    }

    fun removeDownload(bookId: Long, driveFileId: String) {
        _activeDownloads.update { it - bookId }
        _driveDownloads.update { it - driveFileId }
    }

    fun postEvent(message: String) {
        _events.value = message
    }

    fun clearEvent() {
        _events.value = null
    }
}
