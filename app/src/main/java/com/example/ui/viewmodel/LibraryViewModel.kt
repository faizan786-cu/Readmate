package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.GeminiKeyRotationManager
import com.example.data.model.ExtractedBookPayload
import com.example.data.model.ExtractedChapterSection
import com.example.data.pdf.PdfStorageManager
import com.example.data.pdf.PdfStructureExtractor
import com.example.data.remote.drive.DriveBookItem
import com.example.data.remote.drive.DriveExplorerRepository
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.worker.CoverExtractionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LibraryTab {
    MY_BOOKS,
    EXPLORE
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val books: List<BookWithChapterCount>) : LibraryUiState
}

data class ExploreUiState(
    val books: List<DriveBookItem> = emptyList(),
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isCloudSearching: Boolean = false,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val errorMessage: String? = null
)

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val driveExplorerRepository: DriveExplorerRepository,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val attemptedCoverFetchIds = mutableSetOf<Long>()

    private val _selectedTab = MutableStateFlow(LibraryTab.MY_BOOKS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    // 1. My Books UI State
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

    // Set of normalized lowercase titles of books currently in local library
    val importedTitles: StateFlow<Set<String>> = bookRepository.booksWithChapterCount
        .map { list ->
            list.map { normalizeTitle(it.book.title) }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // 2. Explore Tab State & Cache
    private var cachedExploreBooks: List<DriveBookItem> = emptyList()
    private val _exploreUiState = MutableStateFlow(ExploreUiState(isLoading = true))
    val exploreUiState: StateFlow<ExploreUiState> = _exploreUiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Pre-fetch Explore catalog in background so switching is instantaneous
        loadExploreCatalog()
    }

    fun setTab(tab: LibraryTab) {
        _selectedTab.value = tab
        if (tab == LibraryTab.EXPLORE && cachedExploreBooks.isEmpty() && !_exploreUiState.value.isLoading) {
            loadExploreCatalog()
        }
    }

    fun loadExploreCatalog(forceRefresh: Boolean = false) {
        if (!forceRefresh && cachedExploreBooks.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _exploreUiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = driveExplorerRepository.fetchExploreBooks()
            result.fold(
                onSuccess = { items ->
                    cachedExploreBooks = items
                    val currentQuery = _exploreUiState.value.searchQuery.trim()
                    val filtered = if (currentQuery.isEmpty()) items else filterCatalog(currentQuery, items)
                    _exploreUiState.update {
                        it.copy(
                            books = filtered,
                            totalCount = items.size,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "Failed to load explore catalog: ${err.message}", err)
                    _exploreUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to connect to Explore repository."
                        )
                    }
                }
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val cleanQuery = query.trim()
        _exploreUiState.update { it.copy(searchQuery = query) }

        if (cleanQuery.isEmpty()) {
            searchJob?.cancel()
            _exploreUiState.update {
                it.copy(
                    books = cachedExploreBooks,
                    isCloudSearching = false
                )
            }
            return
        }

        // 1. Instant Client-Side Filter
        val instantMatches = filterCatalog(cleanQuery, cachedExploreBooks)
        _exploreUiState.update { it.copy(books = instantMatches) }

        // 2. Deep Cloud Search Fallback: If query >= 3 chars and local matches < 3, trigger debounced (450ms) search
        searchJob?.cancel()
        if (cleanQuery.length >= 3 && instantMatches.size < 3) {
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                delay(450)
                _exploreUiState.update { it.copy(isCloudSearching = true) }
                val searchResult = driveExplorerRepository.searchExploreBooks(cleanQuery)
                _exploreUiState.update { it.copy(isCloudSearching = false) }

                searchResult.onSuccess { cloudMatches ->
                    val merged = (instantMatches + cloudMatches).distinctBy { it.id }
                    _exploreUiState.update { it.copy(books = merged) }
                }
            }
        } else {
            _exploreUiState.update { it.copy(isCloudSearching = false) }
        }
    }

    private fun filterCatalog(query: String, catalog: List<DriveBookItem>): List<DriveBookItem> {
        val lowerQuery = query.lowercase()
        return catalog.filter { book ->
            val normTitle = book.title.lowercase()
            val tokens = book.title.split(" ", "_", "-", ":", ",")
            val author = book.author?.lowercase() ?: ""
            val rawName = book.rawName.lowercase()

            normTitle.contains(lowerQuery) ||
                    tokens.any { it.startsWith(lowerQuery, ignoreCase = true) } ||
                    rawName.contains(lowerQuery) ||
                    author.contains(lowerQuery)
        }
    }

    /**
     * One-Tap Stream, Download & Background Ingestion Flow:
     * 1. Pre-Check: Checks Room DB for duplicate title
     * 2. Inline Download Progress: Stream binary with progress tracking
     * 3. Pipeline Handoff: Parse structure, insert book + chapters, enqueue vision worker
     * 4. Call onSuccess callback for smooth transition
     */
    fun downloadAndImportBook(
        book: DriveBookItem,
        onAlreadyExists: () -> Unit,
        onSuccess: (Long, String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Pre-Check: verify if book already exists in Room DB
                val cleanTitle = normalizeTitle(book.title)
                val preExisting = bookRepository.getBookByCleanTitle(cleanTitle)
                    ?: bookRepository.getBookByCleanTitle(book.title)
                    ?: bookRepository.getBookByCleanTitle(normalizeTitle(book.rawName))

                if (preExisting != null) {
                    withContext(Dispatchers.Main) {
                        onAlreadyExists()
                    }
                    return@launch
                }

                // 2. Inline Download Progress Initialization
                _exploreUiState.update {
                    it.copy(downloadProgress = it.downloadProgress + (book.id to 0.02f))
                }

                val app = getApplication<Application>()
                val booksDir = File(app.filesDir, "books")
                if (!booksDir.exists()) {
                    booksDir.mkdirs()
                }
                val targetFile = File(booksDir, "${book.id}.pdf")

                // 3. Stream binary directly into app's private files folder
                val downloadResult = driveExplorerRepository.downloadBookFile(
                    fileId = book.id,
                    targetFile = targetFile,
                    expectedSizeBytes = book.sizeBytes
                ) { progress ->
                    _exploreUiState.update {
                        it.copy(downloadProgress = it.downloadProgress + (book.id to progress.coerceIn(0.05f, 0.95f)))
                    }
                }

                val savedFile = downloadResult.getOrThrow()
                _exploreUiState.update {
                    it.copy(downloadProgress = it.downloadProgress + (book.id to 0.98f))
                }

                // 4. Pipeline Handoff: Parse structure and insert into Room
                val totalPages = PdfStorageManager.getPdfPageCount(savedFile).coerceAtLeast(1)
                val rotationManager = GeminiKeyRotationManager(secureApiKeyStorage)
                val extractor = PdfStructureExtractor(rotationManager)
                val fallbackSections = extractor.createFallbackSections(totalPages, book.title)

                val payload: ExtractedBookPayload = try {
                    if (secureApiKeyStorage.hasApiKey()) {
                        extractor.extractBookAndStructure(savedFile, totalPages, book.title).getOrNull()
                            ?: ExtractedBookPayload(bookTitle = book.title, author = book.author, sections = fallbackSections)
                    } else {
                        ExtractedBookPayload(bookTitle = book.title, author = book.author, sections = fallbackSections)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Structure extraction fallback used: ${e.message}")
                    ExtractedBookPayload(bookTitle = book.title, author = book.author, sections = fallbackSections)
                }

                val finalTitle = payload.bookTitle?.trim()?.ifBlank { book.title } ?: book.title
                val finalAuthor = payload.author?.trim()?.ifBlank { null } ?: book.author

                // Save book into Room DB with high-res cover URL
                val newBookId = bookRepository.createBook(
                    title = finalTitle,
                    author = finalAuthor,
                    description = null,
                    coverImageUrl = book.highResCoverUrl,
                    pdfFilePath = savedFile.absolutePath,
                    pdfFileName = "${book.id}.pdf",
                    pdfTotalPages = totalPages,
                    pdfLastReadPage = 0
                )

                // Background Gemini Vision cover verification
                try {
                    CoverExtractionWorker.enqueue(
                        context = app,
                        bookId = newBookId,
                        filePath = savedFile.absolutePath,
                        bookTitle = finalTitle
                    )
                } catch (_: Exception) {}

                // Batch insert chapters mapped to newBookId
                val rawSections = payload.sections.ifEmpty { fallbackSections }
                val now = System.currentTimeMillis()
                var coreCounter = 1
                val chapterEntities = rawSections.mapIndexed { index, sec ->
                    val cleanSecTitle = sec.title.trim().ifEmpty { "Chapter ${index + 1}" }
                    val chapPages = (sec.endPage - sec.startPage + 1).coerceAtLeast(1)
                    val chapNum = if (sec.sectionType == ExtractedChapterSection.TYPE_CORE_CHAPTER) {
                        sec.chapterNumber ?: coreCounter++
                    } else {
                        null
                    }
                    Chapter(
                        bookId = newBookId,
                        chapterNumber = chapNum,
                        sectionType = sec.sectionType,
                        startPage = sec.startPage,
                        endPage = sec.endPage,
                        title = cleanSecTitle,
                        pdfFilePath = savedFile.absolutePath,
                        pdfFileName = "${book.id}.pdf",
                        pdfTotalPages = chapPages,
                        pdfLastReadPage = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                if (chapterEntities.isNotEmpty()) {
                    chapterRepository.insertChapters(chapterEntities)
                }
                chapterRepository.purgeJunkChapters()

                _exploreUiState.update {
                    it.copy(downloadProgress = it.downloadProgress - book.id)
                }

                withContext(Dispatchers.Main) {
                    onSuccess(newBookId, finalTitle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download/import book: ${e.message}", e)
                _exploreUiState.update {
                    it.copy(downloadProgress = it.downloadProgress - book.id)
                }
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to import book")
                }
            }
        }
    }

    fun isBookImported(book: DriveBookItem): Boolean {
        val imported = importedTitles.value
        val cleanTitle = normalizeTitle(book.title)
        val cleanRaw = normalizeTitle(book.rawName)
        return imported.contains(cleanTitle) || imported.contains(cleanRaw)
    }

    private fun normalizeTitle(raw: String): String {
        return raw.lowercase()
            .removeSuffix(".pdf")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

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
