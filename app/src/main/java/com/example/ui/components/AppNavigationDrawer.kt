package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Strict Figma Obsidian Monochrome Color Palette
private val DarkDrawerBg = Color(0xFF0B0B0E)
private val TopBrandCardBg = Color(0xFF141418)
private val LogoBoxBg = Color(0xFF0B0B0E)
private val CardSurfaceActive = Color(0xFF1F1F24)
private val FineBorderColor = Color(0xFF27272F)
private val ActiveBorderColor = Color(0xFF2E2E38)
private val SubheaderColor = Color(0xFF52525B)
private val DividerColor = Color(0xFF1A1A20)
private val PrimaryWhite = Color(0xFFFFFFFF)
private val SecondaryGray = Color(0xFFA1A1AA)

@Composable
fun AppNavigationDrawer(
    drawerState: DrawerState,
    currentRoute: String,
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToLibrary: () -> Unit,
    onNavigateToWordVault: () -> Unit = {},
    onNavigateToProgress: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onReportIssue: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkDrawerBg,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .testTag("app_navigation_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // 1. Top Brand Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TopBrandCardBg,
                        border = BorderStroke(1.dp, FineBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_header")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // App Logo Box (Size 42.dp, bg #0B0B0E, border 1.dp #27272F, shape 10.dp)
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = LogoBoxBg,
                                border = BorderStroke(1.dp, FineBorderColor)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    ReadMateBrandLogo(
                                        size = 32.dp,
                                        showWordmark = false
                                    )
                                }
                            }

                            // Typography Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "ReadMate",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = PrimaryWhite,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFF1F1F24),
                                    border = BorderStroke(1.dp, FineBorderColor)
                                ) {
                                    Text(
                                        text = "EXECUTIVE TIER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = SecondaryGray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Navigation Structure & Ordering (Scrollable if screen height is constrained)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // SECTION 1: WORKSPACE
                        Text(
                            text = "WORKSPACE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = SubheaderColor,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                        )

                        // Item 1: Dashboard
                        DrawerMenuItem(
                            label = "Dashboard",
                            icon = Icons.Default.Dashboard,
                            isSelected = currentRoute == "dashboard",
                            onClick = onNavigateToDashboard,
                            modifier = Modifier.testTag("drawer_menu_dashboard")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item 2: Library
                        DrawerMenuItem(
                            label = "Library",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            isSelected = currentRoute == "library",
                            onClick = onNavigateToLibrary,
                            modifier = Modifier.testTag("drawer_menu_library")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item 3: Word Vault
                        DrawerMenuItem(
                            label = "Word Vault",
                            icon = Icons.Default.Bookmarks,
                            isSelected = currentRoute.startsWith("wordvault"),
                            onClick = onNavigateToWordVault,
                            modifier = Modifier.testTag("drawer_menu_word_vault")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item 4: Progress
                        DrawerMenuItem(
                            label = "Progress",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isSelected = currentRoute == "progress",
                            onClick = onNavigateToProgress,
                            modifier = Modifier.testTag("drawer_menu_progress")
                        )

                        // Divider: Subtle horizontal rule
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = DividerColor,
                            thickness = 1.dp
                        )

                        // SECTION 2: SYSTEM & SUPPORT
                        Text(
                            text = "SYSTEM & SUPPORT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = SubheaderColor,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )

                        // Item 5: Settings
                        DrawerMenuItem(
                            label = "Settings",
                            icon = Icons.Default.Settings,
                            isSelected = currentRoute == "settings",
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("drawer_menu_settings")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item 6: Feedback & Support
                        DrawerMenuItem(
                            label = "Feedback & Support",
                            icon = Icons.Default.BugReport,
                            isSelected = false,
                            onClick = onReportIssue,
                            modifier = Modifier.testTag("drawer_menu_report_issue")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item 7: About ReadMate
                        DrawerMenuItem(
                            label = "About ReadMate",
                            icon = Icons.Outlined.Info,
                            isSelected = currentRoute == "about_screen",
                            onClick = onNavigateToAbout,
                            modifier = Modifier.testTag("drawer_menu_about")
                        )
                    }

                    // 4. Minimalist Footer (Bottom pinned with subtle top border line #1A1A20)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(
                            color = DividerColor,
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "ReadMate • v$appVersionName",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = SubheaderColor
                        )
                    }
                }
            }
        },
        content = content
    )
}

/**
 * Figma-grade Obsidian Navigation Drawer Row with refined ergonomics and visual states:
 * - Height: 48.dp, horizontal padding: 12.dp, shape: RoundedCornerShape(12.dp).
 * - Active: Background #1F1F24, border 1.dp solid #2E2E38, Icon & Text #FFFFFF (SemiBold 14sp), ChevronRight #FFFFFF (18.dp).
 * - Inactive: Background Transparent, Icon & Text #A1A1AA (Medium 14sp), no trailing chevron.
 */
@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CardSurfaceActive else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, ActiveBorderColor) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) PrimaryWhite else SecondaryGray,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.1.sp
                ),
                color = if (isSelected) PrimaryWhite else SecondaryGray,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = PrimaryWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
