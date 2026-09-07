package com.example.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

sealed class ExtractionStatus {
    data object Idle : ExtractionStatus()
    data class Processing(
        val bookId: Long,
        val bookTitle: String,
        val message: String = "Analyzing book structure with Flash-Lite..."
    ) : ExtractionStatus()
    data class Success(
        val bookId: Long,
        val bookTitle: String,
        val sectionCount: Int,
        val message: String = "Extracted $sectionCount sections successfully."
    ) : ExtractionStatus()
    data class Error(
        val bookId: Long,
        val bookTitle: String,
        val message: String
    ) : ExtractionStatus()
}

/**
 * Shared state manager for PDF structure scanning overlay and live notifications.
 */
object PdfExtractionStateManager {
    private val _statusMap = ConcurrentHashMap<Long, ExtractionStatus>()
    private val _currentStatus = MutableStateFlow<ExtractionStatus>(ExtractionStatus.Idle)
    val currentStatus: StateFlow<ExtractionStatus> = _currentStatus.asStateFlow()

    fun setProcessing(bookId: Long, bookTitle: String) {
        val status = ExtractionStatus.Processing(
            bookId = bookId,
            bookTitle = bookTitle,
            message = "Analyzing book structure with Flash-Lite..."
        )
        _statusMap[bookId] = status
        _currentStatus.value = status
    }

    fun setSuccess(bookId: Long, bookTitle: String, sectionCount: Int) {
        val status = ExtractionStatus.Success(
            bookId = bookId,
            bookTitle = bookTitle,
            sectionCount = sectionCount,
            message = "Extracted $sectionCount sections successfully."
        )
        _statusMap[bookId] = status
        _currentStatus.value = status
    }

    fun setError(bookId: Long, bookTitle: String, error: String) {
        val status = ExtractionStatus.Error(
            bookId = bookId,
            bookTitle = bookTitle,
            message = error
        )
        _statusMap[bookId] = status
        _currentStatus.value = status
    }

    fun clearStatus(bookId: Long) {
        _statusMap.remove(bookId)
        if (_currentStatus.value is ExtractionStatus.Processing && (_currentStatus.value as ExtractionStatus.Processing).bookId == bookId) {
            _currentStatus.value = ExtractionStatus.Idle
        } else if (_currentStatus.value is ExtractionStatus.Success && (_currentStatus.value as ExtractionStatus.Success).bookId == bookId) {
            _currentStatus.value = ExtractionStatus.Idle
        } else if (_currentStatus.value is ExtractionStatus.Error && (_currentStatus.value as ExtractionStatus.Error).bookId == bookId) {
            _currentStatus.value = ExtractionStatus.Idle
        }
    }

    fun getStatus(bookId: Long): ExtractionStatus {
        return _statusMap[bookId] ?: ExtractionStatus.Idle
    }
}
