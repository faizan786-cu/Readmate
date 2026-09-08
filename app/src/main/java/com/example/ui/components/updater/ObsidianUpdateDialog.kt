package com.example.ui.components.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.updater.UpdateInfo
import com.example.data.updater.UpdateState
import java.io.File
import java.util.Locale

// Strict monochrome Obsidian color palette
private val ObsidianDarkest = Color(0xFF0B0B0E)
private val ObsidianSurface = Color(0xFF141418)
private val ObsidianBorder = Color(0xFF27272F)
private val ObsidianIconBox = Color(0xFF1F1F24)
private val ObsidianWhite = Color(0xFFFFFFFF)
private val ObsidianMuted = Color(0xFF71717A)
private val ObsidianTextSubtle = Color(0xFFA1A1AA)
private val ObsidianEmerald = Color(0xFF10B981)

@Composable
fun ObsidianUpdateDialog(
    state: UpdateState.UpdateAvailable,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstall: (File?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = {
            if (!state.isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.isDownloading,
            dismissOnClickOutside = !state.isDownloading
        )
    ) {
        // Centered modal container (#141418 surface, 1dp #27272F border, rounded 20dp, padding 24dp)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header: Icon Box (40dp, #1F1F24, RoundedCornerShape(10.dp)) + "New Update Available" (16sp, Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianIconBox),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "New update available",
                                tint = ObsidianWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "New Update Available",
                            color = ObsidianWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (!state.isDownloading) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss update prompt",
                                tint = ObsidianMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Body: Version Pill Row (new version capsule #27272F + Current version muted text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val formattedNewVersion = if (state.updateInfo.newVersion.startsWith("v", ignoreCase = true)) {
                        state.updateInfo.newVersion
                    } else {
                        "v${state.updateInfo.newVersion}"
                    }

                    val formattedCurrentVersion = if (state.updateInfo.currentVersion.startsWith("v", ignoreCase = true)) {
                        state.updateInfo.currentVersion
                    } else {
                        "v${state.updateInfo.currentVersion}"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianBorder)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formattedNewVersion,
                            color = ObsidianWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Current: $formattedCurrentVersion",
                        color = ObsidianMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Body: Changelog bullet points (sanitized)
                val bulletPoints = remember(state.updateInfo.releaseNotes) {
                    parseReleaseNotes(state.updateInfo.releaseNotes)
                }

                Text(
                    text = "What's New:",
                    color = ObsidianWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bulletPoints.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "•",
                                color = ObsidianMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = item,
                                color = ObsidianTextSubtle,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Error message display if any
                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.errorMessage,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // States:
                // 1. Downloading: Metrics header (Emerald progress + MB text) + 6dp linear progress bar (#27272F track, #10B981 emerald indicator)
                if (state.isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloading Update... (${state.downloadProgress}%)",
                                color = ObsidianEmerald,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (state.totalBytes > 0) {
                                val currentMb = String.format(Locale.US, "%.1f", state.downloadedBytes / (1024f * 1024f))
                                val totalMb = String.format(Locale.US, "%.1f", state.totalBytes / (1024f * 1024f))
                                Text(
                                    text = "$currentMb / $totalMb MB",
                                    color = ObsidianMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Linear progress bar (#10B981 emerald indicator, #27272F base track, 6dp height)
                        LinearProgressIndicator(
                            progress = { (state.downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ObsidianEmerald,
                            trackColor = ObsidianBorder
                        )
                    }
                }
                // 2. Ready to install: "Install & Relaunch →" button
                else if (state.downloadedFile != null && state.downloadedFile.exists()) {
                    Button(
                        onClick = { onInstall(state.downloadedFile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianWhite,
                            contentColor = ObsidianDarkest
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Install & Relaunch →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ObsidianDarkest
                        )
                    }
                }
                // 3. Default Idle State: "Later" (text button) and "Update Now" (44dp pill button)
                else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "Later",
                                color = ObsidianMuted,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { onStartDownload(state.updateInfo) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianWhite,
                                contentColor = ObsidianDarkest
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "Update Now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ObsidianDarkest
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts, sanitizes, and parses bullet points from GitHub release changelog text.
 * Technical repository, keystore, branch, and commit jargon are thoroughly filtered out.
 */
private fun parseReleaseNotes(notes: String): List<String> {
    if (notes.isBlank()) {
        return listOf(
            "Performance and stability enhancements",
            "Latest automated knowledge engine updates",
            "Reader stability and bug fixes"
        )
    }

    val technicalKeywords = listOf(
        "github", "repository", "keystore", "signed with", "master", "commit",
        "merge branch", "pull request", "pr #", "workflow", "ci/cd", "sha-",
        "gradlew", "dependabot", "release.apk"
    )

    val lines = notes.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val extracted = mutableListOf<String>()
    for (line in lines) {
        if (line.startsWith("#")) continue // skip markdown headers

        val lowerLine = line.lowercase(Locale.ROOT)
        // Skip technical metadata lines
        if (technicalKeywords.any { lowerLine.contains(it) }) continue

        val cleaned = line
            .removePrefix("*")
            .removePrefix("-")
            .removePrefix("•")
            .trim()

        if (cleaned.isNotBlank()) {
            extracted.add(cleaned)
        }
    }

    return if (extracted.isNotEmpty()) {
        extracted.take(6)
    } else {
        listOf("New features, optimizations, and bug fixes included.")
    }
}
