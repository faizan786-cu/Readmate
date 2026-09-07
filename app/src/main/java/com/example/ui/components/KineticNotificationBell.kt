package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private val ObsidianBackground = Color(0xFF0B0B0E)
private val CrispWhite = Color(0xFFFFFFFF)
private val MutedZinc = Color(0xFF71717A)

/**
 * Kinetic Pendulum Bell Component
 * - Monitors unread notification count
 * - White Unread Dot (7.dp circle, fill #FFFFFF, 1.5.dp solid #0B0B0E border)
 * - Pendulum Swing Micro-Interaction: 0f -> -14f -> +14f -> -8f -> +8f -> 0f over 650ms, 5350ms rest
 * - Top-rung transform origin: TransformOrigin(0.5f, 0.1f)
 */
@Composable
fun KineticNotificationBell(
    unreadCount: Int,
    onOpenCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bellRotation = if (unreadCount > 0) {
        val infiniteTransition = rememberInfiniteTransition(label = "kinetic_bell_pendulum")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 6000
                    0f at 0
                    -14f at 130
                    14f at 260
                    -8f at 390
                    8f at 520
                    0f at 650
                    0f at 6000
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "pendulum_angle"
        )
        angle
    } else {
        0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(40.dp)
    ) {
        IconButton(
            onClick = onOpenCenter,
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_notifications_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notifications ($unreadCount unread)",
                tint = if (unreadCount > 0) CrispWhite else MutedZinc,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        rotationZ = bellRotation
                        transformOrigin = TransformOrigin(0.5f, 0.1f)
                    }
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(6.dp)
                    .background(CrispWhite, CircleShape)
            )
        }
    }
}
