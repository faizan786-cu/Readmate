package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.InAppNotification
import com.example.data.model.NotificationType

private val ScrimColor = Color(0xCC0B0B0E)
private val SurfaceZinc = Color(0xFF141418)
private val SubduedZinc = Color(0xFF101013)
private val SlateBorder = Color(0xFF27272F)
private val CrispWhite = Color(0xFFFFFFFF)
private val TextSilver = Color(0xFFA1A1AA)
private val ZincMuted = Color(0xFF71717A)
private val IconSubtle = Color(0xFF52525B)

@Composable
fun NotificationCenterDialog(
    isOpen: Boolean,
    notifications: List<InAppNotification>,
    onDismiss: () -> Unit,
    onNotificationAction: (InAppNotification) -> Unit,
    onClearNotification: (String) -> Unit = {}
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Root Full-Screen Scrim Box (captures taps outside the dialog)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Centered Modal Window with explicit minimum width & height
            Surface(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 380.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(min = 280.dp, max = 540.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks inside the modal card
                    )
                    .testTag("notification_center_dialog"),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceZinc,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Fixed Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "NOTIFICATIONS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = ZincMuted
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("close_notification_center_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close notifications",
                                tint = CrispWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (notifications.isEmpty()) {
                        // Robust Empty State Card ("All Caught Up")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsNone,
                                        contentDescription = null,
                                        tint = IconSubtle,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "All Caught Up",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CrispWhite,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "No pending retention reviews or active notices for today.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                ),
                                color = ZincMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("dismiss_empty_notifications_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SubduedZinc,
                                    contentColor = CrispWhite
                                ),
                                border = BorderStroke(1.dp, SlateBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = CrispWhite
                                )
                            }
                        }
                    } else {
                        // Active Local Notification Cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(notifications, key = { it.id }) { notification ->
                                NotificationCard(
                                    notification = notification,
                                    onAction = { onNotificationAction(notification) }
                                )
                            }
                        }

                        // Modal Footer
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = SlateBorder,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("dismiss_notification_center_button")
                        ) {
                            Text(
                                text = "Dismiss & Close",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = ZincMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: InAppNotification,
    onAction: () -> Unit
) {
    val badgeLabel = when (notification.type) {
        NotificationType.RETENTION_QUIZ -> "RETENTION QUIZ"
        NotificationType.WORD_VAULT_QUIZ -> "WORD VAULT"
        NotificationType.STREAK_MILESTONE -> "STREAK"
        NotificationType.INACTIVE_RECALL -> "INACTIVE BOOK"
    }

    val actionText = notification.actionLabel ?: when (notification.type) {
        NotificationType.RETENTION_QUIZ -> "Start Quiz →"
        NotificationType.WORD_VAULT_QUIZ -> "Inspect Vault →"
        NotificationType.STREAK_MILESTONE -> "View Progress →"
        NotificationType.INACTIVE_RECALL -> "Resume Reading →"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = SubduedZinc,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Tag Pill
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SlateBorder
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = CrispWhite,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                )
            }

            // Title
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CrispWhite
            )

            // Message
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                ),
                color = TextSilver
            )

            // Direct Navigation Action Button
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .height(34.dp)
                    .align(Alignment.End)
                    .testTag("notification_cta_${notification.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrispWhite,
                    contentColor = Color(0xFF0B0B0E)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
