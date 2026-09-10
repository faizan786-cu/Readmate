package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.BookDownloadManager
import com.example.data.remote.drive.DriveBookItem
import com.example.data.remote.drive.DriveExplorerRepository
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import com.example.data.worker.BookDownloadWorker
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

    // Active in-flight downloads for My Books and Explore
    val activeDownloads = BookDownloadManager.activeDownloads

    // 2. Explore Tab State & Cache
    private var cachedExploreBooks: List<DriveBookItem> = emptyList()
    private val _exploreUiState = MutableStateFlow(ExploreUiState(isLoading = true))
    val exploreUiState: StateFlow<ExploreUiState> = _exploreUiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Pre-fetch Explore catalog in background so switching is instantaneous
        loadExploreCatalog()

        // Sync download progress from BookDownloadManager to Explore cards
        viewModelScope.launch {
            BookDownloadManager.driveDownloads.collect { progressMap ->
                _exploreUiState.update { it.copy(downloadProgress = progressMap) }
            }
        }
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
     * Resilient Background Ingestion Architecture (WorkManager-backed):
     * Task 1: Duplicate Verification
     * Task 2: Immediate Entity Placeholder in Room
     * Task 3: Ingestion Hand-off to BookDownloadWorker
     * Task 4: UI callback for instant tab redirection
     */
    fun startBackgroundDownload(
        book: DriveBookItem,
        onAlreadyExists: () -> Unit,
        onStarted: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Task 1: Duplicate Verification against Room DB
                val cleanTitle = normalizeTitle(book.title)
                val exists = bookRepository.isBookExists(cleanTitle) ||
                        bookRepository.isBookExists(book.title) ||
                        bookRepository.isBookExists(normalizeTitle(book.rawName))

                if (exists) {
                    withContext(Dispatchers.Main) {
                        onAlreadyExists()
                    }
                    return@launch
                }

                // Task 2: Immediate Entity Placeholder in Room DB with pdfTotalPages = 0
                val app = getApplication<Application>()
                val tempBookId = bookRepository.createBook(
                    title = book.title,
                    author = book.author,
                    description = null,
                    coverImageUrl = book.highResCoverUrl,
                    pdfFilePath = null,
                    pdfFileName = "${book.id}.pdf",
                    pdfTotalPages = 0,
                    pdfLastReadPage = 0
                )

                // Task 3: Initialize status in persistent BookDownloadManager
                BookDownloadManager.updateProgress(
                    bookId = tempBookId,
                    driveFileId = book.id,
                    progress = 0.05f,
                    statusText = "Starting download..."
                )

                // Task 4: Enqueue persistent background WorkManager worker (decoupled from viewModelScope)
                BookDownloadWorker.enqueue(
                    context = app,
                    bookId = tempBookId,
                    driveFileId = book.id,
                    bookTitle = book.title,
                    author = book.author,
                    coverUrl = book.highResCoverUrl,
                    expectedSizeBytes = book.sizeBytes
                )

                withContext(Dispatchers.Main) {
                    onStarted(tempBookId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background download: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to initiate download")
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
