package com.example.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashBackground = Color(0xFF0B0B0E)
private val PureWhite = Color(0xFFFFFFFF)
private val SoftWhite = Color(0xFFFAFAFA)
private val HairlineZinc = Color(0xFF27272F)
private val HairlineMid = Color(0xFF3F3F46)
private val TextSubMuted = Color(0xFF71717A)

/**
 * Cold Launch "Knowledge Monogram" Aperture Animation.
 *
 * Sequence: Total duration exactly 1600ms (1.6s).
 * - Phase 1 (0ms - 600ms): Crisp vertical spine appears (#FFFFFF) and splits into two symmetrical
 *   geometric book-leaf pages fanning outward horizontally using FastOutSlowInEasing.
 * - Phase 2 (600ms - 1100ms): Three horizontal scanning light pulses (laser-thin hairlines) sweep
 *   vertically through the open leaves, symbolizing automated structural parsing and ingestion.
 * - Phase 3 (1100ms - 1600ms): App title "READMATE" fades in with letter-spacing expanding from 2.sp
 *   to 6.sp, followed by a smooth container fade into Dashboard.
 *
 * Zero Modal Overlay: Never hosts or renders dialogs; triggers onAnimationFinished directly.
 */
@Composable
fun FirstLaunchSplash(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Phase 1: Spine alpha & Page Open Progress (0f to 1f)
    val spineAlpha = remember { Animatable(0f) }
    val bookOpenProgress = remember { Animatable(0f) }

    // Phase 2: Scanning light pulses progress (0f to 1f) and scan alpha
    val scanProgress = remember { Animatable(0f) }
    val scanAlpha = remember { Animatable(0f) }

    // Phase 3: Title alpha, letter-spacing progress, and final exit fade
    val titleAlpha = remember { Animatable(0f) }
    val letterSpacingProgress = remember { Animatable(0f) }
    val overallExitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // --- PHASE 1 (0ms - 600ms): Spine + Pages Fanning Outward ---
        launch {
            spineAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 250, easing = LinearEasing)
            )
        }
        launch {
            bookOpenProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }

        delay(600)

        // --- PHASE 2 (600ms - 1100ms): 3 Laser-Thin Horizontal Scanning Light Pulses ---
        launch {
            scanAlpha.snapTo(1.0f)
            scanAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, delayMillis = 300, easing = LinearEasing)
            )
        }
        launch {
            scanProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
            )
        }

        delay(500) // At 1100ms

        // --- PHASE 3 (1100ms - 1600ms): Title Reveal with Letter-Spacing Expansion & Fade Out ---
        launch {
            titleAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
        launch {
            letterSpacingProgress.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }

        delay(350) // At 1450ms -> Begin smooth container transition

        launch {
            overallExitAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )
        }

        delay(150) // Exactly 1600ms total
        onAnimationFinished()
    }

    val currentLetterSpacing = (2.0f + (4.0f * letterSpacingProgress.value)).sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground)
            .graphicsLayer { alpha = overallExitAlpha.value },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Knowledge Monogram Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawKnowledgeMonogram(
                        spineAlpha = spineAlpha.value,
                        openProgress = bookOpenProgress.value,
                        scanProgress = scanProgress.value,
                        scanAlpha = scanAlpha.value
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // App Title "READMATE" (Phase 3 reveal)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                }
            ) {
                Text(
                    text = "READMATE",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = currentLetterSpacing,
                        color = SoftWhite
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "COGNITIVE CO-PILOT",
                    style = TextStyle(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        color = TextSubMuted
                    )
                )
            }
        }
    }
}

/**
 * Draws the minimalist geometric Knowledge Monogram book-leaf structure and scanning lasers.
 */
private fun DrawScope.drawKnowledgeMonogram(
    spineAlpha: Float,
    openProgress: Float,
    scanProgress: Float,
    scanAlpha: Float
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    val bookHeight = 70.dp.toPx()
    val maxHalfWidth = 44.dp.toPx()
    val currentHalfWidth = maxHalfWidth * openProgress

    val topY = centerY - bookHeight / 2f
    val bottomY = centerY + bookHeight / 2f

    // 1. Base Subtle Ambient Frame Ring
    drawCircle(
        color = HairlineZinc.copy(alpha = 0.5f),
        radius = 58.dp.toPx(),
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.dp.toPx())
    )

    // 2. Central Vertical Spine Hairline
    if (spineAlpha > 0f) {
        drawLine(
            color = PureWhite.copy(alpha = spineAlpha),
            start = Offset(centerX, topY - 4.dp.toPx()),
            end = Offset(centerX, bottomY + 4.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    if (openProgress > 0.02f) {
        val dipOffset = 8.dp.toPx() * openProgress
        val archCurve = 6.dp.toPx() * openProgress

        // --- LEFT PAGE LEAF ---
        val leftPath = Path().apply {
            moveTo(centerX, topY)
            cubicTo(
                centerX - currentHalfWidth * 0.4f, topY - archCurve,
                centerX - currentHalfWidth * 0.8f, topY - dipOffset * 0.3f,
                centerX - currentHalfWidth, topY + dipOffset
            )
            lineTo(centerX - currentHalfWidth, bottomY - dipOffset * 0.5f)
            cubicTo(
                centerX - currentHalfWidth * 0.8f, bottomY + dipOffset * 0.3f,
                centerX - currentHalfWidth * 0.4f, bottomY - archCurve * 0.5f,
                centerX, bottomY
            )
            close()
        }

        // Draw Left Page Outer Contour
        drawPath(
            path = leftPath,
            color = PureWhite.copy(alpha = 0.95f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Inner Left Rib / Sub-leaf Hairline
        val leftRibPath = Path().apply {
            moveTo(centerX, topY + 8.dp.toPx())
            cubicTo(
                centerX - currentHalfWidth * 0.35f, topY - archCurve * 0.2f + 8.dp.toPx(),
                centerX - currentHalfWidth * 0.65f, topY + 8.dp.toPx(),
                centerX - currentHalfWidth * 0.82f, topY + dipOffset + 4.dp.toPx()
            )
            lineTo(centerX - currentHalfWidth * 0.82f, bottomY - dipOffset * 0.5f - 4.dp.toPx())
        }
        drawPath(
            path = leftRibPath,
            color = HairlineMid.copy(alpha = 0.7f * openProgress),
            style = Stroke(width = 1.dp.toPx())
        )

        // --- RIGHT PAGE LEAF ---
        val rightPath = Path().apply {
            moveTo(centerX, topY)
            cubicTo(
                centerX + currentHalfWidth * 0.4f, topY - archCurve,
                centerX + currentHalfWidth * 0.8f, topY - dipOffset * 0.3f,
                centerX + currentHalfWidth, topY + dipOffset
            )
            lineTo(centerX + currentHalfWidth, bottomY - dipOffset * 0.5f)
            cubicTo(
                centerX + currentHalfWidth * 0.8f, bottomY + dipOffset * 0.3f,
                centerX + currentHalfWidth * 0.4f, bottomY - archCurve * 0.5f,
                centerX, bottomY
            )
            close()
        }

        // Draw Right Page Outer Contour
        drawPath(
            path = rightPath,
            color = PureWhite.copy(alpha = 0.95f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Inner Right Rib / Sub-leaf Hairline
        val rightRibPath = Path().apply {
            moveTo(centerX, topY + 8.dp.toPx())
            cubicTo(
                centerX + currentHalfWidth * 0.35f, topY - archCurve * 0.2f + 8.dp.toPx(),
                centerX + currentHalfWidth * 0.65f, topY + 8.dp.toPx(),
                centerX + currentHalfWidth * 0.82f, topY + dipOffset + 4.dp.toPx()
            )
            lineTo(centerX + currentHalfWidth * 0.82f, bottomY - dipOffset * 0.5f - 4.dp.toPx())
        }
        drawPath(
            path = rightRibPath,
            color = HairlineMid.copy(alpha = 0.7f * openProgress),
            style = Stroke(width = 1.dp.toPx())
        )

        // --- PHASE 2: 3 Scanning Laser Hairlines ---
        if (scanAlpha > 0f) {
            val scanStartY = topY + 4.dp.toPx()
            val scanRange = (bottomY - topY - 8.dp.toPx())
            val laserBaseY = scanStartY + (scanRange * scanProgress)

            val offsets = listOf(-8.dp.toPx(), 0f, 8.dp.toPx())
            val opacities = listOf(0.4f, 1.0f, 0.5f)

            for (i in offsets.indices) {
                val currentLaserY = laserBaseY + offsets[i]
                if (currentLaserY in topY..bottomY) {
                    val laserLeftX = centerX - (currentHalfWidth * 0.90f)
                    val laserRightX = centerX + (currentHalfWidth * 0.90f)

                    // Laser line
                    drawLine(
                        color = PureWhite.copy(alpha = scanAlpha * opacities[i]),
                        start = Offset(laserLeftX, currentLaserY),
                        end = Offset(laserRightX, currentLaserY),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Laser center micro-pulse
                    drawCircle(
                        color = PureWhite.copy(alpha = scanAlpha * opacities[i]),
                        radius = 1.5.dp.toPx(),
                        center = Offset(centerX, currentLaserY)
                    )
                }
            }
        }
    }
}
