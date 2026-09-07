package com.example.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.QuoteCardImageExporter
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.model.WordTranslationResult
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.WisdomQuoteRepository
import com.example.data.repository.WordVaultRepository
import com.example.ui.screens.chat.WordTranslationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WisdomFilter {
    data object All : WisdomFilter
    data class ByBook(val bookId: Long, val bookTitle: String) : WisdomFilter
    data class ByChapter(
        val chapterId: Long,
        val chapterTitle: String,
        val bookId: Long,
        val bookTitle: String
    ) : WisdomFilter
    data object Favorites : WisdomFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
class WisdomReelsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val wisdomQuoteRepository: WisdomQuoteRepository,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val wordVaultRepository: WordVaultRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    companion object {
        private const val KEY_CURRENT_PAGE = "current_page_index"
    }

    var translationState by mutableStateOf<WordTranslationState>(WordTranslationState.Idle)
        private set

    private val _wordAutoSavedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val wordAutoSavedEvent: SharedFlow<String> = _wordAutoSavedEvent.asSharedFlow()

    private val _currentFilter = MutableStateFlow<WisdomFilter>(WisdomFilter.All)
    val currentFilter: StateFlow<WisdomFilter> = _currentFilter.asStateFlow()

    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode: StateFlow<Boolean> = _isShuffleMode.asStateFlow()

    private val _shuffleSeed = MutableStateFlow(System.currentTimeMillis())

    // All books for filter bottom sheet
    val books: StateFlow<List<BookWithChapterCount>> = bookRepository.booksWithChapterCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered quotes observing Room DB reactively
    val quotesState: StateFlow<List<WisdomQuote>> = combine(
        _currentFilter,
        _isShuffleMode,
        _shuffleSeed
    ) { filter, isShuffle, seed ->
        Triple(filter, isShuffle, seed)
    }.flatMapLatest { (filter, isShuffle, seed) ->
        val rawFlow: Flow<List<WisdomQuote>> = when (filter) {
            is WisdomFilter.All -> wisdomQuoteRepository.observeAllQuotes()
            is WisdomFilter.Favorites -> wisdomQuoteRepository.observeFavoriteQuotes()
            is WisdomFilter.ByBook -> wisdomQuoteRepository.observeQuotesForBook(filter.bookId)
            is WisdomFilter.ByChapter -> wisdomQuoteRepository.observeQuotesForChapter(filter.chapterId)
        }

        rawFlow.map { list ->
            if (isShuffle && list.isNotEmpty()) {
                list.shuffled(java.util.Random(seed))
            } else {
                list
            }
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savedPageIndex: Int
        get() = savedStateHandle.get<Int>(KEY_CURRENT_PAGE) ?: 0

    fun onPageChanged(index: Int) {
        savedStateHandle[KEY_CURRENT_PAGE] = index
    }

    fun setFilter(filter: WisdomFilter) {
        if (_currentFilter.value != filter) {
            _currentFilter.value = filter
            savedStateHandle[KEY_CURRENT_PAGE] = 0
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffleMode.value
        _isShuffleMode.value = newShuffle
        _shuffleSeed.value = System.currentTimeMillis()
        savedStateHandle[KEY_CURRENT_PAGE] = 0
    }

    fun reShuffle() {
        _shuffleSeed.value = System.currentTimeMillis()
        savedStateHandle[KEY_CURRENT_PAGE] = 0
    }

    fun toggleFavorite(quote: WisdomQuote) {
        viewModelScope.launch(Dispatchers.IO) {
            wisdomQuoteRepository.toggleFavorite(quote.id, !quote.isFavorite)
        }
    }

    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>> {
        return chapterRepository.getChaptersForBook(bookId)
    }

    fun dismissTranslation() {
        translationState = WordTranslationState.Idle
    }

    /**
     * Tap & Hold word translation with automatic Word Vault persistence.
     */
    fun translateWord(
        selectedWord: String,
        sentence: String,
        quote: WisdomQuote
    ) {
        val cleanWord = selectedWord.trim()
        val cleanSentence = sentence.trim().ifBlank { quote.englishQuote }
        if (cleanWord.isEmpty()) return

        translationState = WordTranslationState.Loading(
            word = cleanWord,
            sentence = cleanSentence,
            targetMessageId = quote.messageId ?: 0L
        )

        viewModelScope.launch {
            try {
                // 1. SMART DUPLICATE CHECK: Search local Word Vault first
                val existingEntry = wordVaultRepository.findMatchingContextEntry(
                    word = cleanWord,
                    sentence = cleanSentence,
                    currentChapterId = quote.chapterId,
                    currentMessageId = quote.messageId
                ) ?: wordVaultRepository.findExistingWordEntry(
                    chapterId = quote.chapterId,
                    word = cleanWord,
                    sentence = cleanSentence,
                    messageId = quote.messageId
                )

                if (existingEntry != null) {
                    translationState = WordTranslationState.Success(
                        translation = WordTranslationResult(
                            word = existingEntry.word,
                            simpleMeaning = existingEntry.meaning,
                            contextMeaning = existingEntry.contextMeaning,
                            originalSentence = existingEntry.originalSentence,
                            sentenceUrduExplanation = existingEntry.explanation,
                            phraseOrIdiomExplanation = existingEntry.phraseOrIdiomExplanation,
                            simpleExample = existingEntry.simpleExample,
                            exampleUrduExplanation = existingEntry.exampleMeaning
                        ),
                        originalSentence = if (cleanSentence.isNotBlank()) cleanSentence else existingEntry.originalSentence,
                        surroundingContext = quote.englishQuote,
                        existingVaultEntry = existingEntry,
                        isSaved = true,
                        isSaving = false,
                        targetMessageId = quote.messageId ?: 0L,
                        originBookTitle = quote.bookTitle,
                        originChapterNumber = quote.chapterNumber,
                        originChapterId = quote.chapterId,
                        originMessageId = quote.messageId
                    )
                    _wordAutoSavedEvent.tryEmit(cleanWord)
                    return@launch
                }

                // 2. Call Gemini API for translation in context
                val result = if (geminiRepository.hasApiKey()) {
                    geminiRepository.translateWordInContext(
                        selectedWord = cleanWord,
                        sentence = cleanSentence,
                        surroundingContext = quote.englishQuote,
                        bookTitle = quote.bookTitle,
                        chapterTitle = "Chapter ${quote.chapterNumber}"
                    )
                } else {
                    Result.failure(IllegalStateException("Please configure Gemini API key in Settings for AI translation."))
                }

                result.onSuccess { translationResult ->
                    val finalSentence = translationResult.originalSentence.ifBlank { cleanSentence }
                    val newEntry = WordVaultEntry(
                        bookId = quote.bookId,
                        chapterId = quote.chapterId,
                        messageId = quote.messageId,
                        word = translationResult.word.trim().ifBlank { cleanWord },
                        meaning = translationResult.simpleMeaning.trim(),
                        contextMeaning = translationResult.contextMeaning.trim(),
                        originalSentence = finalSentence.trim(),
                        explanation = translationResult.sentenceUrduExplanation.trim().ifBlank { translationResult.contextMeaning.trim() },
                        phraseOrIdiomExplanation = translationResult.phraseOrIdiomExplanation.trim(),
                        simpleExample = translationResult.simpleExample.trim(),
                        exampleMeaning = translationResult.exampleUrduExplanation.trim(),
                        createdAt = System.currentTimeMillis()
                    )

                    val savedId = wordVaultRepository.saveOrUpdateWord(newEntry)
                    val savedEntryWithId = newEntry.copy(id = savedId)

                    translationState = WordTranslationState.Success(
                        translation = translationResult,
                        originalSentence = cleanSentence,
                        surroundingContext = quote.englishQuote,
                        existingVaultEntry = savedEntryWithId,
                        isSaved = true,
                        isSaving = false,
                        targetMessageId = quote.messageId ?: 0L,
                        originBookTitle = quote.bookTitle,
                        originChapterNumber = quote.chapterNumber,
                        originChapterId = quote.chapterId,
                        originMessageId = quote.messageId
                    )
                    _wordAutoSavedEvent.tryEmit(newEntry.word)
                }.onFailure { error ->
                    translationState = WordTranslationState.Error(
                        word = cleanWord,
                        sentence = cleanSentence,
                        surroundingContext = quote.englishQuote,
                        errorMessage = error.message ?: "Word ko samajhne mein problem aa gayi. Dobara try karo.",
                        targetMessageId = quote.messageId ?: 0L
                    )
                }
            } catch (e: Exception) {
                translationState = WordTranslationState.Error(
                    word = cleanWord,
                    sentence = cleanSentence,
                    surroundingContext = quote.englishQuote,
                    errorMessage = e.message ?: "Word ko samajhne mein problem aa gayi. Dobara try karo.",
                    targetMessageId = quote.messageId ?: 0L
                )
            }
        }
    }

    fun saveWordToVault(translation: WordTranslationResult, fallbackSentence: String, quote: WisdomQuote?) {
        viewModelScope.launch {
            if (quote == null) return@launch
            val entry = WordVaultEntry(
                bookId = quote.bookId,
                chapterId = quote.chapterId,
                messageId = quote.messageId,
                word = translation.word.trim(),
                meaning = translation.simpleMeaning.trim(),
                contextMeaning = translation.contextMeaning.trim(),
                originalSentence = translation.originalSentence.ifBlank { fallbackSentence }.trim(),
                explanation = translation.sentenceUrduExplanation.trim().ifBlank { translation.contextMeaning.trim() },
                phraseOrIdiomExplanation = translation.phraseOrIdiomExplanation.trim(),
                simpleExample = translation.simpleExample.trim(),
                exampleMeaning = translation.exampleUrduExplanation.trim(),
                createdAt = System.currentTimeMillis()
            )
            val savedId = wordVaultRepository.saveOrUpdateWord(entry)
            (translationState as? WordTranslationState.Success)?.let { curr ->
                translationState = curr.copy(isSaved = true, existingVaultEntry = entry.copy(id = savedId))
            }
            _wordAutoSavedEvent.tryEmit(entry.word)
        }
    }

    fun shareQuote(context: Context, quote: WisdomQuote, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            QuoteCardImageExporter.renderAndShareQuoteCard(context, quote)
            onComplete?.invoke()
        }
    }

    fun shareBitmap(context: Context, bitmap: android.graphics.Bitmap, quote: WisdomQuote, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            QuoteCardImageExporter.shareBitmap(context, bitmap, quote)
            onComplete?.invoke()
        }
    }

    fun saveQuoteToGallery(context: Context, quote: WisdomQuote, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = QuoteCardImageExporter.saveQuoteToGallery(context, quote)
            onResult(result.isSuccess)
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap, quote: WisdomQuote, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = QuoteCardImageExporter.saveBitmapToGallery(context, bitmap, quote)
            onResult(result.isSuccess)
        }
    }
}
