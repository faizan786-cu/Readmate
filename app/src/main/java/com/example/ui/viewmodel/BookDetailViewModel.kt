package com.example.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.ChapterPdfExporter
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.manager.DailyRetentionManager
import com.example.data.model.ChapterUnlockStatus
import com.example.data.model.SequentialChapterLockManager
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.QuizRepository
import com.example.ui.components.PdfExportUiState
import com.example.ui.navigation.ReaderNavigationGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterMessageRepository: ChapterMessageRepository,
    private val dailyRetentionManager: DailyRetentionManager? = null,
    private val quizRepository: QuizRepository? = null
) : ViewModel() {

    val dailyRetentionState: StateFlow<DailyRetentionStateEntity?> = (dailyRetentionManager?.observeTodayRetentionState() ?: flowOf(null))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    val book: StateFlow<Book?> = bookRepository.observeBook(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isReaderLocked: StateFlow<Boolean> = combine(
        dailyRetentionState,
        quizRepository?.observePendingRetentionQuizzesCount() ?: flowOf(0),
        book
    ) { retentionState, pendingCount, currentBook ->
        ReaderNavigationGuard.isReaderLocked(
            retentionState = retentionState,
            totalBooksCount = if (currentBook != null) 1 else 0,
            pendingQuizzesCount = pendingCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val chapters: StateFlow<List<Chapter>> = chapterRepository.getChaptersForBook(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unlockStatuses: StateFlow<Map<Long, ChapterUnlockStatus>> = chapters
        .map { list ->
            SequentialChapterLockManager.evaluateUnlockStatuses(list)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        viewModelScope.launch {
            chapterRepository.purgeJunkChapters()
        }
    }

    var exportUiState by mutableStateOf<PdfExportUiState?>(null)
        private set

    fun toggleChapterCompletion(chapterId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            chapterRepository.updateCompletionStatus(chapterId, isCompleted)
        }
    }

    fun deleteBook(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                quizRepository?.deleteQuizzesForBook(bookId)
            } catch (_: Exception) {}
            bookRepository.deleteBookById(bookId)
            onDeleted()
        }
    }

    fun deleteChapter(chapterId: Long) {
        viewModelScope.launch {
            chapterRepository.deleteChapterById(chapterId)
        }
    }

    fun exportChapterToPdf(context: Context, chapter: Chapter) {
        val currentBook = book.value ?: return
        exportUiState = PdfExportUiState.Exporting

        viewModelScope.launch {
            val messages = chapterMessageRepository.getMessagesForChapter(chapter.id)
            if (messages.isEmpty()) {
                exportUiState = PdfExportUiState.Error(
                    "No saved passage explanations found in this chapter to export. Add and explain some passages first!"
                )
                return@launch
            }

            val result = ChapterPdfExporter.exportChapterToPdf(
                context = context.applicationContext,
                book = currentBook,
                chapter = chapter,
                messages = messages
            )

            exportUiState = result.fold(
                onSuccess = { PdfExportUiState.Success(it) },
                onFailure = { PdfExportUiState.Error(it.message ?: "Failed to generate PDF") }
            )
        }
    }

    fun dismissExportDialog() {
        exportUiState = null
    }
}
