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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
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

// Ultra-modern Figma Monochrome Color Palette
private val DarkDrawerBg = Color(0xFF0F0F12)
private val CardSurfaceSecondary = Color(0xFF16161B)
private val CardSurfaceActive = Color(0xFF1E1E24)
private val FineBorderColor = Color(0xFF27272A)
private val ActiveBorderColor = Color(0xFF3F3F46)
private val PrimaryWhite = Color(0xFFFFFFFF)
private val MutedWhite = Color(0xFFF4F4F5)
private val SecondaryGray = Color(0xFFA1A1AA)
private val TertiaryGray = Color(0xFF71717A)

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
                    // Top header identity card with the unified emblem (size = 40dp) beside the user's name/tier
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardSurfaceSecondary,
                        border = BorderStroke(1.dp, FineBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("drawer_header")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReadMateBrandLogo(
                                size = 40.dp,
                                showWordmark = false
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "ReadMate",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = PrimaryWhite,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF27272F),
                                    border = BorderStroke(0.5.dp, Color(0xFF3F3F46))
                                ) {
                                    Text(
                                        text = "EXECUTIVE TIER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = Color(0xFFD4D4D8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Low-opacity horizontal divider line
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = FineBorderColor,
                        thickness = 0.8.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Navigation Menu Items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DrawerMenuItem(
                            label = "Dashboard",
                            icon = Icons.Default.Dashboard,
                            isSelected = currentRoute == "dashboard",
                            onClick = onNavigateToDashboard,
                            modifier = Modifier.testTag("drawer_menu_dashboard")
                        )

                        DrawerMenuItem(
                            label = "Library",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            isSelected = currentRoute == "library",
                            onClick = onNavigateToLibrary,
                            modifier = Modifier.testTag("drawer_menu_library")
                        )

                        DrawerMenuItem(
                            label = "Word Vault",
                            icon = Icons.Default.Bookmarks,
                            isSelected = currentRoute.startsWith("wordvault"),
                            onClick = onNavigateToWordVault,
                            modifier = Modifier.testTag("drawer_menu_word_vault")
                        )

                        DrawerMenuItem(
                            label = "Progress",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isSelected = currentRoute == "progress",
                            onClick = onNavigateToProgress,
                            modifier = Modifier.testTag("drawer_menu_progress")
                        )

                        DrawerMenuItem(
                            label = "Settings",
                            icon = Icons.Default.Settings,
                            isSelected = currentRoute == "settings",
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("drawer_menu_settings")
                        )

                        DrawerMenuItem(
                            label = "About ReadMate",
                            icon = Icons.Outlined.Info,
                            isSelected = currentRoute == "about_screen",
                            onClick = onNavigateToAbout,
                            modifier = Modifier.testTag("drawer_menu_about")
                        )
                    }

                    // Minimalist Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        HorizontalDivider(
                            color = FineBorderColor,
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "ReadMate • v$appVersionName",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = TertiaryGray
                        )
                    }
                }
            }
        },
        content = content
    )
}

/**
 * Modern Navigation Drawer Pill Item with Figma-grade active and inactive states.
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
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) PrimaryWhite else SecondaryGray,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
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
                    tint = MutedWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

