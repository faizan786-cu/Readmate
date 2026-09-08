package com.example.ui.components.feedback

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.feedback.FeedbackApiService
import kotlinx.coroutines.launch

// Strict monochrome Obsidian palette with emerald and danger accents
private val CanvasObsidian = Color(0xFF0B0B0E)
private val SurfaceObsidian = Color(0xFF141418)
private val BorderObsidian = Color(0xFF27272F)
private val IconBoxObsidian = Color(0xFF1F1F24)
private val PureWhite = Color(0xFFFFFFFF)
private val ZincMuted = Color(0xFF71717A)
private val ZincSubtle = Color(0xFFA1A1AA)
private val DangerRed = Color(0xFFEF4444)

@Composable
fun ReportIssueModal(
    isOpen: Boolean,
    userEmail: String,
    appVersionName: String,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit,
    feedbackApiService: FeedbackApiService = remember { FeedbackApiService() },
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Bug") }
    var messageText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Bug", "Feature Request", "Other Query")
    val deviceManufacturerModel = remember {
        "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} ${Build.MODEL}".trim()
    }
    val effectiveAccount = remember(userEmail) {
        userEmail.ifBlank { "guest" }
    }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = !isSubmitting
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceObsidian,
            border = BorderStroke(1.dp, BorderObsidian),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("report_issue_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header: Icon box (36dp, #1F1F24) + Title ("Report an Issue & Query", 16sp, Bold, #FFFFFF) + Close button
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(IconBoxObsidian),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Report issue",
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Report an Issue & Query",
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    }

                    if (!isSubmitting) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("report_issue_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close dialog",
                                tint = ZincMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Category Selector (Single-choice chips: Bug, Feature Request, Other Query)
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = ZincMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PureWhite else IconBoxObsidian,
                            border = if (isSelected) null else BorderStroke(1.dp, BorderObsidian),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    enabled = !isSubmitting,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedCategory = category
                                        errorMessage = null
                                    }
                                )
                                .testTag("report_category_${category.lowercase().replace(" ", "_")}")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CanvasObsidian else ZincMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message Input Area
                Text(
                    text = "MESSAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = ZincMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        if (errorMessage != null) errorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = "Describe the bug, screen where it happened, or your query...",
                            color = ZincMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    minLines = 4,
                    maxLines = 6,
                    enabled = !isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CanvasObsidian,
                        unfocusedContainerColor = CanvasObsidian,
                        disabledContainerColor = CanvasObsidian,
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = BorderObsidian,
                        disabledBorderColor = BorderObsidian,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        disabledTextColor = ZincSubtle,
                        cursorColor = PureWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_issue_message_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Fetched Metadata (Read-Only Preview Row)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IconBoxObsidian,
                    border = BorderStroke(1.dp, BorderObsidian),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Device: $deviceManufacturerModel • Version: v$appVersionName • Account: $effectiveAccount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        ),
                        color = ZincMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                // Inline Error Display
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = DangerRed,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.testTag("report_issue_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Submit Button: Full-width button (44dp height, background #FFFFFF, text #0B0B0E, Bold, rounded 12dp)
                Button(
                    onClick = {
                        if (messageText.isBlank()) {
                            errorMessage = "Please enter a message before submitting."
                            return@Button
                        }
                        isSubmitting = true
                        errorMessage = null

                        coroutineScope.launch {
                            val result = feedbackApiService.submitReport(
                                email = effectiveAccount,
                                reportType = selectedCategory,
                                message = messageText.trim(),
                                appVersion = "v$appVersionName"
                            )

                            isSubmitting = false
                            result.onSuccess {
                                onSubmitSuccess()
                            }.onFailure {
                                errorMessage = "Submission failed. Please try again."
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = CanvasObsidian,
                        disabledContainerColor = PureWhite.copy(alpha = 0.6f),
                        disabledContentColor = CanvasObsidian.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("report_issue_submit_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = CanvasObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Submit Report",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CanvasObsidian
                        )
                    }
                }
            }
        }
    }
}
