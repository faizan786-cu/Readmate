package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.WordVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WordVaultFilterScope {
    CURRENT_CHAPTER,
    CURRENT_BOOK,
    ALL_BOOKS
}

class WordVaultViewModel(
    savedStateHandle: SavedStateHandle,
    private val wordVaultRepository: WordVaultRepository,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterMessageRepository: ChapterMessageRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    val initialChapterId: Long = when (val raw = savedStateHandle.get<Any>("chapterId")) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: -1L
        else -> -1L
    }.takeIf { it > 0 } ?: -1L

    val initialBookId: Long = when (val raw = savedStateHandle.get<Any>("bookId")) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: -1L
        else -> -1L
    }.takeIf { it > 0 } ?: -1L

    val chapter: StateFlow<Chapter?> = (
        if (initialChapterId > 0) {
            chapterRepository.observeChapter(initialChapterId)
        } else {
            flowOf(null)
        }
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val book: StateFlow<Book?> = (
        if (initialBookId > 0) {
            bookRepository.observeBook(initialBookId)
        } else if (initialChapterId > 0) {
            chapter.flatMapLatest { chap ->
                if (chap != null) bookRepository.observeBook(chap.bookId) else flowOf(null)
            }
        } else {
            flowOf(null)
        }
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _filterScope = MutableStateFlow(
        if (initialChapterId > 0) WordVaultFilterScope.CURRENT_CHAPTER
        else if (initialBookId > 0) WordVaultFilterScope.CURRENT_BOOK
        else WordVaultFilterScope.ALL_BOOKS
    )
    val filterScope: StateFlow<WordVaultFilterScope> = _filterScope.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    private val _searchQueryFlow = MutableStateFlow("")

    val filteredWords: StateFlow<List<WordVaultEntry>> = combine(
        filterScope,
        chapter,
        book,
        _searchQueryFlow
    ) { scope, currentChap, currentBk, query ->
        CombinedFilterParams(scope, currentChap, currentBk, query)
    }.flatMapLatest { params ->
        val baseFlow = when (params.scope) {
            WordVaultFilterScope.CURRENT_CHAPTER -> {
                val chapId = params.chapter?.id ?: initialChapterId
                if (chapId > 0) wordVaultRepository.observeWordsForChapter(chapId)
                else wordVaultRepository.allWords
            }
            WordVaultFilterScope.CURRENT_BOOK -> {
                val bkId = params.book?.id ?: params.chapter?.bookId ?: initialBookId
                if (bkId > 0) wordVaultRepository.observeWordsForBook(bkId)
                else wordVaultRepository.allWords
            }
            WordVaultFilterScope.ALL_BOOKS -> wordVaultRepository.allWords
        }

        combine(baseFlow, flowOf(params.query)) { list, q ->
            val cleanQuery = q.trim().lowercase()
            if (cleanQuery.isEmpty()) {
                list
            } else {
                list.filter { entry ->
                    entry.word.lowercase().contains(cleanQuery) ||
                            entry.meaning.lowercase().contains(cleanQuery) ||
                            entry.contextMeaning.lowercase().contains(cleanQuery) ||
                            entry.explanation.lowercase().contains(cleanQuery) ||
                            entry.simpleExample.lowercase().contains(cleanQuery) ||
                            entry.exampleMeaning.lowercase().contains(cleanQuery) ||
                            entry.originalSentence.lowercase().contains(cleanQuery)
                }
            }
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalCount: StateFlow<Int> = wordVaultRepository.totalWordCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun onSearchQueryChanged(newQuery: String) {
        searchQuery = newQuery
        _searchQueryFlow.value = newQuery
    }

    fun onFilterScopeSelected(scope: WordVaultFilterScope) {
        _filterScope.value = scope
    }

    fun deleteWord(entry: WordVaultEntry) {
        viewModelScope.launch {
            wordVaultRepository.deleteWord(entry)
        }
    }

    private data class CombinedFilterParams(
        val scope: WordVaultFilterScope,
        val chapter: Chapter?,
        val book: Book?,
        val query: String
    )
}
