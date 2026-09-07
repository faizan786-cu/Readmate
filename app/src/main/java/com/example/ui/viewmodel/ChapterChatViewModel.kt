package com.example.ui.viewmodel

import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.ChapterPdfExporter
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.manager.ExplanationJobState
import com.example.data.manager.ExplanationPipelineManager
import com.example.data.manager.QuoteExtractionEngine
import com.example.data.model.GeminiConnectionState
import com.example.data.model.WordTranslationResult
import com.example.data.ocr.TextMergeUtils
import com.example.data.pdf.PdfStorageManager
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WordVaultRepository
import com.example.ui.components.PdfExportUiState
import com.example.ui.screens.chat.ChatScrollPositionCache
import com.example.ui.screens.chat.WordTranslationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SnippetPayload(
    val pageIndex: Int,
    val cropRect: RectF,
    val viewWidth: Float,
    val viewHeight: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class ChapterChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val chapterRepository: ChapterRepository,
    private val bookRepository: BookRepository,
    private val chapterMessageRepository: ChapterMessageRepository,
    private val geminiRepository: GeminiRepository,
    private val wordVaultRepository: WordVaultRepository,
    private val quoteExtractionEngine: QuoteExtractionEngine? = null,
    private val explanationPipelineManager: ExplanationPipelineManager? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    val responseFontSizePercent: StateFlow<Int> = userPreferencesRepository?.responseFontSizePercent
        ?: kotlinx.coroutines.flow.MutableStateFlow(UserPreferencesRepository.DEFAULT_FONT_SIZE_PERCENT)

    val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val pipelineManager: ExplanationPipelineManager = explanationPipelineManager
        ?: ExplanationPipelineManager(
            geminiRepository = geminiRepository,
            chapterMessageRepository = chapterMessageRepository,
            bookRepository = bookRepository,
            chapterRepository = chapterRepository,
            quoteExtractionEngine = quoteExtractionEngine
        )

    val targetMessageId: Long? = savedStateHandle.get<String>("targetMessageId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("targetMessageId")

    var savedScrollIndex: Int?
        get() = savedStateHandle.get<Int>("chat_scroll_index") ?: ChatScrollPositionCache.getPosition(chapterId)?.first
        set(value) {
            if (value != null) {
                savedStateHandle["chat_scroll_index"] = value
                ChatScrollPositionCache.savePosition(chapterId, value, savedScrollOffset ?: 0)
            }
        }

    var savedScrollOffset: Int?
        get() = savedStateHandle.get<Int>("chat_scroll_offset") ?: ChatScrollPositionCache.getPosition(chapterId)?.second
        set(value) {
            if (value != null) {
                savedStateHandle["chat_scroll_offset"] = value
                ChatScrollPositionCache.savePosition(chapterId, savedScrollIndex ?: 0, value)
            }
        }

    fun updateScrollPosition(index: Int, offset: Int) {
        savedStateHandle["chat_scroll_index"] = index
        savedStateHandle["chat_scroll_offset"] = offset
        ChatScrollPositionCache.savePosition(chapterId, index, offset)
    }

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent: SharedFlow<Unit> = _scrollToBottomEvent.asSharedFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()

    var highlightedMessageId by mutableStateOf<Long?>(null)
        private set

    var activeJobState by mutableStateOf<ExplanationJobState.InProgress?>(null)
        private set

    var lastSnippetPayload by mutableStateOf<SnippetPayload?>(null)
        private set

    fun clearHighlightedMessage(id: Long) {
        if (highlightedMessageId == id) {
            highlightedMessageId = null
        }
    }

    val chapter: StateFlow<Chapter?> = chapterRepository.observeChapter(chapterId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val book: StateFlow<Book?> = chapter
        .flatMapLatest { chap ->
            if (chap != null) {
                bookRepository.observeBook(chap.bookId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val messages: StateFlow<List<ChapterMessage>> = chapterMessageRepository
        .observeMessagesForChapter(chapterId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chapterWords: StateFlow<List<WordVaultEntry>> = wordVaultRepository
        .observeWordsForChapter(chapterId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val connectionState: StateFlow<GeminiConnectionState> = geminiRepository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (geminiRepository.hasApiKey()) {
                GeminiConnectionState.Connected(geminiRepository.getMaskedApiKey() ?: "••••••••")
            } else {
                GeminiConnectionState.NotConnected
            }
        )

    var inputText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var activeErrorState by mutableStateOf<ExplanationJobState.Error?>(null)
        private set

    init {
        // Observe persistent background explanation state for this chapter
        viewModelScope.launch {
            pipelineManager.observeState(chapterId).collect { state ->
                when (state) {
                    is ExplanationJobState.Idle -> {
                        isLoading = false
                        activeJobState = null
                        activeErrorState = null
                    }
                    is ExplanationJobState.InProgress -> {
                        isLoading = true
                        errorMessage = null
                        activeErrorState = null
                        activeJobState = state
                        _scrollToTopEvent.tryEmit(Unit)
                        _scrollToBottomEvent.tryEmit(Unit)
                    }
                    is ExplanationJobState.Completed -> {
                        isLoading = false
                        errorMessage = null
                        activeJobState = null
                        activeErrorState = null
                        highlightedMessageId = state.messageId
                        _scrollToTopEvent.tryEmit(Unit)
                        _scrollToBottomEvent.tryEmit(Unit)
                    }
                    is ExplanationJobState.Error -> {
                        isLoading = false
                        activeJobState = null
                        errorMessage = state.errorMessage
                        activeErrorState = state
                    }
                }
            }
        }

        // Listen for direct completion events across scope boundaries
        viewModelScope.launch {
            pipelineManager.completionEvents.collect { (compChapterId, compMessageId) ->
                if (compChapterId == chapterId) {
                    highlightedMessageId = compMessageId
                    _scrollToTopEvent.tryEmit(Unit)
                    _scrollToBottomEvent.tryEmit(Unit)
                }
            }
        }
    }

    var translationState by mutableStateOf<WordTranslationState>(WordTranslationState.Idle)
        private set

    var exportUiState by mutableStateOf<PdfExportUiState?>(null)
        private set

    // --- PDF Reader & OCR State ---
    var isPdfReaderOpen by mutableStateOf(false)
        private set

    var isSnipModeActive by mutableStateOf(false)
        private set

    var isProcessingOcr by mutableStateOf(false)
        private set

    var part1CapturedText by mutableStateOf<String?>(null)
        private set

    var part1WordCount by mutableIntStateOf(0)
        private set

    var ocrErrorMessage by mutableStateOf<String?>(null)
        private set

    var pdfTargetPage by mutableStateOf<Int?>(null)
        private set

    fun openPdfReader(targetPage: Int? = null) {
        pdfTargetPage = targetPage
        isPdfReaderOpen = true
        isSnipModeActive = false
        part1CapturedText = null
        part1WordCount = 0
        ocrErrorMessage = null
    }

    fun closePdfReader() {
        pdfTargetPage = null
        isPdfReaderOpen = false
        isSnipModeActive = false
        part1CapturedText = null
        part1WordCount = 0
    }

    fun startSnipMode() {
        isSnipModeActive = true
        part1CapturedText = null
        part1WordCount = 0
        ocrErrorMessage = null
    }

    fun cancelSnipMode() {
        isSnipModeActive = false
        part1CapturedText = null
        part1WordCount = 0
        ocrErrorMessage = null
    }

    fun attachPdfUri(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val currentBookId = chapter.value?.bookId ?: book.value?.id ?: 1L
            val result = PdfStorageManager.importPdfForChapter(
                context = context.applicationContext,
                uri = uri,
                bookId = currentBookId,
                chapterId = chapterId
            )

            result.onSuccess { importResult ->
                chapterRepository.updatePdfInfo(
                    chapterId = chapterId,
                    filePath = importResult.filePath,
                    fileName = importResult.fileName,
                    totalPages = importResult.totalPages,
                    lastReadPage = 0
                )
                isPdfReaderOpen = true
                isSnipModeActive = false
            }.onFailure { error ->
                errorMessage = "Could not attach PDF: ${error.message}"
            }
        }
    }

    var activeViewerPageIndex by mutableIntStateOf(0)
        private set

    fun updatePdfLastReadPage(pageIndex: Int) {
        activeViewerPageIndex = pageIndex
        if (pageIndex > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val currentChapter = chapter.value
                    val currentBookId = currentChapter?.bookId ?: book.value?.id
                    if (currentBookId != null) {
                        bookRepository.updateHighestReadPageAnchor(currentBookId, pageIndex)
                        bookRepository.updateLastReadPage(currentBookId, pageIndex)
                    }
                    chapterRepository.updateHighestReadPageAnchor(chapterId, pageIndex)
                    chapterRepository.updateLastReadPage(chapterId, pageIndex)

                    val endPage = currentChapter?.let { if (it.endPage >= it.startPage) it.endPage else it.pdfTotalPages } ?: 0
                    if (endPage > 0 && pageIndex >= endPage && currentChapter?.isCompleted == false) {
                        chapterRepository.updateCompletionStatus(chapterId, true)
                    }
                } catch (_: Throwable) {
                    // Non-blocking
                }
            }
        }
    }

    fun onPdfPageScrolled(pageIndex: Int) {
        activeViewerPageIndex = pageIndex
    }

    /**
     * Tamper-Proof Reading Anchor:
     * Advances the verified reading progress anchor ONLY when a passage is snipped and sent for explanation.
     * Never decreases if a user reviews or snips from an earlier page.
     */
    fun recordPassageSnippetProgress(sourcePageNumber: Int) {
        if (sourcePageNumber <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentChapter = chapter.value
                val currentBookId = currentChapter?.bookId ?: book.value?.id
                if (currentBookId != null) {
                    bookRepository.updateHighestReadPageAnchor(currentBookId, sourcePageNumber)
                }
                chapterRepository.updateHighestReadPageAnchor(chapterId, sourcePageNumber)

                val endPage = currentChapter?.let { if (it.endPage >= it.startPage) it.endPage else it.pdfTotalPages } ?: 0
                if (endPage > 0 && sourcePageNumber >= endPage && currentChapter?.isCompleted == false) {
                    chapterRepository.updateCompletionStatus(chapterId, true)
                }
            } catch (_: Throwable) {
                // Fail-safe non-blocking
            }
        }
    }

    fun removeChapterPdf(context: android.content.Context) {
        val currentPath = chapter.value?.pdfFilePath
        viewModelScope.launch {
            PdfStorageManager.deletePdfFile(currentPath)
            chapterRepository.updatePdfInfo(
                chapterId = chapterId,
                filePath = null,
                fileName = null,
                totalPages = 0,
                lastReadPage = 0
            )
            isPdfReaderOpen = false
            isSnipModeActive = false
            part1CapturedText = null
            part1WordCount = 0
        }
    }

    /**
     * Single-Page Visual Snip:
     * Immediately dismisses the reader overlay with zero UI latency, saves the snippet payload in session,
     * and delegates OCR extraction and Gemini synthesis to the persistent background pipeline manager.
     */
    fun explainSinglePageSnippet(
        pageIndex: Int,
        cropRect: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f
    ) {
        val pdfPath = chapter.value?.pdfFilePath ?: book.value?.pdfFilePath
        if (pdfPath.isNullOrBlank()) {
            ocrErrorMessage = "No PDF file attached to this chapter."
            return
        }

        if (!geminiRepository.hasApiKey()) {
            ocrErrorMessage = "No Gemini API key connected. Please configure your API key in Settings."
            return
        }

        // 1. Immediately save the cropped snippet payload into active session
        lastSnippetPayload = SnippetPayload(pageIndex, cropRect, viewWidth, viewHeight)

        // 2. Instantly dismiss reader and snip mode with ZERO UI latency
        isSnipModeActive = false
        isPdfReaderOpen = false
        part1CapturedText = null
        part1WordCount = 0
        isProcessingOcr = false
        ocrErrorMessage = null

        recordPassageSnippetProgress(sourcePageNumber = pageIndex + 1)

        val currentBookId = chapter.value?.bookId ?: book.value?.id
        val currentBookTitle = book.value?.title
        val currentChapterTitle = chapter.value?.title
        val currentAuthor = book.value?.author
        val currentChapNum = chapter.value?.chapterNumber ?: 1

        // 3. Kick off extraction & Gemini synthesis pipeline asynchronously in persistent background scope
        pipelineManager.startSnippetExplanation(
            chapterId = chapterId,
            pdfFilePath = pdfPath,
            pageIndex = pageIndex,
            cropRect = cropRect,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            authorName = currentAuthor,
            bookId = currentBookId,
            chapterNumber = currentChapNum
        )
    }

    /**
     * Multi-Page Step 1:
     * Extracts Part 1 from current page using Gemini Flash-Lite Vision, stores in memory,
     * and sets up the second step for the next page.
     */
    fun capturePart1ForMultiPage(
        pageIndex: Int,
        cropRect: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f
    ) {
        val pdfPath = chapter.value?.pdfFilePath ?: book.value?.pdfFilePath
        if (pdfPath.isNullOrBlank()) {
            ocrErrorMessage = "No PDF file attached to this chapter."
            return
        }

        if (!geminiRepository.hasApiKey()) {
            ocrErrorMessage = "No Gemini API key connected. Please configure your API key in Settings."
            return
        }

        isProcessingOcr = true
        ocrErrorMessage = null

        viewModelScope.launch {
            val visionResult = geminiRepository.extractTextFromPageRegion(
                pdfFilePath = pdfPath,
                pageIndex = pageIndex,
                cropRectNormalized = cropRect,
                viewWidth = viewWidth,
                viewHeight = viewHeight
            )
            isProcessingOcr = false

            visionResult.onSuccess { text ->
                part1CapturedText = text
                part1WordCount = TextMergeUtils.countWords(text)
                // isSnipModeActive stays true for Step 2!
            }.onFailure { error ->
                ocrErrorMessage = error.message ?: "Failed to extract text for Part 1."
            }
        }
    }

    /**
     * Multi-Page Step 2:
     * Immediately dismisses reader with zero UI latency, saves snippet payload,
     * and delegates Part 2 extraction, merging, and AI explanation to the persistent background pipeline manager.
     */
    fun mergeAndExplainMultiPage(
        pageIndex: Int,
        cropRect: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f
    ) {
        val pdfPath = chapter.value?.pdfFilePath ?: book.value?.pdfFilePath
        val part1 = part1CapturedText
        if (pdfPath.isNullOrBlank() || part1.isNullOrBlank()) {
            ocrErrorMessage = "Part 1 text missing. Please try snipping again."
            cancelSnipMode()
            return
        }

        if (!geminiRepository.hasApiKey()) {
            ocrErrorMessage = "No Gemini API key connected. Please configure your API key in Settings."
            return
        }

        // 1. Immediately save the cropped snippet payload into active session
        lastSnippetPayload = SnippetPayload(pageIndex, cropRect, viewWidth, viewHeight)

        // 2. Instantly dismiss reader and snip mode with ZERO UI latency
        isSnipModeActive = false
        isPdfReaderOpen = false
        part1CapturedText = null
        part1WordCount = 0
        isProcessingOcr = false
        ocrErrorMessage = null

        recordPassageSnippetProgress(sourcePageNumber = pageIndex + 1)

        val currentBookId = chapter.value?.bookId ?: book.value?.id
        val currentBookTitle = book.value?.title
        val currentChapterTitle = chapter.value?.title
        val currentAuthor = book.value?.author
        val currentChapNum = chapter.value?.chapterNumber ?: 1

        // 3. Kick off extraction & Gemini synthesis pipeline asynchronously in persistent background scope
        pipelineManager.startMergedSnippetExplanation(
            chapterId = chapterId,
            pdfFilePath = pdfPath,
            pageIndex = pageIndex,
            part1Text = part1,
            cropRect = cropRect,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            authorName = currentAuthor,
            bookId = currentBookId,
            chapterNumber = currentChapNum
        )
    }

    fun onInputTextChanged(newText: String) {
        inputText = newText
        if (errorMessage != null && newText.isNotBlank()) {
            errorMessage = null
        }
    }

    fun sendMessage(passageText: String? = null) {
        val textToExplain = (passageText ?: inputText).trim()
        if (textToExplain.isEmpty()) return

        if (!geminiRepository.hasApiKey()) {
            errorMessage = "No Gemini API key connected. Please configure your API key in Settings."
            return
        }

        val currentBookId = chapter.value?.bookId ?: book.value?.id
        val currentBookTitle = book.value?.title
        val currentChapterTitle = chapter.value?.title
        val currentAuthor = book.value?.author
        val currentChapNum = chapter.value?.chapterNumber ?: 1

        pipelineManager.startExplanation(
            chapterId = chapterId,
            passage = textToExplain,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            authorName = currentAuthor,
            bookId = currentBookId,
            chapterNumber = currentChapNum
        )
        inputText = ""
    }

    /**
     * Manual Translate Word Action:
     * Triggered ONLY when the user selects a word/phrase in the passage and taps "Translate".
     * 1. First checks local Word Vault across all passages/chapters for an existing contextual meaning.
     * 2. If existing contextual match is found: DO NOT call Gemini, open saved explanation immediately.
     *    If learned from a different passage, shows origin information and 'View Original Passage' button.
     *    Also attaches a tag to the current message if not yet present.
     * 3. If new contextual meaning: Calls Gemini -> auto-saves to Word Vault -> creates tag -> shows explanation.
     */
    fun translateWord(
        selectedWord: String,
        sentence: String,
        surroundingContext: String? = null,
        messageId: Long = 0L
    ) {
        val cleanWord = selectedWord.trim()
        val cleanSentence = sentence.trim().ifBlank { cleanWord }
        if (cleanWord.isEmpty()) return

        translationState = WordTranslationState.Loading(
            word = cleanWord,
            sentence = cleanSentence,
            targetMessageId = messageId
        )

        viewModelScope.launch {
            try {
                // 1. SMART DUPLICATE CHECK: Search local Word Vault for contextual match
                val existingEntry = wordVaultRepository.findMatchingContextEntry(
                    word = cleanWord,
                    sentence = cleanSentence,
                    currentChapterId = chapterId,
                    currentMessageId = if (messageId > 0) messageId else null
                )

                if (existingEntry != null) {
                    // Check if entry came from a different passage (different message or chapter)
                    val isDifferentPassage = (existingEntry.messageId != null && messageId > 0 && existingEntry.messageId != messageId) ||
                            (existingEntry.chapterId != chapterId)

                    var originBookTitle: String? = null
                    var originChapterTitle: String? = null
                    var originChapterNumber: Int? = null

                    if (isDifferentPassage) {
                        val originBook = bookRepository.getBook(existingEntry.bookId)
                        val originChapter = chapterRepository.getChapter(existingEntry.chapterId)
                        originBookTitle = originBook?.title
                        originChapterTitle = originChapter?.title
                        originChapterNumber = originChapter?.chapterNumber
                    }

                    // If not yet tagged in current message, ensure tag is created for current message too
                    if (messageId > 0 && existingEntry.messageId != messageId) {
                        val currentBookId = chapter.value?.bookId
                            ?: book.value?.id
                            ?: chapterRepository.getChapter(chapterId)?.bookId
                            ?: existingEntry.bookId

                        val localCopyForCurrentMessage = existingEntry.copy(
                            id = 0L,
                            bookId = currentBookId,
                            chapterId = chapterId,
                            messageId = messageId,
                            createdAt = System.currentTimeMillis()
                        )
                        wordVaultRepository.saveOrUpdateWord(localCopyForCurrentMessage)
                    }

                    // Open saved explanation immediately - ZERO GEMINI CALLS
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
                        surroundingContext = surroundingContext,
                        existingVaultEntry = existingEntry,
                        isSaved = true,
                        isSaving = false,
                        targetMessageId = messageId,
                        originBookTitle = originBookTitle,
                        originChapterTitle = originChapterTitle,
                        originChapterNumber = originChapterNumber,
                        originPassageText = existingEntry.originalSentence,
                        originChapterId = existingEntry.chapterId,
                        originMessageId = existingEntry.messageId,
                        isFromDifferentPassage = isDifferentPassage
                    )
                    return@launch
                }

                // 2. NEW CONTEXTUAL MEANING: Call Gemini API
                val currentBookTitle = book.value?.title
                val currentChapterTitle = chapter.value?.title

                val result = geminiRepository.translateWordInContext(
                    selectedWord = cleanWord,
                    sentence = cleanSentence,
                    surroundingContext = surroundingContext,
                    bookTitle = currentBookTitle,
                    chapterTitle = currentChapterTitle
                )

                result.onSuccess { translationResult ->
                    // AUTO-SAVE IMMEDIATELY TO WORD VAULT
                    val targetBookId = chapter.value?.bookId
                        ?: book.value?.id
                        ?: chapterRepository.getChapter(chapterId)?.bookId
                        ?: 1L

                    val finalSentence = translationResult.originalSentence.ifBlank { cleanSentence }
                    val newEntry = WordVaultEntry(
                        bookId = targetBookId,
                        chapterId = chapterId,
                        messageId = if (messageId > 0) messageId else null,
                        word = translationResult.word.trim().ifBlank { cleanWord },
                        meaning = translationResult.simpleMeaning.trim(),
                        contextMeaning = translationResult.contextMeaning.trim(),
                        originalSentence = finalSentence.trim(),
                        explanation = translationResult.sentenceUrduExplanation.trim().ifBlank { translationResult.contextMeaning.trim() },
                        phraseOrIdiomExplanation = translationResult.phraseOrIdiomExplanation.trim(),
                        simpleExample = translationResult.simpleExample.trim(),
                        exampleMeaning = translationResult.exampleUrduExplanation.trim()
                    )

                    val savedId = wordVaultRepository.saveOrUpdateWord(newEntry)
                    val savedEntryWithId = newEntry.copy(id = savedId)

                    // Show success explanation with auto-saved status
                    translationState = WordTranslationState.Success(
                        translation = translationResult,
                        originalSentence = cleanSentence,
                        surroundingContext = surroundingContext,
                        existingVaultEntry = savedEntryWithId,
                        isSaved = true,
                        isSaving = false,
                        targetMessageId = messageId
                    )
                }.onFailure { error ->
                    translationState = WordTranslationState.Error(
                        word = cleanWord,
                        sentence = cleanSentence,
                        surroundingContext = surroundingContext,
                        errorMessage = error.message ?: "Word ko samajhne mein problem aa gayi. Dobara try karo.",
                        targetMessageId = messageId
                    )
                }
            } catch (e: Exception) {
                translationState = WordTranslationState.Error(
                    word = cleanWord,
                    sentence = cleanSentence,
                    surroundingContext = surroundingContext,
                    errorMessage = e.message ?: "Word ko samajhne mein problem aa gayi. Dobara try karo.",
                    targetMessageId = messageId
                )
            }
        }
    }

    /**
     * Instant Open of an Existing Saved Word Translation (Zero Gemini / API calls).
     * Loads directly from local Room Word Vault.
     */
    fun openSavedWordTranslation(entry: WordVaultEntry) {
        viewModelScope.launch {
            val isDifferentPassage = (entry.messageId != null && entry.chapterId != chapterId)
            var originBookTitle: String? = null
            var originChapterTitle: String? = null
            var originChapterNumber: Int? = null

            if (isDifferentPassage) {
                val originBook = bookRepository.getBook(entry.bookId)
                val originChapter = chapterRepository.getChapter(entry.chapterId)
                originBookTitle = originBook?.title
                originChapterTitle = originChapter?.title
                originChapterNumber = originChapter?.chapterNumber
            }

            translationState = WordTranslationState.Success(
                translation = WordTranslationResult(
                    word = entry.word,
                    simpleMeaning = entry.meaning,
                    contextMeaning = entry.contextMeaning,
                    originalSentence = entry.originalSentence,
                    sentenceUrduExplanation = entry.explanation,
                    phraseOrIdiomExplanation = entry.phraseOrIdiomExplanation,
                    simpleExample = entry.simpleExample,
                    exampleUrduExplanation = entry.exampleMeaning
                ),
                originalSentence = entry.originalSentence,
                surroundingContext = null,
                existingVaultEntry = entry,
                isSaved = true,
                targetMessageId = entry.messageId ?: 0L,
                originBookTitle = originBookTitle,
                originChapterTitle = originChapterTitle,
                originChapterNumber = originChapterNumber,
                originPassageText = entry.originalSentence,
                originChapterId = entry.chapterId,
                originMessageId = entry.messageId,
                isFromDifferentPassage = isDifferentPassage
            )
        }
    }

    /**
     * Auto-save is performed automatically upon translation.
     * This method remains for any manual programmatic save if needed.
     */
    fun saveWordToVault(translation: WordTranslationResult, fallbackSentence: String) {
        val currentSuccess = translationState as? WordTranslationState.Success ?: return
        if (currentSuccess.isSaving || currentSuccess.isSaved) {
            dismissTranslation()
            return
        }

        translationState = currentSuccess.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val targetBookId = chapter.value?.bookId
                    ?: book.value?.id
                    ?: chapterRepository.getChapter(chapterId)?.bookId
                    ?: 1L

                val finalSentence = translation.originalSentence.ifBlank { fallbackSentence }
                val entry = WordVaultEntry(
                    bookId = targetBookId,
                    chapterId = chapterId,
                    messageId = if (currentSuccess.targetMessageId > 0) currentSuccess.targetMessageId else null,
                    word = translation.word.trim(),
                    meaning = translation.simpleMeaning.trim(),
                    contextMeaning = translation.contextMeaning.trim(),
                    originalSentence = finalSentence.trim(),
                    explanation = translation.sentenceUrduExplanation.trim().ifBlank { translation.contextMeaning.trim() },
                    phraseOrIdiomExplanation = translation.phraseOrIdiomExplanation.trim(),
                    simpleExample = translation.simpleExample.trim(),
                    exampleMeaning = translation.exampleUrduExplanation.trim()
                )

                wordVaultRepository.saveOrUpdateWord(entry)
                dismissTranslation()
            } catch (e: Exception) {
                translationState = currentSuccess.copy(isSaving = false)
            }
        }
    }

    fun dismissTranslation() {
        translationState = WordTranslationState.Idle
    }

    fun regenerateMessage(message: ChapterMessage) {
        val currentBookId = chapter.value?.bookId ?: book.value?.id
        val currentBookTitle = book.value?.title
        val currentChapterTitle = chapter.value?.title
        val currentAuthor = book.value?.author
        val currentChapNum = chapter.value?.chapterNumber ?: 1

        pipelineManager.startRegeneration(
            chapterId = chapterId,
            messageId = message.id,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            authorName = currentAuthor,
            bookId = currentBookId,
            chapterNumber = currentChapNum
        )
    }

    fun deleteMessage(
        message: ChapterMessage,
        context: android.content.Context? = null,
        onDeleted: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (activeJobState?.targetMessageId == message.id) {
                pipelineManager.cancelJob(chapterId)
            }
            chapterMessageRepository.deleteMessageById(message.id)

            // Clean up any local cached audio/tts files for this message ID
            try {
                val cacheDir = context?.cacheDir
                cacheDir?.listFiles()?.forEach { file ->
                    if (file.name.contains("${message.id}")) {
                        file.delete()
                    }
                }
            } catch (ignored: Throwable) {}

            onDeleted?.invoke()
        }
    }

    fun cancelActiveGeneration() {
        pipelineManager.cancelJob(chapterId)
        isLoading = false
        activeJobState = null
        activeErrorState = null
        errorMessage = null
    }

    fun retryLastExplanation() {
        retrySend()
    }

    fun retrySend(passageToRetry: String? = null) {
        val currentBookId = chapter.value?.bookId ?: book.value?.id
        val currentBookTitle = book.value?.title
        val currentChapterTitle = chapter.value?.title
        val currentAuthor = book.value?.author
        val currentChapNum = chapter.value?.chapterNumber ?: 1

        if (passageToRetry != null && passageToRetry.isNotBlank()) {
            pipelineManager.startExplanation(
                chapterId = chapterId,
                passage = passageToRetry,
                bookTitle = currentBookTitle,
                chapterTitle = currentChapterTitle,
                authorName = currentAuthor,
                bookId = currentBookId,
                chapterNumber = currentChapNum
            )
        } else {
            pipelineManager.retryExplanation(
                chapterId = chapterId,
                bookTitle = currentBookTitle,
                chapterTitle = currentChapterTitle,
                authorName = currentAuthor,
                bookId = currentBookId,
                chapterNumber = currentChapNum
            )
        }
    }

    fun clearError() {
        errorMessage = null
        ocrErrorMessage = null
        pipelineManager.clearError(chapterId)
    }

    fun clearOcrError() {
        ocrErrorMessage = null
    }

    fun deleteMessage(message: ChapterMessage) {
        viewModelScope.launch {
            chapterMessageRepository.deleteMessage(message)
        }
    }

    fun exportCurrentChapterToPdf(context: android.content.Context) {
        val currentBook = book.value
        val currentChapter = chapter.value
        if (currentBook == null || currentChapter == null) return

        exportUiState = PdfExportUiState.Exporting

        viewModelScope.launch {
            val msgs = chapterMessageRepository.getMessagesForChapter(chapterId)
            if (msgs.isEmpty()) {
                exportUiState = PdfExportUiState.Error(
                    "No saved passage explanations found in this chapter to export. Add and explain some passages first!"
                )
                return@launch
            }

            val result = ChapterPdfExporter.exportChapterToPdf(
                context = context.applicationContext,
                book = currentBook,
                chapter = currentChapter,
                messages = msgs
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

    fun toggleChapterCompletion() {
        val current = chapter.value ?: return
        val newStatus = !current.isCompleted
        viewModelScope.launch {
            chapterRepository.updateCompletionStatus(chapterId, newStatus)
        }
    }
}

