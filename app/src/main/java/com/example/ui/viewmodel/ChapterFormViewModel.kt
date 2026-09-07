package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChapterRepository
import kotlinx.coroutines.launch

class ChapterFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository
) : ViewModel() {

    val chapterId: Long? = savedStateHandle.get<Long>("chapterId")?.takeIf { it > 0 }
    val bookId: Long? = savedStateHandle.get<Long>("bookId")?.takeIf { it > 0 }

    var chapterNumberText by mutableStateOf("1")
        private set
    var title by mutableStateOf("")
        private set

    var titleError by mutableStateOf<String?>(null)
        private set
    var numberError by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set

    private var targetBookId: Long = bookId ?: 0L
    private var createdAt: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            if (chapterId != null && chapterId > 0) {
                val chapter = chapterRepository.getChapter(chapterId)
                if (chapter != null) {
                    targetBookId = chapter.bookId
                    chapterNumberText = chapter.chapterNumber.toString()
                    title = chapter.title
                    createdAt = chapter.createdAt
                }
            } else if (bookId != null && bookId > 0) {
                targetBookId = bookId
                val nextNumber = chapterRepository.getNextChapterNumber(bookId)
                chapterNumberText = nextNumber.toString()
            }
            isLoading = false
        }
    }

    fun onChapterNumberChange(newNumber: String) {
        chapterNumberText = newNumber
        if (numberError != null) {
            numberError = null
        }
    }

    fun onTitleChange(newTitle: String) {
        title = newTitle
        if (titleError != null && newTitle.isNotBlank()) {
            titleError = null
        }
    }

    fun saveChapter(onSuccess: () -> Unit) {
        val trimmedTitle = title.trim()
        val num = chapterNumberText.trim().toIntOrNull()

        var hasError = false

        if (num == null || num <= 0) {
            numberError = "Chapter number must be a positive number"
            hasError = true
        }

        if (trimmedTitle.isBlank()) {
            titleError = "Chapter title is required"
            hasError = true
        }

        if (hasError || num == null) return

        if (isSubmitting) return
        isSubmitting = true

        viewModelScope.launch {
            try {
                if (chapterId != null && chapterId > 0) {
                    chapterRepository.updateChapter(
                        id = chapterId,
                        bookId = targetBookId,
                        chapterNumber = num,
                        title = trimmedTitle,
                        createdAt = createdAt
                    )
                } else {
                    chapterRepository.createChapter(
                        bookId = targetBookId,
                        chapterNumber = num,
                        title = trimmedTitle
                    )
                }
                onSuccess()
            } finally {
                isSubmitting = false
            }
        }
    }
}
