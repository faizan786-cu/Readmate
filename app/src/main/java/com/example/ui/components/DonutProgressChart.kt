package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ultra-sleek, multi-layered circular donut progress chart with gradient sweep and bold percentage display.
 *
 * @param progress Fraction of completion between 0.0f and 1.0f.
 * @param size Overall diameter of the chart.
 * @param strokeWidth Thickness of the donut track and progress ring.
 * @param progressColors List of colors for the vibrant gradient sweep (defaults to Ruby/Crimson).
 * @param trackColor Deep background ring color.
 * @param textColor Color for the central percentage text.
 * @param textStyle Style for the central percentage label.
 * @param showPercentageText Whether to show the percentage text inside the donut.
 */
@Composable
fun DonutProgressChart(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.5.dp,
    progressColor: Color = Color(0xFFFF1744),
    progressColors: List<Color> = listOf(
        Color(0xFFFF1744), // Vibrant Crimson
        Color(0xFFE53935), // Deep Ruby
        Color(0xFFFF5252)  // Bright Coral
    ),
    trackColor: Color = Color(0xFF1E2430),
    textColor: Color = Color(0xFFF8FAFC),
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp
    ),
    showPercentageText: Boolean = true,
    testTag: String = "donut_progress_chart"
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "DonutProgressAnimation"
    )

    val percentageInt = (animatedProgress * 100).toInt().coerceIn(0, 100)

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = size.toPx() - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // 1. Subtle Outer Glow / Shadow Track
            drawArc(
                color = trackColor.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokePx / 2f - 1f, strokePx / 2f - 1f),
                size = Size(arcSize + 2f, arcSize + 2f),
                style = Stroke(width = strokePx + 2f, cap = StrokeCap.Round)
            )

            // 2. Deep Dark Background Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 3. Active Progress Arc with Radiant Gradient Sweep
            if (animatedProgress > 0f) {
                val gradientBrush = if (progressColors.size > 1) {
                    Brush.sweepGradient(
                        colors = progressColors,
                        center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    )
                } else {
                    Brush.linearGradient(listOf(progressColor, progressColor))
                }

                drawArc(
                    brush = gradientBrush,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        if (showPercentageText) {
            Text(
                text = "$percentageInt%",
                style = textStyle,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

