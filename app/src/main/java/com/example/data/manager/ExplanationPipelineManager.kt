package com.example.data.manager

import android.graphics.RectF
import android.util.Log
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.GeminiRepository
import com.example.data.ocr.TextMergeUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

sealed class ExplanationJobState {
    object Idle : ExplanationJobState()

    data class InProgress(
        val passage: String,
        val startedAt: Long = System.currentTimeMillis(),
        val pageNumber: Int? = null,
        val statusLabel: String = "Synthesizing...",
        val targetMessageId: Long? = null,
        val isRegeneration: Boolean = (targetMessageId != null)
    ) : ExplanationJobState()

    data class Completed(
        val messageId: Long,
        val passage: String,
        val finishedAt: Long = System.currentTimeMillis(),
        val isRegeneration: Boolean = false
    ) : ExplanationJobState()

    data class Error(
        val passage: String,
        val errorMessage: String,
        val failedAt: Long = System.currentTimeMillis(),
        val pageNumber: Int? = null,
        val targetMessageId: Long? = null
    ) : ExplanationJobState()
}

/**
 * Persistent, lifecycle-independent explanation manager running on an application-level coroutine scope.
 * Guarantees that AI passage explanations continue uninterrupted even if the user navigates back to the PDF,
 * switches chapters, or backgrounds the application.
 * Persists completed explanations directly into the Room database and triggers asynchronous wisdom quote extraction.
 */
class ExplanationPipelineManager(
    private val geminiRepository: GeminiRepository,
    private val chapterMessageRepository: ChapterMessageRepository,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val quoteExtractionEngine: QuoteExtractionEngine? = null,
    private val mcqGenerationEngine: McqGenerationEngine? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ExplanationPipeline"
    }

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val _states = MutableStateFlow<Map<Long, ExplanationJobState>>(emptyMap())
    val states: StateFlow<Map<Long, ExplanationJobState>> = _states.asStateFlow()

    private val _completionEvents = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 16)
    val completionEvents: SharedFlow<Pair<Long, Long>> = _completionEvents.asSharedFlow()

    fun getState(chapterId: Long): ExplanationJobState {
        return _states.value[chapterId] ?: ExplanationJobState.Idle
    }

    fun observeState(chapterId: Long): Flow<ExplanationJobState> {
        return states.map { it[chapterId] ?: ExplanationJobState.Idle }
    }

    fun isGenerating(chapterId: Long): Boolean {
        return getState(chapterId) is ExplanationJobState.InProgress
    }

    fun getInProgressPassage(chapterId: Long): String? {
        return when (val state = getState(chapterId)) {
            is ExplanationJobState.InProgress -> state.passage
            is ExplanationJobState.Error -> state.passage
            else -> null
        }
    }

    fun startExplanation(
        chapterId: Long,
        passage: String,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1
    ) {
        val trimmedPassage = passage.trim()
        if (trimmedPassage.isEmpty()) return

        if (isGenerating(chapterId)) {
            Log.d(TAG, "Chapter $chapterId already has an explanation in progress. Ignoring duplicate start.")
            return
        }

        if (!geminiRepository.hasApiKey()) {
            _states.update { current ->
                current + (chapterId to ExplanationJobState.Error(
                    passage = trimmedPassage,
                    errorMessage = "No Gemini API key connected. Please configure your API key in Settings."
                ))
            }
            return
        }

        // Set in-progress state immediately
        _states.update { current ->
            current + (chapterId to ExplanationJobState.InProgress(
                passage = trimmedPassage,
                statusLabel = "Synthesizing..."
            ))
        }

        // Launch in persistent application-level scope
        val job = applicationScope.launch(ioDispatcher) {
            try {
                executeGeminiExplanation(
                    chapterId = chapterId,
                    trimmedPassage = trimmedPassage,
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    authorName = authorName,
                    bookId = bookId,
                    chapterNumber = chapterNumber
                )
            } catch (t: Throwable) {
                _states.update { current ->
                    current + (chapterId to ExplanationJobState.Error(
                        passage = trimmedPassage,
                        errorMessage = t.message ?: "Unexpected error during explanation generation."
                    ))
                }
            } finally {
                activeJobs.remove(chapterId)
            }
        }
        activeJobs[chapterId] = job
    }

    /**
     * Single-Page Snippet:
     * Immediately sets InProgress state ("Analyzing..."), performs OCR extraction via Gemini Vision,
     * updates status to "Synthesizing...", and completes AI explanation synthesis in applicationScope.
     */
    fun startSnippetExplanation(
        chapterId: Long,
        pdfFilePath: String,
        pageIndex: Int,
        cropRect: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1
    ) {
        if (isGenerating(chapterId)) {
            Log.d(TAG, "Chapter $chapterId already has an explanation in progress. Ignoring duplicate start.")
            return
        }

        val pageNumber = pageIndex + 1

        if (!geminiRepository.hasApiKey()) {
            _states.update { current ->
                current + (chapterId to ExplanationJobState.Error(
                    passage = "Page $pageNumber",
                    errorMessage = "No Gemini API key connected. Please configure your API key in Settings."
                ))
            }
            return
        }

        // 1. Immediately set InProgress state with "Analyzing..."
        _states.update { current ->
            current + (chapterId to ExplanationJobState.InProgress(
                passage = "Page $pageNumber",
                pageNumber = pageNumber,
                statusLabel = "Analyzing..."
            ))
        }

        val job = applicationScope.launch(ioDispatcher) {
            try {
                val ocrResult = geminiRepository.extractTextFromPageRegion(
                    pdfFilePath = pdfFilePath,
                    pageIndex = pageIndex,
                    cropRectNormalized = cropRect,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight
                )

                ocrResult.onSuccess { extractedText ->
                    val formattedPassage = if (!extractedText.trim().startsWith("[Page", ignoreCase = true)) {
                        "[Page $pageNumber]\n${extractedText.trim()}"
                    } else {
                        extractedText.trim()
                    }

                    // 2. Update status to "Synthesizing..."
                    _states.update { current ->
                        current + (chapterId to ExplanationJobState.InProgress(
                            passage = formattedPassage,
                            pageNumber = pageNumber,
                            statusLabel = "Synthesizing..."
                        ))
                    }

                    executeGeminiExplanation(
                        chapterId = chapterId,
                        trimmedPassage = formattedPassage,
                        bookTitle = bookTitle,
                        chapterTitle = chapterTitle,
                        authorName = authorName,
                        bookId = bookId,
                        chapterNumber = chapterNumber
                    )
                }.onFailure { error ->
                    _states.update { current ->
                        current + (chapterId to ExplanationJobState.Error(
                            passage = "Page $pageNumber",
                            errorMessage = error.message ?: "Failed to extract English text from selection."
                        ))
                    }
                }
            } catch (t: Throwable) {
                _states.update { current ->
                    current + (chapterId to ExplanationJobState.Error(
                        passage = "Page $pageNumber",
                        errorMessage = t.message ?: "Unexpected error during snippet processing."
                    ))
                }
            } finally {
                activeJobs.remove(chapterId)
            }
        }
        activeJobs[chapterId] = job
    }

    /**
     * Multi-Page Snippet:
     * Immediately sets InProgress state ("Analyzing..."), performs Part 2 OCR extraction,
     * merges Part 1 and Part 2, updates status to "Synthesizing...", and executes AI explanation.
     */
    fun startMergedSnippetExplanation(
        chapterId: Long,
        pdfFilePath: String,
        pageIndex: Int,
        part1Text: String,
        cropRect: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1
    ) {
        if (isGenerating(chapterId)) {
            Log.d(TAG, "Chapter $chapterId already has an explanation in progress. Ignoring duplicate start.")
            return
        }

        val pageNumber = pageIndex + 1

        if (!geminiRepository.hasApiKey()) {
            _states.update { current ->
                current + (chapterId to ExplanationJobState.Error(
                    passage = "Page $pageNumber",
                    errorMessage = "No Gemini API key connected. Please configure your API key in Settings."
                ))
            }
            return
        }

        // 1. Immediately set InProgress state with "Analyzing..."
        _states.update { current ->
            current + (chapterId to ExplanationJobState.InProgress(
                passage = "Page $pageNumber",
                pageNumber = pageNumber,
                statusLabel = "Analyzing..."
            ))
        }

        val job = applicationScope.launch(ioDispatcher) {
            try {
                val ocrResult = geminiRepository.extractTextFromPageRegion(
                    pdfFilePath = pdfFilePath,
                    pageIndex = pageIndex,
                    cropRectNormalized = cropRect,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight
                )

                ocrResult.onSuccess { part2Text ->
                    val mergedText = TextMergeUtils.mergeMultiPagePassages(part1Text, part2Text)
                    val formattedPassage = if (!mergedText.trim().startsWith("[Page", ignoreCase = true)) {
                        "[Page $pageNumber]\n${mergedText.trim()}"
                    } else {
                        mergedText.trim()
                    }

                    // 2. Update status to "Synthesizing..."
                    _states.update { current ->
                        current + (chapterId to ExplanationJobState.InProgress(
                            passage = formattedPassage,
                            pageNumber = pageNumber,
                            statusLabel = "Synthesizing..."
                        ))
                    }

                    executeGeminiExplanation(
                        chapterId = chapterId,
                        trimmedPassage = formattedPassage,
                        bookTitle = bookTitle,
                        chapterTitle = chapterTitle,
                        authorName = authorName,
                        bookId = bookId,
                        chapterNumber = chapterNumber
                    )
                }.onFailure { error ->
                    _states.update { current ->
                        current + (chapterId to ExplanationJobState.Error(
                            passage = "Page $pageNumber",
                            errorMessage = error.message ?: "Failed to extract text for Part 2."
                        ))
                    }
                }
            } catch (t: Throwable) {
                _states.update { current ->
                    current + (chapterId to ExplanationJobState.Error(
                        passage = "Page $pageNumber",
                        errorMessage = t.message ?: "Unexpected error during multi-page snippet processing."
                    ))
                }
            } finally {
                activeJobs.remove(chapterId)
            }
        }
        activeJobs[chapterId] = job
    }

    /**
     * Cancels any in-flight active job for this chapter (OCR or Gemini generation),
     * immediately aborting network requests and coroutines.
     */
    fun cancelJob(chapterId: Long) {
        activeJobs[chapterId]?.cancel()
        activeJobs.remove(chapterId)
        _states.update { current -> current - chapterId }
    }

    /**
     * Regenerate an existing explanation atomically using the cached raw passage text.
     * Uses an intentional variation temperature (0.85f) to produce a fresh, alternative conceptual breakdown.
     * Atomically overwrites the existing entry record in Room while preserving original creation timestamp and ID.
     */
    fun startRegeneration(
        chapterId: Long,
        messageId: Long,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1
    ) {
        if (isGenerating(chapterId)) {
            Log.d(TAG, "Chapter $chapterId already has an explanation in progress. Ignoring duplicate start.")
            return
        }

        if (!geminiRepository.hasApiKey()) {
            _states.update { current ->
                current + (chapterId to ExplanationJobState.Error(
                    passage = "",
                    errorMessage = "No Gemini API key connected. Please configure your API key in Settings.",
                    targetMessageId = messageId
                ))
            }
            return
        }

        val job = applicationScope.launch(ioDispatcher) {
            val message = chapterMessageRepository.getMessageById(messageId)
            if (message == null) {
                _states.update { current ->
                    current + (chapterId to ExplanationJobState.Error(
                        passage = "",
                        errorMessage = "Entry not found in database.",
                        targetMessageId = messageId
                    ))
                }
                return@launch
            }

            val cachedPassage = message.originalText.trim()

            // 1. Immediately set InProgress state with targetMessageId
            _states.update { current ->
                current + (chapterId to ExplanationJobState.InProgress(
                    passage = cachedPassage,
                    statusLabel = "Regenerating...",
                    targetMessageId = messageId
                ))
            }

            try {
                executeGeminiExplanation(
                    chapterId = chapterId,
                    trimmedPassage = cachedPassage,
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    authorName = authorName,
                    bookId = bookId,
                    chapterNumber = chapterNumber,
                    targetMessageId = messageId,
                    temperature = 0.85f // intentional variation temperature for fresh breakdown
                )
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    _states.update { current ->
                        current + (chapterId to ExplanationJobState.Error(
                            passage = cachedPassage,
                            errorMessage = t.message ?: "Unexpected error during regeneration.",
                            targetMessageId = messageId
                        ))
                    }
                }
            } finally {
                activeJobs.remove(chapterId)
            }
        }
        activeJobs[chapterId] = job
    }

    private suspend fun executeGeminiExplanation(
        chapterId: Long,
        trimmedPassage: String,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1,
        targetMessageId: Long? = null,
        temperature: Float? = null
    ) {
        val history = chapterMessageRepository.getMessagesForChapter(chapterId)
            .filter { it.id != targetMessageId }
        val bTitle = bookTitle ?: bookId?.let { bookRepository.getBook(it)?.title }
        val cTitle = chapterTitle ?: chapterRepository.getChapter(chapterId)?.title
        val aName = authorName ?: bookId?.let { bookRepository.getBook(it)?.author }
        val bId = bookId ?: chapterRepository.getChapter(chapterId)?.bookId ?: 1L
        val cNum = chapterNumber.takeIf { it > 0 } ?: chapterRepository.getChapter(chapterId)?.chapterNumber ?: 1

        val result = geminiRepository.explainPassage(
            passage = trimmedPassage,
            conversationHistory = history,
            bookTitle = bTitle,
            chapterTitle = cTitle,
            authorName = aName,
            temperature = temperature
        )

        result.onSuccess { explanation ->
            val finalMessageId = if (targetMessageId != null) {
                chapterMessageRepository.updateAiResponse(targetMessageId, explanation)
                targetMessageId
            } else {
                chapterMessageRepository.saveMessage(
                    chapterId = chapterId,
                    originalText = trimmedPassage,
                    aiResponse = explanation
                )
            }

            _states.update { current ->
                current + (chapterId to ExplanationJobState.Completed(
                    messageId = finalMessageId,
                    passage = trimmedPassage,
                    isRegeneration = targetMessageId != null
                ))
            }
            _completionEvents.tryEmit(chapterId to finalMessageId)

            // Silently trigger background quote extraction engine
            quoteExtractionEngine?.let { engine ->
                try {
                    engine.extractAndSaveQuotesSilently(
                        passage = trimmedPassage,
                        bookId = bId,
                        bookTitle = bTitle.orEmpty(),
                        author = aName,
                        chapterId = chapterId,
                        chapterNumber = cNum,
                        chapterTitle = cTitle.orEmpty(),
                        messageId = finalMessageId
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Silently handled quote extraction error: ${e.message}")
                }
            }

            // Silently trigger background Flash-Lite MCQ generator
            mcqGenerationEngine?.let { engine ->
                try {
                    engine.generateAndSaveMcqsSilently(
                        passage = trimmedPassage,
                        explanation = explanation,
                        bookId = bId,
                        bookTitle = bTitle,
                        chapterId = chapterId,
                        chapterTitle = cTitle,
                        passageTurnId = finalMessageId
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Silently handled MCQ generation error: ${e.message}")
                }
            }
        }.onFailure { error ->
            _states.update { current ->
                current + (chapterId to ExplanationJobState.Error(
                    passage = trimmedPassage,
                    errorMessage = error.message ?: "Failed to get explanation from Gemini.",
                    targetMessageId = targetMessageId
                ))
            }
        }
    }

    fun retryExplanation(
        chapterId: Long,
        bookTitle: String? = null,
        chapterTitle: String? = null,
        authorName: String? = null,
        bookId: Long? = null,
        chapterNumber: Int = 1
    ) {
        val currentState = getState(chapterId)
        if (currentState is ExplanationJobState.Error) {
            if (currentState.targetMessageId != null) {
                startRegeneration(
                    chapterId = chapterId,
                    messageId = currentState.targetMessageId,
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    authorName = authorName,
                    bookId = bookId,
                    chapterNumber = chapterNumber
                )
            } else {
                startExplanation(
                    chapterId = chapterId,
                    passage = currentState.passage,
                    bookTitle = bookTitle,
                    chapterTitle = chapterTitle,
                    authorName = authorName,
                    bookId = bookId,
                    chapterNumber = chapterNumber
                )
            }
        }
    }

    fun clearError(chapterId: Long) {
        _states.update { current ->
            if (current[chapterId] is ExplanationJobState.Error) {
                current - chapterId
            } else {
                current
            }
        }
    }

    fun clearCompleted(chapterId: Long) {
        _states.update { current ->
            if (current[chapterId] is ExplanationJobState.Completed) {
                current - chapterId
            } else {
                current
            }
        }
    }
}
