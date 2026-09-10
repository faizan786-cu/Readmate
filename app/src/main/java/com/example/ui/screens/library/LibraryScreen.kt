package com.example.ui.screens.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.ReadMateApplication
import com.example.data.local.database.model.BookWithChapterCount
import com.example.data.remote.drive.DriveBookItem
import com.example.ui.components.ApiKeySetupDialog
import com.example.ui.components.DonutProgressChart
import com.example.ui.components.ReadMateBrandLogo
import com.example.ui.screens.book.AddBookBottomSheet
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.ExploreUiState
import com.example.ui.viewmodel.LibraryTab
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

// Ultra-modern Figma Monochrome Obsidian Color Palette
private val DarkCanvasBg = Color(0xFF0B0B0E)
private val CardSurfaceBg = Color(0xFF141418)
private val CardSurfaceSecondary = Color(0xFF18181D)
private val CardSurfaceTertiary = Color(0xFF1F1F24)
private val FineBorderColor = Color(0xFF27272F)
private val ActiveBorderColor = Color(0xFF3F3F46)
private val PrimaryWhite = Color(0xFFFFFFFF)
private val MutedWhite = Color(0xFFF4F4F5)
private val SecondaryGray = Color(0xFFA1A1AA)
private val TertiaryGray = Color(0xFF71717A)
private val SubtleGray = Color(0xFF52525B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToCreateBook: () -> Unit,
    onNavigateToBookDetail: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToWisdomReels: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val exploreUiState by viewModel.exploreUiState.collectAsStateWithLifecycle()
    val importedTitles by viewModel.importedTitles.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val app = context.applicationContext as ReadMateApplication
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddBookSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    fun handleAddBookTap() {
        if (!app.secureApiKeyStorage.hasApiKey()) {
            showApiKeyDialog = true
        } else {
            showAddBookSheet = true
        }
    }

    if (showApiKeyDialog) {
        ApiKeySetupDialog(
            isOpen = true,
            onDismiss = { showApiKeyDialog = false },
            onSuccess = {
                showApiKeyDialog = false
                showAddBookSheet = true
            },
            onKeySaved = {
                showApiKeyDialog = false
                showAddBookSheet = true
            },
            secureStorage = app.secureApiKeyStorage,
            userPreferencesRepository = app.userPreferencesRepository
        )
    }

    if (showAddBookSheet) {
        AddBookBottomSheet(
            onDismissRequest = { showAddBookSheet = false },
            onBookCreated = { newBookId ->
                showAddBookSheet = false
                onNavigateToBookDetail(newBookId)
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvasBg),
        containerColor = DarkCanvasBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CardSurfaceTertiary,
                    contentColor = PrimaryWhite,
                    actionColor = PrimaryWhite,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCanvasBg)
            ) {
                TopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    navigationIcon = {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("library_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation drawer",
                                tint = PrimaryWhite
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReadMateBrandLogo(
                                size = 28.dp,
                                showWordmark = false
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryWhite
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onNavigateToWisdomReels,
                            modifier = Modifier.testTag("library_wisdom_reels_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Wisdom Reels",
                                tint = SecondaryGray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkCanvasBg,
                        titleContentColor = PrimaryWhite,
                        navigationIconContentColor = PrimaryWhite,
                        actionIconContentColor = PrimaryWhite
                    )
                )

                // Dual-Tab Segmented Controller positioned right beneath TopAppBar
                val myBooksCount = (uiState as? LibraryUiState.Success)?.books?.size ?: 0
                LibrarySegmentedTabs(
                    selectedTab = selectedTab,
                    myBooksCount = myBooksCount,
                    exploreCount = exploreUiState.totalCount,
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
        },
        floatingActionButton = {
            // Only show Floating Action Button for local device PDF upload in "My Books" tab
            if (selectedTab == LibraryTab.MY_BOOKS) {
                Surface(
                    onClick = { handleAddBookTap() },
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryWhite,
                    contentColor = Color(0xFF0D0D11),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp, end = 4.dp)
                        .height(46.dp)
                        .testTag("add_book_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Book",
                            tint = Color(0xFF0D0D11),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add Book",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFF0D0D11)
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedTab) {
                LibraryTab.MY_BOOKS -> {
                    when (val state = uiState) {
                        is LibraryUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryWhite,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        is LibraryUiState.Success -> {
                            if (state.books.isEmpty()) {
                                EmptyLibraryView(
                                    onAddBookClick = { handleAddBookTap() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LibraryContentView(
                                    books = state.books,
                                    onBookClick = onNavigateToBookDetail,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 840.dp)
                                )
                            }
                        }
                    }
                }

                LibraryTab.EXPLORE -> {
                    ExploreContentView(
                        exploreUiState = exploreUiState,
                        importedTitles = importedTitles,
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onAddBookClick = { book ->
                            viewModel.downloadAndImportBook(
                                book = book,
                                onAlreadyExists = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Book already exists in your library",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                onSuccess = { newBookId, bookTitle ->
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "\"$bookTitle\" added to your library",
                                            actionLabel = "Go to Library",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.setTab(LibraryTab.MY_BOOKS)
                                        }
                                    }
                                },
                                onError = { errorMsg ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Failed to add book: $errorMsg",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        },
                        onRetryClick = { viewModel.loadExploreCatalog(forceRefresh = true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dual-Tab Segmented Controller:
 * "My Books" (with badge showing local count) & "Explore" (with subtle cloud/globe icon).
 * Active state: High-contrast #1F1F24 capsule with white typography.
 */
@Composable
private fun LibrarySegmentedTabs(
    selectedTab: LibraryTab,
    myBooksCount: Int,
    exploreCount: Int,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardSurfaceBg,
        border = BorderStroke(1.dp, FineBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // "My Books" Tab
            val isMyBooks = selectedTab == LibraryTab.MY_BOOKS
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected(LibraryTab.MY_BOOKS) }
                    .testTag("tab_my_books"),
                shape = RoundedCornerShape(10.dp),
                color = if (isMyBooks) CardSurfaceTertiary else Color.Transparent,
                border = if (isMyBooks) BorderStroke(1.dp, ActiveBorderColor) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "My Books",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isMyBooks) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        color = if (isMyBooks) PrimaryWhite else SecondaryGray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMyBooks) FineBorderColor else Color(0xFF18181D)
                    ) {
                        Text(
                            text = "$myBooksCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (isMyBooks) PrimaryWhite else TertiaryGray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // "Explore" Tab
            val isExplore = selectedTab == LibraryTab.EXPLORE
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected(LibraryTab.EXPLORE) }
                    .testTag("tab_explore"),
                shape = RoundedCornerShape(10.dp),
                color = if (isExplore) CardSurfaceTertiary else Color.Transparent,
                border = if (isExplore) BorderStroke(1.dp, ActiveBorderColor) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isExplore) PrimaryWhite else TertiaryGray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isExplore) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        color = if (isExplore) PrimaryWhite else SecondaryGray
                    )
                    if (exploreCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExplore) FineBorderColor else Color(0xFF18181D)
                        ) {
                            Text(
                                text = "$exploreCount",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = if (isExplore) PrimaryWhite else TertiaryGray,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Explore Catalog Ecosystem:
 * Instant search bar with deep cloud fallback indicator, catalog stats, and 2-column grid.
 */
@Composable
private fun ExploreContentView(
    exploreUiState: ExploreUiState,
    importedTitles: Set<String>,
    onSearchQueryChanged: (String) -> Unit,
    onAddBookClick: (DriveBookItem) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Instant & Cloud Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = CardSurfaceBg,
            border = BorderStroke(1.dp, FineBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TertiaryGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = exploreUiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = PrimaryWhite,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(PrimaryWhite),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .testTag("explore_search_input"),
                    decorationBox = { innerTextField ->
                        if (exploreUiState.searchQuery.isEmpty()) {
                            Text(
                                text = "Search cloud vault (e.g. Habits, Money)...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TertiaryGray,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )
                if (exploreUiState.isCloudSearching) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = PrimaryWhite,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (exploreUiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChanged("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TertiaryGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Status & Results Count Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val countLabel = if (exploreUiState.searchQuery.isBlank()) {
                "${exploreUiState.totalCount} Curated Cloud Titles"
            } else {
                "${exploreUiState.books.size} ${if (exploreUiState.books.size == 1) "result" else "results"} found"
            }
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TertiaryGray
            )

            if (exploreUiState.isCloudSearching) {
                Text(
                    text = "Searching cloud…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = SecondaryGray
                )
            }
        }

        // Grid Content with Shimmer & States
        when {
            exploreUiState.isLoading && exploreUiState.books.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryWhite,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Streaming curated repository...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TertiaryGray,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            exploreUiState.errorMessage != null && exploreUiState.books.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "Unable to connect to Drive repository",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryWhite
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = exploreUiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TertiaryGray
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            onClick = onRetryClick,
                            shape = RoundedCornerShape(10.dp),
                            color = CardSurfaceTertiary,
                            border = BorderStroke(1.dp, FineBorderColor)
                        ) {
                            Text(
                                text = "Retry Connection",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryWhite
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            exploreUiState.books.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No books found",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryWhite
                            )
                        )
                        Text(
                            text = "Try different keywords or clear your search query.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TertiaryGray
                            )
                        )
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = exploreUiState.books,
                        key = { it.id }
                    ) { book ->
                        val normTitle = book.title.lowercase()
                            .removeSuffix(".pdf")
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .replace("\\s+".toRegex(), " ")
                            .trim()
                        val normRaw = book.rawName.lowercase()
                            .removeSuffix(".pdf")
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .replace("\\s+".toRegex(), " ")
                            .trim()
                        val isImported = importedTitles.contains(normTitle) || importedTitles.contains(normRaw)
                        val progress = exploreUiState.downloadProgress[book.id]

                        ExploreBookCard(
                            book = book,
                            isImported = isImported,
                            downloadProgress = progress,
                            onAddClick = { onAddBookClick(book) },
                            modifier = Modifier.testTag("explore_card_${book.id}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Explore Grid Card:
 * - Book Cover image (aspectRatio(2f / 3f), rounded 12.dp, border 1.dp solid #27272F)
 * - Obsidian shimmer placeholder (#141418 <-> #1F1F24) while loading
 * - Fallback dark elegant spine placeholder with initial letters
 * - Title (max 2 lines, FontWeight.SemiBold, 14sp, #FFFFFF)
 * - File size pill (e.g., "12.4 MB" in #71717A)
 * - State-Aware CTA:
 *   - "In Library" disabled pill if imported
 *   - Streamed percentage spinner if actively downloading
 *   - High-tactile "+ Add Book" button (#FFFFFF background, #0B0B0E text) if not imported
 */
@Composable
private fun ExploreBookCard(
    book: DriveBookItem,
    isImported: Boolean,
    downloadProgress: Float?,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(book.highResCoverUrl) {
        ImageRequest.Builder(context)
            .data(book.highResCoverUrl)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) ReadMate/1.0")
            .build()
    }
    val shimmerBrush = rememberObsidianShimmerBrush()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceBg),
        border = BorderStroke(1.dp, FineBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // 1. High-Resolution Cover Thumbnail (aspect ratio 2:3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, FineBorderColor, RoundedCornerShape(12.dp))
                    .background(CardSurfaceBg)
            ) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = "${book.title} cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(shimmerBrush)
                        )
                    },
                    error = {
                        BookSpinePlaceholder(
                            title = book.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                // File size pill overlay at bottom right
                if (book.formattedSize.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkCanvasBg.copy(alpha = 0.85f),
                        border = BorderStroke(0.8.dp, FineBorderColor),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = book.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TertiaryGray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Book Title: max 2 lines, FontWeight.SemiBold, 14sp, #FFFFFF
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = PrimaryWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Author if present
            if (!book.author.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = TertiaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. State-Aware CTA
            when {
                isImported -> {
                    // Disabled pill "In Library" with subtle checkmark
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardSurfaceTertiary,
                        border = BorderStroke(1.dp, FineBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("cta_in_library_${book.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TertiaryGray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "In Library",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                ),
                                color = TertiaryGray
                            )
                        }
                    }
                }

                downloadProgress != null -> {
                    // Inline download progress percentage and spinner
                    val percent = (downloadProgress * 100).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardSurfaceTertiary,
                        border = BorderStroke(1.dp, ActiveBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("cta_downloading_${book.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                strokeWidth = 2.5.dp,
                                color = PrimaryWhite,
                                trackColor = FineBorderColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                ),
                                color = PrimaryWhite
                            )
                        }
                    }
                }

                else -> {
                    // High-tactile "+ Add Book" button (White background #FFFFFF, black text #0B0B0E, FontWeight.Bold, height 36.dp)
                    Surface(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(10.dp),
                        color = PrimaryWhite,
                        contentColor = Color(0xFF0B0B0E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("cta_add_book_${book.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF0B0B0E),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Add Book",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.1.sp
                                ),
                                color = Color(0xFF0B0B0E)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fallback dark elegant book spine placeholder with title initials.
 */
@Composable
private fun BookSpinePlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(title) {
        val words = title.trim().split(" ", "_", "-").filter { it.isNotBlank() }
        when {
            words.size >= 2 -> "${words[0].firstOrNull()?.uppercaseChar() ?: ""}${words[1].firstOrNull()?.uppercaseChar() ?: ""}"
            words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase()
            words.size == 1 -> words[0].take(1).uppercase()
            else -> "BK"
        }
    }

    Box(
        modifier = modifier
            .background(CardSurfaceBg),
        contentAlignment = Alignment.Center
    ) {
        // Spine left accent line
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(4.dp)
                .background(FineBorderColor)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = TertiaryGray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontSize = 15.sp
                ),
                color = MutedWhite,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Obsidian shimmer placeholder brush (#141418 <-> #1F1F24)
 */
@Composable
private fun rememberObsidianShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "obsidian_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF141418),
            Color(0xFF1F1F24),
            Color(0xFF27272F),
            Color(0xFF1F1F24),
            Color(0xFF141418)
        ),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Single-Column, Full-Width List Layout for Book Cards in "My Books".
 */
@Composable
private fun LibraryContentView(
    books: List<BookWithChapterCount>,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "My Books",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp
                    ),
                    color = PrimaryWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${books.size} ${if (books.size == 1) "book" else "books"} in your personal collection",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = SecondaryGray
                )
            }
        }

        items(books, key = { it.book.id }) { item ->
            BookCard(
                bookWithCount = item,
                onClick = { onBookClick(item.book.id) },
                modifier = Modifier.testTag("book_card_${item.book.id}")
            )
        }
    }
}

/**
 * Modern Full-Width Book Card for "My Books":
 * Left: Sleek Book thumbnail with proportional 2:3 aspect ratio
 * Center: Title, author, and chapter count / progress badges
 * Right: Circular progress indicator + subtle chevron navigation arrow
 */
@Composable
private fun BookCard(
    bookWithCount: BookWithChapterCount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = bookWithCount.book
    val chapterCount = bookWithCount.chapterCount
    val hasPdf = book.pdfTotalPages > 0 || !book.pdfFilePath.isNullOrBlank()
    val totalPages = book.pdfTotalPages
    val highestReadPage = book.pdfLastReadPage
    val progressFraction = if (totalPages > 0) (highestReadPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceBg),
        border = BorderStroke(1.dp, FineBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Book Cover Thumbnail
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardSurfaceTertiary,
                border = BorderStroke(1.dp, FineBorderColor),
                modifier = Modifier
                    .width(50.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (!book.coverImageUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    val imageRequest = remember(book.coverImageUrl) {
                        ImageRequest.Builder(context)
                            .data(book.coverImageUrl)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) ReadMate/1.0")
                            .allowHardware(true)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = "${book.title} cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CardSurfaceTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = SecondaryGray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        error = {
                            BookSpinePlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                } else {
                    BookSpinePlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Fluid column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = PrimaryWhite,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!book.author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = SecondaryGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = SecondaryGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CardSurfaceTertiary,
                        border = BorderStroke(0.8.dp, FineBorderColor)
                    ) {
                        Text(
                            text = if (chapterCount == 1) "1 Chapter" else "$chapterCount Chapters",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MutedWhite,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (hasPdf && totalPages > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CardSurfaceSecondary,
                            border = BorderStroke(0.8.dp, FineBorderColor)
                        ) {
                            Text(
                                text = "$highestReadPage / $totalPages pages",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = SecondaryGray,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Right: Donut chart and chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 10.dp)
            ) {
                if (hasPdf && totalPages > 0) {
                    DonutProgressChart(
                        progress = progressFraction,
                        size = 36.dp,
                        strokeWidth = 3.5.dp,
                        progressColors = listOf(
                            PrimaryWhite,
                            Color(0xFFE4E4E7),
                            Color(0xFFD4D4D8)
                        ),
                        trackColor = Color(0xFF27272A),
                        textColor = PrimaryWhite,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        ),
                        testTag = "book_donut_chart_${book.id}"
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View Book",
                    tint = SecondaryGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onAddBookClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardSurfaceTertiary,
                border = BorderStroke(1.dp, FineBorderColor),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = PrimaryWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Start your reading journey",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                color = PrimaryWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create your first book or explore curated titles from our cloud repository.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                color = SecondaryGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                onClick = onAddBookClick,
                shape = RoundedCornerShape(14.dp),
                color = PrimaryWhite,
                contentColor = Color(0xFF0D0D11),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .height(46.dp)
                    .testTag("empty_state_add_book_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF0D0D11),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add Book",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color(0xFF0D0D11)
                    )
                }
            }
        }
    }
}
