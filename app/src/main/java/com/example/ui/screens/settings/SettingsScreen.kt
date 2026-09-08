package com.example.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.ReadMateApplication
import com.example.data.model.GeminiConnectionState
import com.example.data.repository.UserPreferencesRepository
import com.example.data.updater.UpdateState
import com.example.ui.components.updater.ObsidianUpdateDialog
import androidx.compose.material.icons.filled.Sync
import com.example.ui.components.chat.DedicatedSnippedPassageQuoteCard
import com.example.ui.components.ReadMateBrandLogo
import com.example.ui.screens.chat.FormattedRomanUrduContent
import com.example.ui.theme.ReadMateGreen
import com.example.ui.theme.ReadMateGreenContainer
import com.example.ui.theme.ReadMateOnGreenContainer
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.SettingsViewModel

// Strict Monochrome Obsidian Color Palette
private val CanvasObsidian = Color(0xFF0B0B0E)
private val CardSurfaceZinc = Color(0xFF141418)
private val SubduedZinc = Color(0xFF101013)
private val IconContainerZinc = Color(0xFF1C1C22)
private val SlateBorder = Color(0xFF27272F)
private val CrispWhite = Color(0xFFFFFFFF)
private val ZincMuted = Color(0xFFA1A1AA)
private val ZincSubtle = Color(0xFF71717A)
private val DangerRed = Color(0xFFEF4444)
private val DangerRedBg = Color(0xFF3B1215)
private val DangerRedBorder = Color(0x44EF4444)

private const val SAMPLE_PREVIEW_AI_RESPONSE = """## 🧠 Understanding
*Marcus Aurelius* kehte hain ke **insan ki asli taqat** uske apne dimagh aur soch mein hoti hai. Jab aap bahar ki pareshaniyon ke bajaye apne **kirdar aur amil** par tawajah dete hain, toh aap ko ek gehri **internal shanti** milti hai.

## 💡 Lesson
**Core Takeaway:** Energy sirf un cheezon par lagayein jo aap ke ikhtiyar mein hain. Nateeja qismat aur mehnat par chor dein.

## 🔑 Insights
1. **Dichotomy of Control**: Apne ikhtiyar aur gher-ikhtiyar ke darmiyan wazeh farq samjhein.
2. **Mental Resilience**: Bahir ke halat ko apne jazbaat par haawi na hone dein.

## 🌍 Example
Jab traffic ya barish ki wajah se late ho jayein, toh ghussa karne ke bajaye us waqt ko sabr seekhne mein istemal karein."""

private const val SAMPLE_PREVIEW_SNIPPET = """[Page 42]
"You have power over your mind - not outside events. Realize this, and you will find strength." """

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToApiManagement: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToGeminiConfig: () -> Unit = onNavigateToApiManagement
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    val app = context.applicationContext as ReadMateApplication
    val updateManager = remember(app) { app.updateManager }
    val updateState by updateManager.updateState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val responseFontSizePercent by viewModel.responseFontSizePercent.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val currentUser = remember { app.authRepository.getCurrentUser() }

    // Obsidian Auto-Update Dialog
    if (updateState is UpdateState.UpdateAvailable) {
        val available = updateState as UpdateState.UpdateAvailable
        ObsidianUpdateDialog(
            state = available,
            onStartDownload = { info -> updateManager.startDownload(info) },
            onInstall = { file -> updateManager.installUpdate(file) },
            onDismiss = { updateManager.dismissDialog() }
        )
    }

    // Dialog: Test API Dashboard Modal
    TestApiDashboardDialog(
        isOpen = viewModel.showTestApiModal,
        keyLabel = "Active API Key",
        maskedKey = viewModel.testDashboardMaskedKey,
        testResults = viewModel.modelTestResults,
        isLoading = viewModel.isTestingAllModels,
        onRetest = viewModel::retryTestApiDashboard,
        onDismiss = viewModel::dismissTestApiDashboard
    )

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = CardSurfaceZinc,
            titleContentColor = CrispWhite,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    text = "Disconnect Gemini?",
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite
                )
            },
            text = {
                Text(
                    text = "All locally stored Gemini API keys will be removed from this device.",
                    color = ZincMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DangerRed
                    ),
                    modifier = Modifier.testTag("confirm_disconnect_button")
                ) {
                    Text("Disconnect All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ZincMuted
                    ),
                    modifier = Modifier.testTag("cancel_disconnect_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!viewModel.isResettingData) {
                    showResetConfirmDialog = false
                }
            },
            containerColor = CardSurfaceZinc,
            titleContentColor = DangerRed,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(14.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Reset All Data?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = DangerRed
                )
            },
            text = {
                Text(
                    text = "This will permanently wipe your entire library, reading streak, XP points, and quiz history. This action cannot be undone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ZincMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllAppData {
                            showResetConfirmDialog = false
                            Toast.makeText(context, "All data has been successfully reset.", Toast.LENGTH_SHORT).show()
                            onNavigateToDashboard()
                        }
                    },
                    enabled = !viewModel.isResettingData,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = CrispWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_wipe_data_button")
                ) {
                    if (viewModel.isResettingData) {
                        CircularProgressIndicator(
                            color = CrispWhite,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resetting...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Yes, Wipe Everything", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    enabled = !viewModel.isResettingData,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ZincMuted
                    ),
                    modifier = Modifier.testTag("cancel_wipe_data_button")
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = CardSurfaceZinc,
            titleContentColor = CrispWhite,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    text = "Sign Out?",
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite
                )
            },
            text = {
                Text(
                    text = "You will be signed out of your ReadMate account on this device. Your local books and reading progress remain preserved.",
                    color = ZincMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        app.authRepository.logout()
                        onNavigateToAuth()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DangerRed
                    ),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ZincMuted
                    ),
                    modifier = Modifier.testTag("cancel_sign_out_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasObsidian),
        containerColor = CanvasObsidian,
        contentColor = CrispWhite,
        topBar = {
            TopAppBar(
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
                            text = "Settings",
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite,
                            fontSize = 19.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("settings_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open menu",
                            tint = CrispWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasObsidian,
                    titleContentColor = CrispWhite,
                    navigationIconContentColor = CrispWhite
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasObsidian)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Section 1: AI Integration
                Text(
                    text = "AI Integration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_settings_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        val isConnected = app.secureApiKeyStorage.hasValidCredentials()

                        // Header Layout: Left 40dp icon box, Clean Title "Gemini API Keys" on single line with Status Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IconContainerZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Gemini API Keys",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = CrispWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (isConnected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF064E3B).copy(alpha = 0.4f),
                                            border = BorderStroke(1.dp, Color(0xFF064E3B)),
                                            modifier = Modifier.testTag("status_badge_connected")
                                        ) {
                                            Text(
                                                text = "Connected • Active",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 0.3.sp
                                                ),
                                                color = Color(0xFF10B981),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SubduedZinc,
                                            border = BorderStroke(1.dp, SlateBorder),
                                            modifier = Modifier.testTag("status_badge_not_connected")
                                        ) {
                                            Text(
                                                text = "Not Configured",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 0.3.sp
                                                ),
                                                color = ZincSubtle,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = "Manage pooled keys, bulk import, and key health.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = ZincMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isConnected) {
                            val keys = remember(connectionState) { app.secureApiKeyStorage.getApiKeys() }
                            val maskedPrimary = remember(connectionState) {
                                app.secureApiKeyStorage.getPrimaryApiKey()?.let { k ->
                                    if (k.length >= 11) "${k.take(7)}...${k.takeLast(4)}" else "${k.take(4)}••••"
                                } ?: "••••••••"
                            }
                            // Failover Meta row in subtle #71717A color
                            Text(
                                text = if (keys.size > 1) {
                                    "${keys.size} keys configured with auto-failover • Primary: $maskedPrimary"
                                } else {
                                    "1 key configured with auto-failover • Primary: $maskedPrimary"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = ZincSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Action Buttons with equal visual weight, minimum 40dp height, proper internal padding
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Primary Action: Manage Keys
                                Button(
                                    onClick = onNavigateToApiManagement,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SubduedZinc,
                                        contentColor = CrispWhite
                                    ),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("change_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Manage",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }

                                // Secondary Action: Test API
                                OutlinedButton(
                                    onClick = { viewModel.openTestApiDashboard() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SubduedZinc,
                                        contentColor = ZincMuted
                                    ),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(40.dp)
                                        .testTag("test_api_dashboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = ZincMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Test",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }

                                // Secondary Action: Disconnect (danger-tinted outline)
                                OutlinedButton(
                                    onClick = { showDisconnectDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SubduedZinc,
                                        contentColor = DangerRed
                                    ),
                                    border = BorderStroke(1.dp, DangerRedBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(40.dp)
                                        .testTag("disconnect_gemini_button")
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onNavigateToApiManagement,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CrispWhite,
                                        contentColor = CanvasObsidian
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("connect_gemini_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = CanvasObsidian
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Configure API Keys",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CanvasObsidian,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onNavigateToApiManagement,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SubduedZinc,
                                        contentColor = ZincMuted
                                    ),
                                    border = BorderStroke(1.dp, SlateBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("configure_gemini_button")
                                ) {
                                    Text("Add Key", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Section 2: Reading & Display Preferences (Response Font Size)
                Text(
                    text = "Reading & Display",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("response_font_size_settings_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Header row: 40dp Icon container, Title & Subtitle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IconContainerZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FormatSize,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Response Font Size",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Adjust AI explanation card text size",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = ZincMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Row: Left "Font Scale", Right Stepper Pill [ - ] 100% [ + ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Font Scale",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = CrispWhite
                                )

                                if (responseFontSizePercent != UserPreferencesRepository.DEFAULT_FONT_SIZE_PERCENT) {
                                    IconButton(
                                        onClick = { viewModel.resetFontSize() },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("reset_font_size_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = "Reset font size to default",
                                            tint = ZincMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Stepper Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    // Minus button
                                    IconButton(
                                        onClick = { viewModel.decreaseFontSize() },
                                        enabled = responseFontSizePercent > UserPreferencesRepository.MIN_FONT_SIZE_PERCENT,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("decrease_font_size_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease font size",
                                            tint = if (responseFontSizePercent > UserPreferencesRepository.MIN_FONT_SIZE_PERCENT) CrispWhite else ZincSubtle,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    Text(
                                        text = "$responseFontSizePercent%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = CrispWhite,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp)
                                            .testTag("font_size_percentage_text")
                                    )

                                    // Plus button
                                    IconButton(
                                        onClick = { viewModel.increaseFontSize() },
                                        enabled = responseFontSizePercent < UserPreferencesRepository.MAX_FONT_SIZE_PERCENT,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("increase_font_size_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase font size",
                                            tint = if (responseFontSizePercent < UserPreferencesRepository.MAX_FONT_SIZE_PERCENT) CrispWhite else ZincSubtle,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Preview Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = ZincSubtle
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text(
                                    text = "$responseFontSizePercent%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CrispWhite,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Streamlined preview container with 12dp internal padding
                        val previewFontScale = responseFontSizePercent / 100f

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SubduedZinc,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 135.dp)
                                .testTag("ai_response_preview_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ZincMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Understanding",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = ZincMuted
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "Just now",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = ZincSubtle
                                    )
                                }

                                Text(
                                    text = "Marcus Aurelius kehte hain ke insan ki asli taqat uske apne dimagh aur soch mein hoti hai. Jab aap kirdar aur amil par tawajah dete hain, toh gehri shanti milti hai.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (13.5 * previewFontScale).sp,
                                        lineHeight = (19 * previewFontScale).sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = Color(0xFFE4E4E7),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Section 3: About
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAbout() }
                        .testTag("settings_about_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IconContainerZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ReadMate",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Version $appVersionName • View Architecture",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = ZincMuted
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View About",
                                tint = ZincSubtle,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "A local-first personal reading companion. All your books, chapters, messages, and vocabulary are safely stored on your device.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = ZincMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Updater Card: Sanitized, elegant metadata display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (updateState !is UpdateState.Checking) {
                                updateManager.checkForUpdates(force = true)
                            }
                        }
                        .testTag("settings_check_update_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IconContainerZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Check for updates",
                                        tint = CrispWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Auto-Updater",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (val state = updateState) {
                                        is UpdateState.Checking -> "Connecting to releases..."
                                        is UpdateState.UpToDate -> "Up to date (v$appVersionName)"
                                        is UpdateState.UpdateAvailable -> "New version ready: ${state.updateInfo.newVersion}"
                                        is UpdateState.Error -> state.message
                                        else -> "Installed: v$appVersionName • GitHub Releases"
                                    },
                                    fontSize = 12.sp,
                                    color = ZincMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (updateState is UpdateState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CrispWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Check for Updates ↗",
                                color = CrispWhite,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Section 4: Account & Cloud Identity
                Text(
                    text = "Account & Cloud Identity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_settings_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        val userName = currentUser?.name?.ifBlank { "Khan" } ?: "Khan"
                        val userEmail = currentUser?.email ?: "nestiffy@gmail.com"
                        val initialChar = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "K"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Obsidian Avatar Circle: #1C1C22 with crisp white initial letter
                            Surface(
                                shape = CircleShape,
                                color = IconContainerZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initialChar,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        ),
                                        color = CrispWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = ZincMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sign Out Button: Outlined style with #27272F border, #141418 background
                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurfaceZinc,
                                contentColor = CrispWhite
                            ),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                tint = CrispWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign Out",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = CrispWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Section 5: Danger Zone
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DangerRed,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("danger_zone_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardSurfaceZinc
                    ),
                    border = BorderStroke(1.dp, SlateBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DangerRedBg,
                                border = BorderStroke(1.dp, DangerRedBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Reset All Data",
                                        tint = DangerRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reset All App Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Permanently delete all books, reading progress, quiz scores, XP, and word vault history.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                    color = ZincMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Danger Reset Action: #141418 background with #EF4444 outline and red text/icon
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DangerRedBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurfaceZinc,
                                contentColor = DangerRed
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("reset_all_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset All App Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DangerRed
                            )
                        }
                    }
                }

                // Safe scrollable bottom insets padding
                Spacer(modifier = Modifier.navigationBarsPadding().height(32.dp))
            }
        }
    }
}
