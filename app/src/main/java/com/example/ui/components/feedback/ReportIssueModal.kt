package com.example.ui.components.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

// Strict monochrome Obsidian palette
private val CanvasObsidian = Color(0xFF0B0B0E)
private val SurfaceObsidian = Color(0xFF141418)
private val InputBgObsidian = Color(0xFF0E0E12)
private val BorderObsidian = Color(0xFF27272F)
private val BorderFocused = Color(0xFF52525B)
private val IconBoxObsidian = Color(0xFF1F1F24)
private val PureWhite = Color(0xFFFFFFFF)
private val ZincMuted = Color(0xFF71717A)
private val ZincSubtle = Color(0xFFA1A1AA)
private val PlaceholderColor = Color(0xFF52525B)
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
    // Category selection: "Bug", "Idea", "Question" (mapped back to backend report_type values if needed)
    var selectedCategory by remember { mutableStateOf("Bug") }
    var messageText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Bug", "Idea", "Question")
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
                    .padding(20.dp)
            ) {
                // Header Row: Icon box (36dp, #1F1F24, RoundedCornerShape(10.dp)) + "Report an Issue" (16sp, Bold, #FFFFFF) + Subtle close button (28dp, CircleShape, #1F1F24)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IconBoxObsidian),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Report issue",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Report an Issue",
                        color = PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (!isSubmitting) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(IconBoxObsidian)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss
                                )
                                .testTag("report_issue_close_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close dialog",
                                tint = ZincMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Category Selector: Label "TYPE" (11sp, Bold, #71717A, uppercase, tracking 1.sp)
                Text(
                    text = "TYPE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ZincMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3 chips row: "Bug", "Idea", "Question"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PureWhite else SurfaceObsidian,
                            border = if (isSelected) null else BorderStroke(1.dp, BorderObsidian),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    enabled = !isSubmitting,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedCategory = category
                                        errorMessage = null
                                    }
                                )
                                .testTag("report_category_${category.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) CanvasObsidian else ZincSubtle,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message Input Area: Label "DESCRIPTION" (11sp, Bold, #71717A, uppercase, tracking 1.sp)
                Text(
                    text = "DESCRIPTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
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
                            text = "What's happening? Be as specific as you like...",
                            color = PlaceholderColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = PureWhite,
                        fontSize = 14.sp
                    ),
                    minLines = 4,
                    maxLines = 5,
                    enabled = !isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBgObsidian,
                        unfocusedContainerColor = InputBgObsidian,
                        disabledContainerColor = InputBgObsidian,
                        focusedBorderColor = BorderFocused,
                        unfocusedBorderColor = BorderObsidian,
                        disabledBorderColor = BorderObsidian,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        disabledTextColor = ZincSubtle,
                        cursorColor = PureWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_issue_message_input")
                )

                // Inline Error Display if validation or network failure occurs
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

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Button: 46dp height, full width, RoundedCornerShape(12.dp), #FFFFFF background, #0B0B0E text
                Button(
                    onClick = {
                        if (messageText.isBlank()) {
                            errorMessage = "Please enter a description before submitting."
                            return@Button
                        }
                        isSubmitting = true
                        errorMessage = null

                        // Map "Idea" -> "Feature Request", "Question" -> "Other Query" or keep clean
                        val reportTypePayload = when (selectedCategory) {
                            "Idea" -> "Feature Request"
                            "Question" -> "Other Query"
                            else -> "Bug"
                        }

                        coroutineScope.launch {
                            val result = feedbackApiService.submitReport(
                                email = effectiveAccount,
                                reportType = reportTypePayload,
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
                        .height(46.dp)
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
                            text = "Send Report",
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
