package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// Strict Monochrome Obsidian Palette
private val EmblemPedestal = Color(0xFF141418)
private val EmblemBorder = Color(0xFF27272F)
private val EmblemGlyphWhite = Color(0xFFFFFFFF)

/**
 * Standardized ReadMate Brand Asset.
 * Single source of truth for the ReadMate brand mark across all screens.
 *
 * The Emblem:
 * An executive, high-contrast monochrome glyph representing focus and deep synthesis:
 * A geometric modern open book fused with a precision focal aperture, rendered in crisp solid
 * #FFFFFF on an Obsidian pedestal #141418 with a 1dp #27272F border.
 *
 * The Wordmark:
 * "ReadMate" styled strictly with clean tracking (letter-spacing: 0.5sp, bold, #FFFFFF).
 */
@Composable
fun ReadMateBrandLogo(
    size: Dp = 48.dp,
    showWordmark: Boolean = false,
    modifier: Modifier = Modifier,
    wordmarkFontSize: TextUnit = (size.value * 0.32f).coerceIn(12f, 24f).sp,
    isRowLayout: Boolean = false
) {
    if (showWordmark) {
        if (isRowLayout) {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((size.value * 0.25f).coerceAtLeast(8f).dp)
            ) {
                ReadMateEmblem(size = size)
                Text(
                    text = "ReadMate",
                    fontSize = wordmarkFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = EmblemGlyphWhite
                )
            }
        } else {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ReadMateEmblem(size = size)
                Spacer(modifier = Modifier.height((size.value * 0.18f).coerceAtLeast(6f).dp))
                Text(
                    text = "ReadMate",
                    fontSize = wordmarkFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = EmblemGlyphWhite
                )
            }
        }
    } else {
        ReadMateEmblem(
            size = size,
            modifier = modifier
        )
    }
}

/**
 * The executive monochrome Emblem container on an Obsidian pedestal.
 */
@Composable
fun ReadMateEmblem(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = (size.value * 0.24f).coerceIn(6f, 20f).dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = EmblemPedestal,
        border = BorderStroke(1.dp, EmblemBorder)
    ) {
        Box(
            modifier = Modifier.padding((size.value * 0.18f).dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size((size.value * 0.64f).dp)) {
                drawBrandGlyph()
            }
        }
    }
}

/**
 * Precision geometric vector glyph: Open book fused with a focal aperture.
 */
private fun DrawScope.drawBrandGlyph() {
    val w = size.width
    val h = size.height
    val centerX = w / 2f
    val centerY = h / 2f
    val strokeWidth = (w * 0.075f).coerceIn(1.5f, 3.8f)

    // 1. Central Vertical Spine Hairline
    drawLine(
        color = EmblemGlyphWhite,
        start = Offset(centerX, h * 0.14f),
        end = Offset(centerX, h * 0.86f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 2. Left Book Page Contour
    val leftLeaf = Path().apply {
        moveTo(centerX, h * 0.16f)
        cubicTo(
            centerX - w * 0.22f, h * 0.10f,
            centerX - w * 0.44f, h * 0.18f,
            centerX - w * 0.46f, h * 0.32f
        )
        lineTo(centerX - w * 0.46f, h * 0.72f)
        cubicTo(
            centerX - w * 0.44f, h * 0.84f,
            centerX - w * 0.22f, h * 0.78f,
            centerX, h * 0.84f
        )
    }
    drawPath(
        path = leftLeaf,
        color = EmblemGlyphWhite,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // 3. Right Book Page Contour
    val rightLeaf = Path().apply {
        moveTo(centerX, h * 0.16f)
        cubicTo(
            centerX + w * 0.22f, h * 0.10f,
            centerX + w * 0.44f, h * 0.18f,
            centerX + w * 0.46f, h * 0.32f
        )
        lineTo(centerX + w * 0.46f, h * 0.72f)
        cubicTo(
            centerX + w * 0.44f, h * 0.84f,
            centerX + w * 0.22f, h * 0.78f,
            centerX, h * 0.84f
        )
    }
    drawPath(
        path = rightLeaf,
        color = EmblemGlyphWhite,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // 4. Precision Focal Aperture Ring (Deep Synthesis)
    val apertureRadius = w * 0.25f
    drawCircle(
        color = EmblemGlyphWhite,
        radius = apertureRadius,
        center = Offset(centerX, centerY),
        style = Stroke(width = strokeWidth)
    )

    // 5. Focal Aperture Reticle / Synthesis Cross Blades
    val bladeRadius = apertureRadius * 0.88f
    val innerRadius = apertureRadius * 0.35f
    for (i in 0 until 4) {
        val angle = (i * 90f + 45f) * (Math.PI / 180f)
        val startX = centerX + bladeRadius * cos(angle).toFloat()
        val startY = centerY + bladeRadius * sin(angle).toFloat()
        val endX = centerX + innerRadius * cos(angle).toFloat()
        val endY = centerY + innerRadius * sin(angle).toFloat()
        drawLine(
            color = EmblemGlyphWhite,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth * 0.85f,
            cap = StrokeCap.Round
        )
    }

    // 6. Central Focal Synthesis Iris Core
    drawCircle(
        color = EmblemGlyphWhite,
        radius = (w * 0.06f).coerceIn(1.2f, 3.2f),
        center = Offset(centerX, centerY)
    )
}
