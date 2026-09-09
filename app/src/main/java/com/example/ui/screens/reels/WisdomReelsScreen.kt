package com.example.ui.screens.reels

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.database.model.BookWithChapterCount
import com.example.ui.components.WordTranslationBottomSheet
import com.example.ui.util.SentenceExtractor
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.WisdomFilter
import com.example.ui.viewmodel.WisdomReelsViewModel
import kotlinx.coroutines.launch

// Strict Monochrome Palette
private val ColorJetObsidian = Color(0xFF0B0B0E)
private val ColorCardSurface = Color(0xFF141418)
private val ColorSurfaceElevated = Color(0xFF18181B)
private val ColorZincBorder = Color(0xFF27272F)
private val ColorMicroCapsuleBg = Color(0xFF101013)
private val ColorPureWhite = Color(0xFFFFFFFF)
private val ColorWhiteText = Color(0xFFF4F4F5)
private val ColorSilverUrdu = Color(0xFFE4E4E7)
private val ColorMutedZinc = Color(0xFFA1A1AA)
private val ColorSubtleZinc = Color(0xFF71717A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WisdomReelsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToChat: (chapterId: Long, messageId: Long?) -> Unit = { _, _ -> },
    initialQuoteId: Long? = null,
    viewModel: WisdomReelsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val quotes by viewModel.quotesState.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val isShuffleMode by viewModel.isShuffleMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showBookFilterSheet by remember { mutableStateOf(false) }
    var showChapterFilterSheetForBook by remember { mutableStateOf<BookWithChapterCount?>(null) }
    var isSharingQuoteId by remember { mutableStateOf<Long?>(null) }
    var isSavingQuoteId by remember { mutableStateOf<Long?>(null) }

    val initialPage = viewModel.savedPageIndex.coerceAtLeast(0)

    // When navigating to a specific quote from Dashboard, reset filter to All so it is in view
    LaunchedEffect(initialQuoteId) {
        if (initialQuoteId != null && initialQuoteId > 0) {
            viewModel.setFilter(WisdomFilter.All)
        }
    }

    // Listen for automatic Word Vault persistence confirmation feedback
    LaunchedEffect(Unit) {
        viewModel.wordAutoSavedEvent.collect { savedWord ->
            val message = "✓ \"$savedWord\" auto-saved to Word Vault"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorJetObsidian)
            .testTag("wisdom_reels_screen")
    ) {
        if (quotes.isEmpty()) {
            EmptyWisdomReelsView(
                currentFilter = currentFilter,
                onResetFilter = { viewModel.setFilter(WisdomFilter.All) },
                onNavigateBack = onNavigateBack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val targetQuoteIndex = remember(quotes, initialQuoteId) {
                if (initialQuoteId != null && initialQuoteId > 0 && quotes.isNotEmpty()) {
                    quotes.indexOfFirst { it.id == initialQuoteId }.takeIf { it >= 0 }
                } else null
            }
            val startingPage = (targetQuoteIndex ?: initialPage).coerceIn(0, (quotes.size - 1).coerceAtLeast(0))

            val pagerState = rememberPagerState(
                initialPage = startingPage,
                pageCount = { quotes.size }
            )

            // Direct snapping when target quote becomes available
            var hasScrolledToInitialQuote by rememberSaveable(initialQuoteId) { mutableStateOf(false) }
            LaunchedEffect(quotes, initialQuoteId) {
                if (!hasScrolledToInitialQuote && initialQuoteId != null && initialQuoteId > 0 && quotes.isNotEmpty()) {
                    val index = quotes.indexOfFirst { it.id == initialQuoteId }
                    if (index >= 0) {
                        if (pagerState.currentPage != index) {
                            pagerState.scrollToPage(index)
                        }
                        viewModel.onPageChanged(index)
                        hasScrolledToInitialQuote = true
                    }
                }
            }

            // Reset pager smoothly to 0 when user actively changes filter/shuffle (excluding initial load)
            var isInitialFilterRun by remember { mutableStateOf(true) }
            LaunchedEffect(currentFilter, isShuffleMode) {
                if (isInitialFilterRun) {
                    isInitialFilterRun = false
                } else {
                    if (pagerState.currentPage != 0 && quotes.isNotEmpty()) {
                        pagerState.scrollToPage(0)
                    }
                }
            }

            // Save active page index
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    viewModel.onPageChanged(page)
                }
            }

            VerticalPager(
                state = pagerState,
                key = { pageIndex -> if (pageIndex in quotes.indices) quotes[pageIndex].id else pageIndex },
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("wisdom_reels_vertical_pager")
            ) { pageIndex ->
                val quote = quotes[pageIndex]
                WisdomReelPageItem(
                    quote = quote,
                    pageIndex = pageIndex,
                    totalPages = quotes.size,
                    isSharing = isSharingQuoteId == quote.id,
                    isSaving = isSavingQuoteId == quote.id,
                    onToggleFavorite = { viewModel.toggleFavorite(quote) },
                    onNavigateToChat = {
                        onNavigateToChat(quote.chapterId, quote.messageId)
                    },
                    onTranslateWord = { selectedWord, sentence ->
                        viewModel.translateWord(selectedWord, sentence, quote)
                    },
                    onShareBitmap = { bitmap ->
                        isSharingQuoteId = quote.id
                        viewModel.shareBitmap(context, bitmap, quote) {
                            isSharingQuoteId = null
                        }
                    },
                    onSaveBitmapToGallery = { bitmap ->
                        isSavingQuoteId = quote.id
                        viewModel.saveBitmapToGallery(context, bitmap, quote) { success ->
                            isSavingQuoteId = null
                            coroutineScope.launch {
                                val message = if (success) "Quote saved to Gallery" else "Failed to save quote"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    onFallbackShare = {
                        isSharingQuoteId = quote.id
                        viewModel.shareQuote(context, quote) {
                            isSharingQuoteId = null
                        }
                    },
                    onFallbackSave = {
                        isSavingQuoteId = quote.id
                        viewModel.saveQuoteToGallery(context, quote) { success ->
                            isSavingQuoteId = null
                            coroutineScope.launch {
                                val message = if (success) "Quote saved to Gallery" else "Failed to save quote"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Header & Multi-Tier Filter Bar Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            // Top Navigation & Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x99121215),
                    border = BorderStroke(1.dp, ColorZincBorder),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reels_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = ColorPureWhite
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x99121215),
                    border = BorderStroke(1.dp, ColorZincBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = ColorPureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (quotes.isNotEmpty()) "Wisdom Reels (${quotes.size})" else "Wisdom Reels",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorWhiteText
                        )
                    }
                }

                // Shuffle button
                Surface(
                    shape = CircleShape,
                    color = if (isShuffleMode) ColorSurfaceElevated else Color(0x99121215),
                    border = BorderStroke(1.dp, if (isShuffleMode) ColorPureWhite else ColorZincBorder),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isShuffleMode) {
                                viewModel.reShuffle()
                            } else {
                                viewModel.toggleShuffle()
                            }
                        },
                        modifier = Modifier.testTag("reels_top_shuffle_button")
                    ) {
                        Icon(
                            imageVector = if (isShuffleMode) Icons.Default.Refresh else Icons.Default.Shuffle,
                            contentDescription = "Shuffle Quotes",
                            tint = ColorPureWhite,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row (Horizontal Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. [ 🎲 All / Shuffle ] Chip
                FilterPillChip(
                    label = if (isShuffleMode) "🎲 Shuffled" else "✨ All Quotes",
                    isSelected = currentFilter is WisdomFilter.All,
                    icon = if (isShuffleMode) Icons.Default.Shuffle else Icons.Default.FormatQuote,
                    onClick = {
                        if (currentFilter is WisdomFilter.All) {
                            viewModel.toggleShuffle()
                        } else {
                            viewModel.setFilter(WisdomFilter.All)
                        }
                    },
                    testTag = "filter_chip_all"
                )

                // 2. [ 📚 By Book ] Chip
                val isBookSelected = currentFilter is WisdomFilter.ByBook || currentFilter is WisdomFilter.ByChapter
                val bookLabel = when (currentFilter) {
                    is WisdomFilter.ByBook -> "📚 ${(currentFilter as WisdomFilter.ByBook).bookTitle}"
                    is WisdomFilter.ByChapter -> "📚 ${(currentFilter as WisdomFilter.ByChapter).bookTitle}"
                    else -> "📚 By Book"
                }
                FilterPillChip(
                    label = bookLabel,
                    isSelected = isBookSelected,
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = { showBookFilterSheet = true },
                    onClear = if (isBookSelected) { { viewModel.setFilter(WisdomFilter.All) } } else null,
                    testTag = "filter_chip_book"
                )

                // 3. [ 📑 By Chapter ] Chip
                if (currentFilter is WisdomFilter.ByBook) {
                    val activeBook = books.find { it.book.id == (currentFilter as WisdomFilter.ByBook).bookId }
                    FilterPillChip(
                        label = "📑 Select Chapter",
                        isSelected = false,
                        icon = Icons.Default.FilterList,
                        onClick = {
                            if (activeBook != null) {
                                showChapterFilterSheetForBook = activeBook
                            }
                        },
                        testTag = "filter_chip_select_chapter"
                    )
                } else if (currentFilter is WisdomFilter.ByChapter) {
                    val chapFilter = currentFilter as WisdomFilter.ByChapter
                    FilterPillChip(
                        label = "📑 ${chapFilter.chapterTitle}",
                        isSelected = true,
                        icon = Icons.Default.FilterList,
                        onClick = {
                            val activeBook = books.find { it.book.id == chapFilter.bookId }
                            if (activeBook != null) {
                                showChapterFilterSheetForBook = activeBook
                            }
                        },
                        onClear = {
                            viewModel.setFilter(WisdomFilter.ByBook(chapFilter.bookId, chapFilter.bookTitle))
                        },
                        testTag = "filter_chip_chapter"
                    )
                }

                // 4. [ ❤️ Favorites Only ] Chip
                FilterPillChip(
                    label = "❤️ Favorites",
                    isSelected = currentFilter is WisdomFilter.Favorites,
                    icon = Icons.Default.Favorite,
                    onClick = {
                        if (currentFilter is WisdomFilter.Favorites) {
                            viewModel.setFilter(WisdomFilter.All)
                        } else {
                            viewModel.setFilter(WisdomFilter.Favorites)
                        }
                    },
                    testTag = "filter_chip_favorites"
                )
            }
        }

        // Snackbar Host for Feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )

        // Book Selection Modal Bottom Sheet
        if (showBookFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBookFilterSheet = false },
                containerColor = ColorCardSurface,
                contentColor = ColorWhiteText,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                BookFilterBottomSheetContent(
                    books = books,
                    currentFilter = currentFilter,
                    onSelectBook = { book ->
                        viewModel.setFilter(WisdomFilter.ByBook(book.book.id, book.book.title))
                        showBookFilterSheet = false
                    },
                    onSelectChapterForBook = { book ->
                        showBookFilterSheet = false
                        showChapterFilterSheetForBook = book
                    },
                    onClear = {
                        viewModel.setFilter(WisdomFilter.All)
                        showBookFilterSheet = false
                    }
                )
            }
        }

        // Chapter Selection Modal Bottom Sheet
        showChapterFilterSheetForBook?.let { selectedBookWithCount ->
            val chaptersFlow = remember(selectedBookWithCount.book.id) {
                viewModel.getChaptersForBook(selectedBookWithCount.book.id)
            }
            val chapters by chaptersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

            ModalBottomSheet(
                onDismissRequest = { showChapterFilterSheetForBook = null },
                containerColor = ColorCardSurface,
                contentColor = ColorWhiteText,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ChapterFilterBottomSheetContent(
                    bookTitle = selectedBookWithCount.book.title,
                    chapters = chapters,
                    currentFilter = currentFilter,
                    onSelectChapter = { chapter ->
                        val chapterTitleDisplay = "Ch. ${chapter.chapterNumber}${if (chapter.title.isNotBlank()) " - ${chapter.title}" else ""}"
                        viewModel.setFilter(
                            WisdomFilter.ByChapter(
                                chapterId = chapter.id,
                                chapterTitle = chapterTitleDisplay,
                                bookId = selectedBookWithCount.book.id,
                                bookTitle = selectedBookWithCount.book.title
                            )
                        )
                        showChapterFilterSheetForBook = null
                    },
                    onClearToBook = {
                        viewModel.setFilter(
                            WisdomFilter.ByBook(
                                selectedBookWithCount.book.id,
                                selectedBookWithCount.book.title
                            )
                        )
                        showChapterFilterSheetForBook = null
                    }
                )
            }
        }

        // Word Translation Modal Bottom Sheet (Triggered by tap & hold on words in quote)
        WordTranslationBottomSheet(
            state = viewModel.translationState,
            onDismiss = viewModel::dismissTranslation,
            onSaveToVault = { translation, fallbackSentence ->
                val activeQuote = quotes.getOrNull(viewModel.savedPageIndex)
                viewModel.saveWordToVault(translation, fallbackSentence, activeQuote)
            },
            onRetry = { word, sentence, _ ->
                val activeQuote = quotes.getOrNull(viewModel.savedPageIndex)
                if (activeQuote != null) {
                    viewModel.translateWord(word, sentence, activeQuote)
                }
            },
            onNavigateToOriginPassage = { targetChapterId, targetMsgId ->
                onNavigateToChat(targetChapterId, targetMsgId)
            }
        )
    }
}

@Composable
private fun WisdomReelPageItem(
    quote: WisdomQuote,
    pageIndex: Int,
    totalPages: Int,
    isSharing: Boolean,
    isSaving: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToChat: () -> Unit,
    onTranslateWord: (selectedWord: String, sentence: String) -> Unit,
    onShareBitmap: (Bitmap) -> Unit,
    onSaveBitmapToGallery: (Bitmap) -> Unit,
    onFallbackShare: () -> Unit,
    onFallbackSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()

    val handleSaveToGallery = {
        coroutineScope.launch {
            try {
                val imageBitmap = graphicsLayer.toImageBitmap()
                val androidBitmap = imageBitmap.asAndroidBitmap()
                onSaveBitmapToGallery(androidBitmap)
            } catch (e: Exception) {
                onFallbackSave()
            }
        }
    }

    val handleShare = {
        coroutineScope.launch {
            try {
                val imageBitmap = graphicsLayer.toImageBitmap()
                val androidBitmap = imageBitmap.asAndroidBitmap()
                onShareBitmap(androidBitmap)
            } catch (e: Exception) {
                onFallbackShare()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 110.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            // Expansive Portrait Reel Card with dedicated graphicsLayer snapshot
            ImmersiveMonochromeReelCard(
                quote = quote,
                graphicsLayer = graphicsLayer,
                onTranslateWord = onTranslateWord,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("wisdom_quote_card_${quote.id}")
            )

            // Bottom Monochrome Action Controls Bar
            BottomMonochromeActionBar(
                quote = quote,
                isSharing = isSharing,
                isSaving = isSaving,
                onToggleFavorite = onToggleFavorite,
                onNavigateToChat = onNavigateToChat,
                onShareQuote = { handleShare() },
                onSaveToGallery = { handleSaveToGallery() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ImmersiveMonochromeReelCard(
    quote: WisdomQuote,
    graphicsLayer: GraphicsLayer,
    onTranslateWord: (selectedWord: String, sentence: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
        border = BorderStroke(1.dp, ColorZincBorder),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            // Main Content Layout with Top-to-Bottom Editorial Hierarchy
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Editorial Quote Block: Brand Mark -> English Quote -> Divider -> Roman Urdu Explanation
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. Brand Mark Relocation (Top-Left Micro-Pedestal)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ColorMicroCapsuleBg,
                        border = BorderStroke(1.dp, ColorZincBorder),
                        modifier = Modifier.testTag("wisdom_card_brand_mark")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = ColorSubtleZinc,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "READMATE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp
                                ),
                                color = ColorSubtleZinc
                            )
                        }
                    }

                    // Generous bottom margin (18dp) between brand mark and primary quote text
                    Spacer(modifier = Modifier.height(18.dp))

                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // English Quote (Primary Hero): High-contrast white (18.sp - 20.sp, FontWeight.SemiBold, line height 28.sp)
                            val textLen = quote.englishQuote.length
                            val englishFontSize = when {
                                textLen < 90 -> 20.sp
                                textLen < 180 -> 18.5.sp
                                else -> 17.sp
                            }
                            val englishLineHeight = (englishFontSize.value * 1.45).sp

                            var englishLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                            Text(
                                text = "\"${quote.englishQuote}\"",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = englishFontSize,
                                    lineHeight = englishLineHeight,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = ColorPureWhite,
                                onTextLayout = { englishLayoutResult = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wisdom_card_english_quote")
                                    .pointerInput(quote.englishQuote) {
                                        detectTapGestures(
                                            onLongPress = { offset ->
                                                englishLayoutResult?.let { layout ->
                                                    val charOffset = layout.getOffsetForPosition(offset)
                                                    val selectedWord = extractWordAtOffset(quote.englishQuote, charOffset)
                                                    if (selectedWord.isNotBlank()) {
                                                        val sentence = SentenceExtractor.extractSentence(quote.englishQuote, selectedWord)
                                                        onTranslateWord(selectedWord, sentence)
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )

                            // Minimalist 1px Zinc Divider & Roman Urdu Explanation
                            if (quote.romanUrduPunchline.isNotBlank()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = ColorZincBorder
                                )
                                Spacer(modifier = Modifier.height(18.dp))

                                var urduLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                Text(
                                    text = quote.romanUrduPunchline,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 15.5.sp,
                                        lineHeight = 24.sp,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = ColorSilverUrdu,
                                    onTextLayout = { urduLayoutResult = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wisdom_card_urdu_punchline")
                                        .pointerInput(quote.romanUrduPunchline) {
                                            detectTapGestures(
                                                onLongPress = { offset ->
                                                    urduLayoutResult?.let { layout ->
                                                        val charOffset = layout.getOffsetForPosition(offset)
                                                        val selectedWord = extractWordAtOffset(quote.romanUrduPunchline, charOffset)
                                                        if (selectedWord.isNotBlank()) {
                                                            val sentence = SentenceExtractor.extractSentence(quote.romanUrduPunchline, selectedWord)
                                                            onTranslateWord(selectedWord, sentence)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 2. Footer Metadata Expansion & Zero-Truncation Alignment (Dedicated Full Width Column)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .testTag("wisdom_card_footer")
                ) {
                    val authorName = quote.author?.takeIf { it.isNotBlank() } ?: "ReadMate Wisdom"
                    // Line 1 (Author): Display author name in bold crisp white (#FFFFFF, 14sp)
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = ColorPureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("wisdom_card_author")
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Line 2 (Source Reference): Display book title and chapter reference in muted zinc (#71717A, 11sp)
                    val bookMeta = if (quote.chapterNumber > 0) {
                        "${quote.bookTitle} • Ch. ${quote.chapterNumber}"
                    } else {
                        quote.bookTitle
                    }
                    Text(
                        text = bookMeta,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        color = ColorSubtleZinc,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("wisdom_card_book_source")
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomMonochromeActionBar(
    quote: WisdomQuote,
    isSharing: Boolean,
    isSaving: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToChat: () -> Unit,
    onShareQuote: () -> Unit,
    onSaveToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ColorSurfaceElevated,
        border = BorderStroke(1.dp, ColorZincBorder),
        modifier = modifier.testTag("wisdom_reel_bottom_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A. Favorite Toggle Button
            val heartScale by animateFloatAsState(
                targetValue = if (quote.isFavorite) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "heart_scale"
            )
            val heartTint by animateColorAsState(
                targetValue = if (quote.isFavorite) ColorPureWhite else ColorSubtleZinc,
                label = "heart_tint"
            )

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .scale(heartScale)
                    .testTag("action_toggle_favorite_${quote.id}")
            ) {
                Icon(
                    imageVector = if (quote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (quote.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = heartTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Divider Dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(ColorZincBorder)
            )

            // B. Read Full Chat Deep-Link Button
            IconButton(
                onClick = onNavigateToChat,
                modifier = Modifier.testTag("action_open_chat_${quote.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "Read full chapter discussion",
                    tint = ColorPureWhite,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Divider Dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(ColorZincBorder)
            )

            // C. Export to Gallery Button
            IconButton(
                onClick = onSaveToGallery,
                enabled = !isSaving,
                modifier = Modifier.testTag("action_export_gallery_${quote.id}")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ColorPureWhite
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Save quote to Gallery",
                        tint = ColorPureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Divider Dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(ColorZincBorder)
            )

            // D. Share Quote Button
            IconButton(
                onClick = onShareQuote,
                enabled = !isSharing,
                modifier = Modifier.testTag("action_share_quote_${quote.id}")
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ColorPureWhite
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share quote card as image",
                        tint = ColorPureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    testTag: String = ""
) {
    val bg = if (isSelected) ColorPureWhite else ColorSurfaceElevated
    val border = if (isSelected) ColorPureWhite else ColorZincBorder
    val textColor = if (isSelected) ColorJetObsidian else ColorWhiteText
    val iconColor = if (isSelected) ColorJetObsidian else ColorMutedZinc

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (onClear != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = iconColor,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClear)
                )
            }
        }
    }
}

@Composable
private fun BookFilterBottomSheetContent(
    books: List<BookWithChapterCount>,
    currentFilter: WisdomFilter,
    onSelectBook: (BookWithChapterCount) -> Unit,
    onSelectChapterForBook: (BookWithChapterCount) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("book_filter_bottom_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter Quotes by Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ColorPureWhite
            )

            if (currentFilter !is WisdomFilter.All) {
                TextButton(onClick = onClear) {
                    Text("Clear Filter", color = ColorPureWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (books.isEmpty()) {
            Text(
                text = "No books available in library.",
                color = ColorMutedZinc,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(books, key = { it.book.id }) { bookWithCount ->
                    val isSelected = when (currentFilter) {
                        is WisdomFilter.ByBook -> currentFilter.bookId == bookWithCount.book.id
                        is WisdomFilter.ByChapter -> currentFilter.bookId == bookWithCount.book.id
                        else -> false
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) ColorSurfaceElevated else ColorCardSurface,
                        border = BorderStroke(1.dp, if (isSelected) ColorPureWhite else ColorZincBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBook(bookWithCount) }
                            .testTag("book_filter_item_${bookWithCount.book.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookWithCount.book.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorPureWhite,
                                    fontSize = 15.sp
                                )
                                val subText = "${bookWithCount.chapterCount} Chapters" +
                                        (bookWithCount.book.author?.let { " • $it" } ?: "")
                                Text(
                                    text = subText,
                                    fontSize = 12.sp,
                                    color = ColorMutedZinc
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onSelectChapterForBook(bookWithCount) }
                                ) {
                                    Text("Chapters", fontSize = 12.sp, color = ColorPureWhite)
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = ColorPureWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterFilterBottomSheetContent(
    bookTitle: String,
    chapters: List<Chapter>,
    currentFilter: WisdomFilter,
    onSelectChapter: (Chapter) -> Unit,
    onClearToBook: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("chapter_filter_bottom_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Chapter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ColorPureWhite
                )
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorMutedZinc,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onClearToBook) {
                Text("All in Book", color = ColorPureWhite)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chapters.isEmpty()) {
            Text(
                text = "No chapters found for this book.",
                color = ColorMutedZinc,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    val isSelected = currentFilter is WisdomFilter.ByChapter && currentFilter.chapterId == chapter.id

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) ColorSurfaceElevated else ColorCardSurface,
                        border = BorderStroke(1.dp, if (isSelected) ColorPureWhite else ColorZincBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(chapter) }
                            .testTag("chapter_filter_item_${chapter.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chapter ${chapter.chapterNumber}${if (chapter.title.isNotBlank()) ": ${chapter.title}" else ""}",
                                fontWeight = FontWeight.Medium,
                                color = ColorPureWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = ColorPureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWisdomReelsView(
    currentFilter: WisdomFilter,
    onResetFilter: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ColorCardSurface,
                border = BorderStroke(1.dp, ColorZincBorder),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (currentFilter is WisdomFilter.Favorites) Icons.Default.Favorite else Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = ColorPureWhite,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val titleText = when (currentFilter) {
                is WisdomFilter.Favorites -> "No Favorite Quotes Yet"
                is WisdomFilter.ByBook -> "No Quotes in this Book"
                is WisdomFilter.ByChapter -> "No Quotes in this Chapter"
                else -> "No Wisdom Quotes Yet"
            }

            val descriptionText = when (currentFilter) {
                is WisdomFilter.Favorites -> "Tap the heart icon (🤍) on any wisdom quote in the reels stream to bookmark it here."
                is WisdomFilter.ByBook, is WisdomFilter.ByChapter -> "No universal wisdom quotes were extracted for this selection yet."
                else -> "As you read chapters and explore passages in Chapter Chat, timeless life lessons and universal principles will be extracted here automatically."
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColorPureWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = ColorMutedZinc,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (currentFilter !is WisdomFilter.All) {
                Button(
                    onClick = onResetFilter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPureWhite,
                        contentColor = ColorJetObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("reels_reset_filter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Show All Quotes",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentFilter is WisdomFilter.All) ColorPureWhite else ColorSurfaceElevated,
                    contentColor = if (currentFilter is WisdomFilter.All) ColorJetObsidian else ColorPureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                border = if (currentFilter !is WisdomFilter.All) BorderStroke(1.dp, ColorZincBorder) else null,
                modifier = Modifier.testTag("reels_empty_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Back to Library",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Extracts the single target word from text at a given character offset, stripping surrounding punctuation.
 */
private fun extractWordAtOffset(text: String, offset: Int): String {
    if (text.isEmpty() || offset !in text.indices) return ""

    var start = offset
    var end = offset

    while (start > 0 && Character.isLetterOrDigit(text[start - 1])) {
        start--
    }
    while (end < text.length && Character.isLetterOrDigit(text[end])) {
        end++
    }

    if (start >= end) return ""
    return text.substring(start, end).trim()
}

