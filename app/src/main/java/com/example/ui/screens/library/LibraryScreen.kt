package com.example.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.request.ImageRequest
import com.example.R
import com.example.ReadMateApplication
import com.example.data.local.database.model.BookWithChapterCount
import com.example.ui.components.ApiKeySetupDialog
import com.example.ui.components.DonutProgressChart
import com.example.ui.components.ReadMateBrandLogo
import com.example.ui.screens.book.AddBookBottomSheet
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.LibraryViewModel

// Ultra-modern Figma Monochrome Color Palette
private val DarkCanvasBg = Color(0xFF0D0D11)
private val CardSurfaceBg = Color(0xFF141418)
private val CardSurfaceSecondary = Color(0xFF18181D)
private val CardSurfaceTertiary = Color(0xFF1E1E24)
private val FineBorderColor = Color(0xFF27272A)
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
    val context = LocalContext.current
    val app = context.applicationContext as ReadMateApplication

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
        topBar = {
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
        },
        floatingActionButton = {
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
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
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
    }
}

/**
 * Single-Column, Full-Width List Layout for Book Cards with balanced hierarchy and spacing.
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
 * Modern Full-Width Book Card:
 * Left: Sleek Book thumbnail with fixed width and proportional 2:3 aspect ratio
 * Center: Fluid column with unwrapped/wrapped title, author, and clean horizontal metadata chips
 * Right: Compact circular progress indicator + subtle chevron navigation arrow
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
    val progressPercent = (progressFraction * 100).toInt().coerceIn(0, 100)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardSurfaceBg
        ),
        border = BorderStroke(1.dp, FineBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Dynamic Book Cover Thumbnail (proportional 2:3 aspect ratio) with Graceful Fallback
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
                            .crossfade(300)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:109.0) Gecko/114.0 Firefox/114.0")
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CardSurfaceTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = PrimaryWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CardSurfaceTertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = PrimaryWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Fluid column taking all remaining available width
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Book Title: Allow wrapping up to 3 lines with comfortable line height and scaled font size
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

                // Author Name: Subtle muted text with single-line clamping and author glyph
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

                // Metadata Row: Clean horizontal badges with comfortable spacing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Capsule chip for chapter count
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

                    // Clean horizontal inline badge for page progress
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

            // Right: Compact, vertically centered progress indicator alongside subtle chevron arrow
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
                text = "Create your first book and start building your personal reading library.",
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
