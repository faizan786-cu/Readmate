package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val books: List<BookWithChapterCount>) : LibraryUiState
}

class LibraryViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val attemptedCoverFetchIds = mutableSetOf<Long>()

    val uiState: StateFlow<LibraryUiState> = bookRepository.booksWithChapterCount
        .map { list ->
            checkAndTriggerMissingCovers(list)
            LibraryUiState.Success(list)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState.Loading
        )

    private fun checkAndTriggerMissingCovers(items: List<BookWithChapterCount>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (item in items) {
                val b = item.book
                if (b.coverImageUrl.isNullOrBlank() && !attemptedCoverFetchIds.contains(b.id)) {
                    attemptedCoverFetchIds.add(b.id)
                    bookRepository.triggerSilentCoverFetch(b.id, b.title, b.author)
                }
            }
        }
    }
}
