package com.example.ui.screens.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ReadMateApplication
import com.example.data.local.database.entity.WisdomQuote
import com.example.ui.components.ApiKeySetupDialog
import com.example.ui.components.DualApiKeySetupDialog
import com.example.ui.components.KineticNotificationBell
import com.example.ui.components.NotificationCenterDialog
import com.example.ui.components.ReaderLockoutSheet
import com.example.ui.components.ReadMateBrandLogo
import com.example.ui.components.StreakConsistencySheet
import com.example.ui.navigation.Screen
import com.example.ui.screens.book.AddBookBottomSheet
import com.example.ui.util.StreakInfo
import com.example.ui.viewmodel.ActiveDailyChallengeModal
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.RecentActiveBookUi
import java.text.NumberFormat

// ==========================================
// Strict Cinematic Monochrome Palette
// ==========================================
private val DeepObsidian = Color(0xFF0B0B0E)
private val ZincSurface = Color(0xFF141418)
private val SubduedZinc = Color(0xFF101013)
private val SlateBorder = Color(0xFF27272F)
private val BorderCover = Color(0xFF27272F)
private val DividerDark = Color(0xFF27272F)
private val DonutTrackBackground = Color(0xFF27272F)
private val ModalSheetBackground = Color(0xFF0D0D11)

// Typography Palette
private val CrispWhite = Color(0xFFFFFFFF)
private val TextSilver = Color(0xFFA1A1AA)
private val ZincMuted = Color(0xFF71717A)
private val TextMutedQuaternary = Color(0xFF52525B)

// Aliases for unified palette compatibility
private val CanvasObsidian = DeepObsidian
private val TextPureWhite = CrispWhite
private val LayeredCardSurface = ZincSurface
private val SubContainerDark = SubduedZinc
private val BorderDark = SlateBorder
private val TextSilverMuted = TextSilver
private val TextMutedTertiary = ZincMuted
private val TextOffWhite = CrispWhite
private val TextGrayAuthor = TextSilver
private val TextChapterPill = TextSilver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToWordVault: () -> Unit,
    onNavigateToWisdomReels: (quoteId: Long?) -> Unit = {},
    onNavigateToDailyRecall: () -> Unit = {},
    onNavigateToWordQuiz: () -> Unit = {},
    onNavigateToChapterMasteryQuiz: (Long) -> Unit = {},
    onNavigateToProgress: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCreateBook: () -> Unit,
    onNavigateToChapterChat: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as ReadMateApplication

    val notificationManager = remember(app) { app.inAppNotificationManager }
    val activeNotifications by notificationManager.activeNotifications.collectAsStateWithLifecycle()
    val unreadNotificationsCount by notificationManager.unreadCount.collectAsStateWithLifecycle()
    var showNotificationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        notificationManager.evaluateNotifications()
    }

    LaunchedEffect(showNotificationDialog) {
        if (showNotificationDialog) {
            notificationManager.evaluateNotifications()
        }
    }

    var showMissionsSheet by remember { mutableStateOf(false) }
    var showLockoutSheet by remember { mutableStateOf(false) }
    var showDualApiKeyDialog by remember { mutableStateOf(false) }
    var showAddBookSheet by remember { mutableStateOf(false) }
    var showStreakCenterSheet by remember { mutableStateOf(false) }

    // Unified In-App Notification Center Dialog
    if (showNotificationDialog) {
        NotificationCenterDialog(
            isOpen = true,
            notifications = activeNotifications,
            onDismiss = {
                showNotificationDialog = false
                notificationManager.markAllAsRead()
            },
            onNotificationAction = { notification ->
                showNotificationDialog = false
                notificationManager.markAllAsRead()
                when (notification.targetRoute) {
                    Screen.Quiz.route, Screen.DailyRecall.route -> onNavigateToDailyRecall()
                    Screen.WordVault.route, Screen.WordVaultQuiz.route -> onNavigateToWordVault()
                    Screen.Progress.route -> onNavigateToProgress()
                    Screen.Library.route -> onNavigateToLibrary()
                    else -> {
                        val route = notification.targetRoute
                        if (route != null) {
                            if (route.startsWith("book/")) {
                                val bookId = route.removePrefix("book/").substringBefore("/").toLongOrNull()
                                if (bookId != null) {
                                    onNavigateToChapterChat(bookId)
                                } else {
                                    onNavigateToLibrary()
                                }
                            } else if (route.contains("wordvault")) {
                                onNavigateToWordVault()
                            } else {
                                onNavigateToDailyRecall()
                            }
                        }
                    }
                }
            },
            onClearNotification = { id ->
                notificationManager.clearNotification(id)
            }
        )
    }

    fun handleCreateBookTap() {
        if (!app.secureApiKeyStorage.hasValidCredentials() || app.secureApiKeyStorage.getPrimaryApiKey().isNullOrBlank()) {
            showDualApiKeyDialog = true
        } else {
            showAddBookSheet = true
        }
    }

    // Dual API Key Setup Dialog with Inline Plug Verification
    if (showDualApiKeyDialog) {
        DualApiKeySetupDialog(
            isOpen = true,
            onDismiss = { showDualApiKeyDialog = false },
            onSuccess = {
                showDualApiKeyDialog = false
                showAddBookSheet = true
            },
            secureStorage = app.secureApiKeyStorage,
            userPreferencesRepository = app.userPreferencesRepository
        )
    }

    // Direct 1-tap PDF Ingestion Bottom Sheet
    if (showAddBookSheet) {
        AddBookBottomSheet(
            onDismissRequest = { showAddBookSheet = false },
            onBookCreated = { newBookId ->
                showAddBookSheet = false
                onNavigateToChapterChat(newBookId)
            }
        )
    }

    // Daily Missions Bottom Sheet (User-triggered via bell icon)
    if (showMissionsSheet && uiState is DashboardUiState.Success) {
        val successState = uiState as DashboardUiState.Success
        TodayMissionsBottomSheet(
            todaySnippetCompleted = successState.todaySnippetCompleted,
            todayWordCompleted = successState.todayWordCompleted,
            streakCount = successState.streakInfo.activeStreakCount,
            onDismiss = { showMissionsSheet = false },
            onNavigateToDailyRecall = {
                showMissionsSheet = false
                onNavigateToDailyRecall()
            },
            onNavigateToWordQuiz = {
                showMissionsSheet = false
                onNavigateToWordQuiz()
            }
        )
    }

    // Reader Lockout Sheet (Shown when attempting to read during Frozen Debt)
    if (showLockoutSheet) {
        ReaderLockoutSheet(
            onStartDailyRecall = {
                showLockoutSheet = false
                onNavigateToDailyRecall()
            },
            onDismiss = { showLockoutSheet = false }
        )
    }

    // Streak & Consistency Center Bottom Sheet (Triggered by tapping the top bar Streak Chip)
    if (showStreakCenterSheet && uiState is DashboardUiState.Success) {
        val successState = uiState as DashboardUiState.Success
        StreakConsistencySheet(
            streakInfo = successState.streakInfo,
            readingTimestamps = successState.readingTimestamps,
            dailyRetentionState = successState.dailyRetentionState,
            allRetentionStates = successState.allRetentionStates,
            onDismiss = { showStreakCenterSheet = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasObsidian)
            .testTag("dashboard_screen")
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TextPureWhite,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
            is DashboardUiState.Success -> {
                val hasPendingMissions = !state.todaySnippetCompleted || !state.todayWordCompleted
                val isReaderLocked = state.isCognitiveDebtActive

                // Responsive zero-scroll single-viewport executive layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Bar (Section 1)
                    DashboardTopBar(
                        readerRank = state.readerRank,
                        totalXp = state.totalXp,
                        streakInfo = state.streakInfo,
                        unreadNotificationsCount = unreadNotificationsCount,
                        onOpenDrawer = onOpenDrawer,
                        onOpenNotifications = {
                            showNotificationDialog = true
                            notificationManager.markAllAsRead()
                        },
                        onViewProgress = onNavigateToProgress,
                        onOpenStreakCenter = { showStreakCenterSheet = true }
                    )

                    // Cold "Frozen Debt" Hero Banner (Section 1.5 - only shown when strict debt conditions are active)
                    if (state.isCognitiveDebtActive) {
                        FrozenDebtHeroBanner(
                            onResolveNow = onNavigateToDailyRecall
                        )
                    }

                    // Books Section
                    val hasBooks = state.totalBooksCount > 0 || state.recentActiveBook != null
                    if (hasBooks) {
                        // Header row with "My Library" and "Add Book"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onNavigateToLibrary)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "My Library",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    color = CrispWhite
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SubduedZinc,
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Text(
                                        text = "${state.totalBooksCount}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = TextSilver,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = { handleCreateBookTap() },
                                shape = RoundedCornerShape(8.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.testTag("dashboard_add_book_header_action")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Book",
                                        tint = CrispWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Add Book",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = CrispWhite
                                    )
                                }
                            }
                        }

                        // Hero Reading Card (Section 2)
                        HeroCurrentlyReadingCard(
                            recentBook = state.recentActiveBook,
                            onNavigateToLibrary = onNavigateToLibrary,
                            onNavigateToCreateBook = { handleCreateBookTap() },
                            onNavigateToChapterChat = { chapterId ->
                                if (isReaderLocked) {
                                    showLockoutSheet = true
                                } else {
                                    onNavigateToChapterChat(chapterId)
                                }
                            }
                        )
                    } else {
                        // Empty-State Card with Prominent Add Book CTA
                        DashboardEmptyBooksCard(
                            onAddBook = { handleCreateBookTap() }
                        )
                    }

                    // Quick Hub 2x2 Matrix (Section 3) - Dynamic Retention Rate metric
                    TactileQuickHubMatrix(
                        totalWords = state.totalWordsCount,
                        pendingMistakes = state.pendingMistakesCount,
                        retentionRateDisplay = state.retentionRateDisplay,
                        totalWisdom = state.totalWisdomCount,
                        onNavigateToWordVault = onNavigateToWordVault,
                        onNavigateToDailyRecall = onNavigateToDailyRecall,
                        onNavigateToProgress = onNavigateToProgress,
                        onNavigateToWisdomReels = { onNavigateToWisdomReels(null) }
                    )

                    // Expanded Wisdom Spotlight Card (Section 4 - auto-flexible pod consuming remaining dead space)
                    if (state.recentWisdomQuotes.isNotEmpty()) {
                        val activeInsightQuote = state.recentWisdomQuotes.first()
                        DynamicWisdomSpotlightCard(
                            quote = activeInsightQuote,
                            onNavigateToWisdomReels = { onNavigateToWisdomReels(activeInsightQuote.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Top Header:
 * - Left: Drawer Menu Icon + "ReadMate" + Inline/Sub Rank & XP Readout
 * - Right: Streak Pill + Kinetic Pendulum Notification Bell
 */
@Composable
private fun DashboardTopBar(
    readerRank: String,
    totalXp: Int,
    streakInfo: StreakInfo,
    unreadNotificationsCount: Int,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onViewProgress: () -> Unit,
    onOpenStreakCenter: () -> Unit = onViewProgress
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Navigation Menu + Compact Brand Emblem + Title & Rank/XP Readout
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("dashboard_open_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation menu",
                    tint = CrispWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            ReadMateBrandLogo(
                size = 28.dp,
                showWordmark = false,
                modifier = Modifier.clickable(onClick = onViewProgress)
            )

            Column(
                modifier = Modifier.clickable(onClick = onViewProgress),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ReadMate",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = CrispWhite
                )
                Text(
                    text = "$readerRank • ${NumberFormat.getIntegerInstance().format(totalXp)} XP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = ZincMuted,
                    maxLines = 1
                )
            }
        }

        // Right: Streak Pill + Kinetic Notification Bell
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Streak Pill with tactile press ripple feedback (Directive 1)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SubduedZinc,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.25f)),
                        onClick = onOpenStreakCenter
                    )
                    .testTag("streak_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥 ${streakInfo.activeStreakCount} ${if (streakInfo.activeStreakCount == 1) "DAY" else "DAYS"}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = CrispWhite
                    )
                }
            }

            // Kinetic Notification Bell
            KineticNotificationBell(
                unreadCount = unreadNotificationsCount,
                onOpenCenter = onOpenNotifications
            )
        }
    }
}

/**
 * Section 2: RE-ARCHITECTED "CURRENTLY READING" HERO CARD (VISUAL WEIGHT)
 * - Surface: #141418, corner radius 22.dp, border 1.dp #27272F. Internal padding: 18.dp.
 * - Top Sub-Header: Small horizontal row with MenuBook icon (14.dp, #71717A) + CONTINUE EXPLORATION label (11.sp, #71717A, bold).
 * - Main Showcase Row:
 *   - Book Cover: Width 68.dp, height 96.dp, rounded 10.dp, sharp 1.dp border #2E2E38.
 *   - Text Column: Title (19.sp, #FFFFFF, FontWeight.Bold, maxLines = 2), Author (#8E8E93, 13.sp), Chapter Status Pill (Ch 1 • 12% Mastered).
 *   - Donut Progress Ring: Size 60.dp, track #222228, active sweep #FFFFFF (6.dp stroke), center text 12% (13.sp bold).
 * - Integrated Action Bar: Full-width button directly below: Height 48.dp, solid #FFFFFF fill, corner radius 12.dp.
 */
@Composable
private fun HeroCurrentlyReadingCard(
    recentBook: RecentActiveBookUi?,
    onNavigateToLibrary: () -> Unit,
    onNavigateToCreateBook: () -> Unit,
    onNavigateToChapterChat: (Long) -> Unit
) {
    if (recentBook != null) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ZincSurface),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("resume_reading_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Top Sub-Header: Vector MenuBook icon + CONTINUE EXPLORATION tracking label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = ZincMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "CONTINUE EXPLORATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp
                        ),
                        color = ZincMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Showcase Row: Top-aligned fluid layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Cover Thumbnail (Width 48.dp, height 66.dp, rounded 8.dp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SubduedZinc,
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .size(width = 48.dp, height = 66.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (!recentBook.coverImageUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(recentBook.coverImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Cover for ${recentBook.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(SubduedZinc),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = ZincMuted,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                error = { HeroCoverFallback() }
                            )
                        } else {
                            HeroCoverFallback()
                        }
                    }

                    // Flexible Title & Author Column: fills all remaining horizontal space without hardcoded limits
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 6.dp)
                    ) {
                        Text(
                            text = recentBook.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = CrispWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!recentBook.author.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = recentBook.author,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextSilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Chapter Status Pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SubduedZinc,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = recentBook.chapterPillLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = TextSilver,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Clean Compact Donut Progress Ring at Top-Right (Size 42.dp)
                    DonutProgressRing(
                        progressPercent = if (recentBook.hasStartedReading) recentBook.progressPercent else 0,
                        size = 42.dp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Integrated Action Bar: Sleek compact button with height 38.dp
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "hero_cta_scale"
                )

                Button(
                    onClick = {
                        if (recentBook.chapterId > 0) {
                            onNavigateToChapterChat(recentBook.chapterId)
                        } else {
                            onNavigateToLibrary()
                        }
                    },
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrispWhite,
                        contentColor = DeepObsidian
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .testTag("resume_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = DeepObsidian,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (recentBook.hasStartedReading) "Resume Chapter ${recentBook.chapterNumber}" else "Start Chapter ${recentBook.chapterNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = DeepObsidian
                        )
                    }
                }
            }
        }
    } else {
        // Empty State Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ZincSurface),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("resume_reading_empty_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Books Added",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = CrispWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Import an EPUB or PDF document to begin your reading session.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = TextSilver,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNavigateToCreateBook,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrispWhite,
                        contentColor = DeepObsidian
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("dashboard_add_book_empty_cta")
                ) {
                    Text(
                        text = "Add Book →",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = DeepObsidian
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Empty-State Card when no books exist in user's library.
 */
@Composable
private fun DashboardEmptyBooksCard(
    onAddBook: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZincSurface),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_empty_books_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Books Added",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = CrispWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Import an EPUB or PDF document to begin your reading session.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = TextSilver,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddBook,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrispWhite,
                    contentColor = DeepObsidian
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("dashboard_empty_add_book_cta")
            ) {
                Text(
                    text = "Add Book →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = DeepObsidian
                )
            }
        }
    }
}

/**
 * High-density monochrome alert banner rendered above the Hero card when in Frozen Debt.
 */
@Composable
fun FrozenDebtHeroBanner(
    onResolveNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ZincSurface,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("frozen_debt_hero_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DeepObsidian,
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Frozen Debt",
                            tint = CrispWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Cognitive Debt Active • Reading Locked",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Retention dropped by 6%. Complete daily drill to restore streak.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = TextSilver,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onResolveNow,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrispWhite,
                    contentColor = DeepObsidian
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("resolve_debt_button")
            ) {
                Text(
                    text = "Resolve Now →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Monochrome Donut Analytics Ring (Right-Aligned in Hero Card)
 * - Compact size: 46.dp circle canvas, stroke width 4.dp
 * - Inactive track: #27272F
 * - Active sweep: Solid #FFFFFF
 * - Center text: 11.5.sp, FontWeight.Bold, #FFFFFF
 */
@Composable
private fun DonutProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val progressFloat = (progressPercent.coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFloat,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "donut_progress_anim"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 4.dp.toPx()
            val arcSize = this.size.width - strokeWidthPx
            val topLeftOffset = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)

            // Background Track Arc (360 degrees)
            drawArc(
                color = DonutTrackBackground,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = Size(arcSize, arcSize),
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )

            // Active Progress Arc (clockwise from top: -90 degrees)
            if (animatedProgress > 0f) {
                drawArc(
                    color = CrispWhite,
                    startAngle = -90f,
                    sweepAngle = (animatedProgress * 360f).coerceIn(0.1f, 360f),
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        Text(
            text = "$progressPercent%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            ),
            color = CrispWhite,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Section 3: QUICK HUB: TACTILE 2x2 BALANCED MATRIX
 * - Row 1 (spacedBy 8.dp):
 *   - Tile 1 (Word Vault): Surface #141418, border 1.dp #27272F, height 58.dp, rounded 12.dp. Bookmark icon, Big number ($totalWords) + "Words Saved"
 *   - Tile 2 (Mistake Bank): Target icon, Big number ($pendingMistakes) + "To Review"
 * - Row 2 (spacedBy 8.dp):
 *   - Tile 3 (Mastery): Crown/Trophy icon, Big number ($masteredChapters) + "Mastered" (concise, no clipping)
 *   - Tile 4 (Rules & Models): Lightbulb icon, Big number ($totalWisdom) + "Mental Models"
 */
@Composable
private fun TactileQuickHubMatrix(
    totalWords: Int,
    pendingMistakes: Int,
    retentionRateDisplay: String,
    totalWisdom: Int,
    onNavigateToWordVault: () -> Unit,
    onNavigateToDailyRecall: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToWisdomReels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metrics_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Word Vault & Mistake Bank
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileMatrixTile(
                icon = Icons.Default.Bookmark,
                count = "$totalWords",
                subtitle = "WORDS SAVED",
                onClick = onNavigateToWordVault,
                modifier = Modifier
                    .weight(1f)
                    .testTag("word_vault_tile")
            )

            TactileMatrixTile(
                icon = Icons.Default.TrackChanges,
                count = "$pendingMistakes",
                subtitle = "TO REVIEW",
                onClick = onNavigateToDailyRecall,
                modifier = Modifier
                    .weight(1f)
                    .testTag("mistake_bank_tile")
            )
        }

        // Row 2: Dynamic Retention Rate & Mental Models
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileMatrixTile(
                icon = Icons.Outlined.GpsFixed,
                count = retentionRateDisplay,
                subtitle = "RETENTION RATE",
                onClick = onNavigateToProgress,
                modifier = Modifier
                    .weight(1f)
                    .testTag("retention_rate_tile")
            )

            TactileMatrixTile(
                icon = Icons.Default.Lightbulb,
                count = "$totalWisdom",
                subtitle = "MENTAL MODELS",
                onClick = onNavigateToWisdomReels,
                modifier = Modifier
                    .weight(1f)
                    .testTag("wisdom_reels_tile")
            )
        }
    }
}

@Composable
private fun TactileMatrixTile(
    icon: ImageVector,
    count: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "matrix_tile_scale"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ZincSurface,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SubduedZinc,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.size(30.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = CrispWhite,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    ),
                    color = ZincMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Section 4: DYNAMIC WISDOM SPOTLIGHT CARD
 * - Surface: #141418, border 1.dp #27272F, rounded 18.dp, internal padding 16.dp
 * - Header: Pill badge "✦ 15-MIN INSIGHT" on the left; "View All →" on the right.
 * - Body Typography:
 *   - English Quote: 15.sp, FontWeight.SemiBold, FontStyle.Italic, line-height 22.sp, maxLines = 3.
 *   - Hairline divider: 1.dp #27272F with 8.dp vertical padding.
 *   - Roman Urdu Takeaway: 13.sp, italicized #A1A1AA, line-height 18.sp, maxLines = 2 (strict clamp).
 * - Streamlined minimal preview without reference metadata for clean, high-density focus.
 * - Direct interactive tap with tactile spring press feedback.
 */
@Composable
private fun DynamicWisdomSpotlightCard(
    quote: WisdomQuote,
    onNavigateToWisdomReels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WisdomCardScale"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ZincSurface),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onNavigateToWisdomReels
            )
            .testTag("wisdom_carousel")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient Glyph Watermark: 44.sp quote mark with subtle opacity
            Text(
                text = "“",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    lineHeight = 44.sp
                ),
                color = CrispWhite.copy(alpha = 0.05f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row: Compact height
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SubduedZinc,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = "✦ 15-MIN INSIGHT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.1.sp
                            ),
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "View All →",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = ZincMuted,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle Content Container: Flexible weight absorbing vertical space
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // English Quote Text: 14sp, italicized, line height 20sp, maxLines = 4
                    Text(
                        text = "\"${quote.englishQuote}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = CrispWhite,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Roman Urdu Takeaway: Expanded lines with lineHeight 18sp and fontSize 12sp
                    if (quote.romanUrduPunchline.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = DividerDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = quote.romanUrduPunchline,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSilver,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Footer Row: Pinned cleanly to the bottom edge of the card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!quote.author.isNullOrBlank()) {
                        Text(
                            text = "— ${quote.author}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = CrispWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (quote.bookTitle.isNotBlank()) {
                        Text(
                            text = quote.bookTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = ZincMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Bottom Sheet for Today's Missions
 * Opens upon tapping the Animated Notification Bell
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayMissionsBottomSheet(
    todaySnippetCompleted: Boolean,
    todayWordCompleted: Boolean,
    streakCount: Int,
    onDismiss: () -> Unit,
    onNavigateToDailyRecall: () -> Unit,
    onNavigateToWordQuiz: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ModalSheetBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = BorderDark,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "TODAY'S MISSIONS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextPureWhite
                    )
                    Text(
                        text = if (todaySnippetCompleted && todayWordCompleted) "All daily missions accomplished! 🔥" else "+100 XP available today",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = TextSilverMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SubContainerDark,
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text(
                        text = if (todaySnippetCompleted && todayWordCompleted) "COMPLETED" else "ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (todaySnippetCompleted && todayWordCompleted) TextMutedTertiary else TextPureWhite,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Mission Cards Container
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LayeredCardSurface),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mission 1: Snippet MCQs
                    MissionSheetRowItem(
                        icon = Icons.Default.Bolt,
                        title = "10 Snippet MCQs",
                        subtitle = "Test concepts read today",
                        xpReward = "+50 XP",
                        isCompleted = todaySnippetCompleted,
                        onClick = onNavigateToDailyRecall,
                        modifier = Modifier.testTag("daily_snippet_objective")
                    )

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = BorderDark
                    )

                    // Mission 2: Word Vault MCQs
                    MissionSheetRowItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "10 Word Vault MCQs",
                        subtitle = "Spaced repetition vocabulary",
                        xpReward = "+50 XP",
                        isCompleted = todayWordCompleted,
                        onClick = onNavigateToWordQuiz,
                        modifier = Modifier.testTag("daily_word_objective")
                    )
                }
            }

            // Streak Status Reminder
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LayeredCardSurface,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = TextPureWhite,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$streakCount Day Streak Active",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            color = TextPureWhite
                        )
                        Text(
                            text = "Complete your daily missions to protect your streak score.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp
                            ),
                            color = TextSilverMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionSheetRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    xpReward: String,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isCompleted, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCompleted) SubContainerDark else TextPureWhite.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isCompleted) TextMutedTertiary else TextPureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = if (isCompleted) TextMutedTertiary else TextPureWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = TextSilverMuted
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SubContainerDark,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = xpReward,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (isCompleted) TextMutedTertiary else TextPureWhite,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Icon(
                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isCompleted) "Completed" else "Not completed",
                tint = if (isCompleted) TextPureWhite else BorderDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HeroCoverFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubContainerDark),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoStories,
            contentDescription = null,
            tint = TextSilverMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ==========================================
// Dialog Modals for Gamification Challenges
// ==========================================

@Composable
private fun ChapterMasteryChallengeDialog(
    chapterTitle: String,
    bookTitle: String,
    availableMcqCount: Int,
    onStartChallenge: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ModalSheetBackground),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SubContainerDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = "CHAPTER MASTERED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSilverMuted
                )

                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextPureWhite
                )

                Text(
                    text = "$bookTitle • $availableMcqCount questions ready for Mastery Seal test",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextSilverMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onStartChallenge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPureWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Take Mastery Quiz (+150 XP)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CanvasObsidian
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSilverMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetChallengeModalDialog(
    availableCount: Int,
    onStartChallenge: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ModalSheetBackground),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SubContainerDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = "DAILY RECALL READY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSilverMuted
                )

                Text(
                    text = "$availableCount Snippet Questions",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextPureWhite
                )

                Text(
                    text = "Consolidate today's reading concepts and earn bonus experience points.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextSilverMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onStartChallenge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPureWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Start Recall Quiz (+50 XP)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CanvasObsidian
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Maybe Later",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSilverMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WordVaultChallengeModalDialog(
    availableCount: Int,
    onStartChallenge: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ModalSheetBackground),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SubContainerDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = "WORD VAULT DRILL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSilverMuted
                )

                Text(
                    text = "$availableCount Words in Queue",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextPureWhite
                )

                Text(
                    text = "Reinforce vocabulary with active recall and unlock vocabulary mastery.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextSilverMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onStartChallenge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPureWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Start Vocabulary Quiz (+50 XP)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CanvasObsidian
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSilverMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyDoubleBonusModalDialog(
    totalXp: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ModalSheetBackground),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SubContainerDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = TextPureWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = "DAILY DOUBLE COMPLETED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSilverMuted
                )

                Text(
                    text = "+$totalXp XP Claimed!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextPureWhite
                )

                Text(
                    text = "You conquered both Snippet and Word Vault recall missions today. Streak locked in!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = TextSilverMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPureWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Awesome!",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CanvasObsidian
                    )
                }
            }
        }
    }
}
