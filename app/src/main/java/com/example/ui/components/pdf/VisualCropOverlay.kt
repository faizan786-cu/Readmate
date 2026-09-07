package com.example.ui.components.pdf

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT, CENTER
}

@Composable
fun VisualCropOverlay(
    isMultiPageSecondStep: Boolean,
    part1WordCount: Int,
    isProcessingOcr: Boolean,
    onExplainSinglePage: (cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onCapturePart1AndNext: (cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onMergeAndExplain: (cropRect: RectF, viewWidth: Float, viewHeight: Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Relative crop coordinates in [0f..1f] relative to container
    var cropLeft by remember { mutableFloatStateOf(0.08f) }
    var cropTop by remember { mutableFloatStateOf(0.18f) }
    var cropRight by remember { mutableFloatStateOf(0.92f) }
    var cropBottom by remember { mutableFloatStateOf(0.48f) }

    // Dynamic drag and touch tracking for instant auto-hide of action bar
    var isInteracting by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var actionBarBounds by remember { mutableStateOf<Rect?>(null) }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("visual_crop_overlay")
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(100f)

        // Pixel coordinates of crop box computed dynamically
        val leftPx = cropLeft * containerWidthPx
        val topPx = cropTop * containerHeightPx
        val rightPx = cropRight * containerWidthPx
        val bottomPx = cropBottom * containerHeightPx
        val boxWidthPx = (rightPx - leftPx).coerceAtLeast(40f)
        val boxHeightPx = (bottomPx - topPx).coerceAtLeast(40f)

        val minBoxSizePx = with(density) { 48.dp.toPx() }
        val handleTouchRadiusPx = with(density) { 34.dp.toPx() } // Generous 68dp touch target hit radius

        // 1. Gesture Tracking & Canvas Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(containerWidthPx, containerHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchX = down.position.x
                        val touchY = down.position.y

                        // If user touched the floating action bar itself, let action bar buttons receive click
                        if (actionBarBounds?.contains(Offset(touchX, touchY)) == true) {
                            return@awaitEachGesture
                        }

                        val currentLeftPx = cropLeft * containerWidthPx
                        val currentTopPx = cropTop * containerHeightPx
                        val currentRightPx = cropRight * containerWidthPx
                        val currentBottomPx = cropBottom * containerHeightPx

                        // Check corner handles first with generous touch radius
                        val dTL = Offset(touchX - currentLeftPx, touchY - currentTopPx).getDistance()
                        val dTR = Offset(touchX - currentRightPx, touchY - currentTopPx).getDistance()
                        val dBL = Offset(touchX - currentLeftPx, touchY - currentBottomPx).getDistance()
                        val dBR = Offset(touchX - currentRightPx, touchY - currentBottomPx).getDistance()

                        val hitHandle = when {
                            dTL <= handleTouchRadiusPx -> DragHandle.TOP_LEFT
                            dTR <= handleTouchRadiusPx -> DragHandle.TOP_RIGHT
                            dBL <= handleTouchRadiusPx -> DragHandle.BOTTOM_LEFT
                            dBR <= handleTouchRadiusPx -> DragHandle.BOTTOM_RIGHT
                            abs(touchY - currentTopPx) <= handleTouchRadiusPx && touchX in currentLeftPx..currentRightPx -> DragHandle.EDGE_TOP
                            abs(touchY - currentBottomPx) <= handleTouchRadiusPx && touchX in currentLeftPx..currentRightPx -> DragHandle.EDGE_BOTTOM
                            abs(touchX - currentLeftPx) <= handleTouchRadiusPx && touchY in currentTopPx..currentBottomPx -> DragHandle.EDGE_LEFT
                            abs(touchX - currentRightPx) <= handleTouchRadiusPx && touchY in currentTopPx..currentBottomPx -> DragHandle.EDGE_RIGHT
                            touchX in currentLeftPx..currentRightPx && touchY in currentTopPx..currentBottomPx -> DragHandle.CENTER
                            else -> DragHandle.NONE
                        }

                        if (hitHandle != DragHandle.NONE) {
                            down.consume()
                            activeHandle = hitHandle
                            isInteracting = true

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        break
                                    }
                                    val dragAmount = change.positionChange()
                                    if (dragAmount != Offset.Zero) {
                                        change.consume()
                                        val dxNorm = dragAmount.x / containerWidthPx
                                        val dyNorm = dragAmount.y / containerHeightPx
                                        val minNormW = minBoxSizePx / containerWidthPx
                                        val minNormH = minBoxSizePx / containerHeightPx

                                        when (hitHandle) {
                                            DragHandle.TOP_LEFT -> {
                                                cropLeft = (cropLeft + dxNorm).coerceIn(0.01f, cropRight - minNormW)
                                                cropTop = (cropTop + dyNorm).coerceIn(0.01f, cropBottom - minNormH)
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minNormW, 0.99f)
                                                cropTop = (cropTop + dyNorm).coerceIn(0.01f, cropBottom - minNormH)
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                cropLeft = (cropLeft + dxNorm).coerceIn(0.01f, cropRight - minNormW)
                                                cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minNormH, 0.99f)
                                            }
                                            DragHandle.BOTTOM_RIGHT -> {
                                                cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minNormW, 0.99f)
                                                cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minNormH, 0.99f)
                                            }
                                            DragHandle.EDGE_TOP -> {
                                                cropTop = (cropTop + dyNorm).coerceIn(0.01f, cropBottom - minNormH)
                                            }
                                            DragHandle.EDGE_BOTTOM -> {
                                                cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minNormH, 0.99f)
                                            }
                                            DragHandle.EDGE_LEFT -> {
                                                cropLeft = (cropLeft + dxNorm).coerceIn(0.01f, cropRight - minNormW)
                                            }
                                            DragHandle.EDGE_RIGHT -> {
                                                cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minNormW, 0.99f)
                                            }
                                            DragHandle.CENTER -> {
                                                val currentW = cropRight - cropLeft
                                                val currentH = cropBottom - cropTop

                                                var newLeft = cropLeft + dxNorm
                                                var newTop = cropTop + dyNorm

                                                if (newLeft < 0.01f) newLeft = 0.01f
                                                if (newLeft + currentW > 0.99f) newLeft = 0.99f - currentW

                                                if (newTop < 0.01f) newTop = 0.01f
                                                if (newTop + currentH > 0.99f) newTop = 0.99f - currentH

                                                cropLeft = newLeft
                                                cropRight = newLeft + currentW
                                                cropTop = newTop
                                                cropBottom = newTop + currentH
                                            }
                                            DragHandle.NONE -> {}
                                        }
                                    }
                                }
                            } finally {
                                activeHandle = DragHandle.NONE
                                isInteracting = false
                            }
                        }
                    }
                }
        ) {
            // Semi-transparent dark canvas with 100% transparent cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shadeColor = Color(0x66000000)

                // Top outer region
                if (topPx > 0f) {
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, topPx)
                    )
                }
                // Bottom outer region
                if (bottomPx < size.height) {
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(0f, bottomPx),
                        size = Size(size.width, size.height - bottomPx)
                    )
                }
                // Left outer region
                if (leftPx > 0f) {
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(0f, topPx),
                        size = Size(leftPx, boxHeightPx)
                    )
                }
                // Right outer region
                if (rightPx < size.width) {
                    drawRect(
                        color = shadeColor,
                        topLeft = Offset(rightPx, topPx),
                        size = Size(size.width - rightPx, boxHeightPx)
                    )
                }

                // Dual-tone high-contrast bounding box border:
                // Outer dark contour (contrast on light pages)
                drawRect(
                    color = Color(0xFF0B0B0E),
                    topLeft = Offset(leftPx - 0.5f, topPx - 0.5f),
                    size = Size(boxWidthPx + 1f, boxHeightPx + 1f),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                // Core pure white border (contrast on dark documents)
                drawRect(
                    color = Color.White,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(boxWidthPx, boxHeightPx),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Grid rule-of-thirds lines inside selection for alignment guidance
                val thirdW = boxWidthPx / 3f
                val thirdH = boxHeightPx / 3f
                val gridColor = Color.White.copy(alpha = 0.35f)
                drawLine(gridColor, Offset(leftPx + thirdW, topPx), Offset(leftPx + thirdW, bottomPx), 1.dp.toPx())
                drawLine(gridColor, Offset(leftPx + 2 * thirdW, topPx), Offset(leftPx + 2 * thirdW, bottomPx), 1.dp.toPx())
                drawLine(gridColor, Offset(leftPx, topPx + thirdH), Offset(rightPx, topPx + thirdH), 1.dp.toPx())
                drawLine(gridColor, Offset(leftPx, topPx + 2 * thirdH), Offset(rightPx, topPx + 2 * thirdH), 1.dp.toPx())
            }

            // 4 Corner circle handles with tactile hit target
            CornerHandleWidget(leftPx, topPx, activeHandle == DragHandle.TOP_LEFT)
            CornerHandleWidget(rightPx, topPx, activeHandle == DragHandle.TOP_RIGHT)
            CornerHandleWidget(leftPx, bottomPx, activeHandle == DragHandle.BOTTOM_LEFT)
            CornerHandleWidget(rightPx, bottomPx, activeHandle == DragHandle.BOTTOM_RIGHT)
        }

        // 2. Floating Compact Action Bar Pill (Auto-hides during touch/drag)
        AnimatedVisibility(
            visible = !isInteracting,
            enter = slideInVertically(animationSpec = tween(220, easing = EaseOutCubic)) { it } + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(animationSpec = tween(130, easing = FastOutLinearInEasing)) { it } + fadeOut(animationSpec = tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, Color(0xFF27272F)),
                shadowElevation = 12.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        actionBarBounds = coordinates.boundsInParent()
                    }
                    .testTag("crop_actions_container")
            ) {
                val currentRect = RectF(cropLeft, cropTop, cropRight, cropBottom)

                if (!isMultiPageSecondStep) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            // Action 1: Cancel Button (Compact dark pill)
                            Surface(
                                onClick = onCancel,
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E1E24),
                                border = BorderStroke(1.dp, Color(0xFF2E2E38)),
                                modifier = Modifier.testTag("crop_cancel_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel snip",
                                        tint = Color(0xFFD4D4D8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFD4D4D8)
                                    )
                                }
                            }

                            // Action 2: Snip Next Page / Combine (Mid-tier dark pill)
                            Surface(
                                onClick = { onCapturePart1AndNext(currentRect, containerWidthPx, containerHeightPx) },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF24242C),
                                border = BorderStroke(1.dp, Color(0xFF383846)),
                                modifier = Modifier.testTag("crop_snip_next_page_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Snip next page",
                                        tint = Color(0xFFF4F4F5),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Next Page",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFF4F4F5)
                                    )
                                }
                            }

                            // Action 3: Explain Now (High-contrast primary white pill)
                            Surface(
                                onClick = { onExplainSinglePage(currentRect, containerWidthPx, containerHeightPx) },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 2.dp,
                                modifier = Modifier.testTag("crop_explain_now_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Explain now",
                                        tint = Color(0xFF0B0B0E),
                                        modifier = Modifier.size(13.5.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.5.dp))
                                    Text(
                                        text = "Explain Now",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = (-0.1).sp
                                        ),
                                        color = Color(0xFF0B0B0E)
                                    )
                                }
                            }
                        }
                    } else {
                        // Multi-page step 2: Merge & Explain
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onCancel,
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E1E24),
                                border = BorderStroke(1.dp, Color(0xFF2E2E38)),
                                modifier = Modifier.testTag("crop_merge_cancel_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = Color(0xFFD4D4D8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFD4D4D8)
                                    )
                                }
                            }

                            Surface(
                                onClick = { onMergeAndExplain(currentRect, containerWidthPx, containerHeightPx) },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 2.dp,
                                modifier = Modifier.testTag("crop_merge_and_explain_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Merge,
                                        contentDescription = "Merge and explain",
                                        tint = Color(0xFF0B0B0E),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Merge & Explain",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFF0B0B0E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun CornerHandleWidget(
    centerXPx: Float,
    centerYPx: Float,
    isDragging: Boolean = false
) {
    val handleDiameterDp = 20.dp
    val density = LocalDensity.current
    val offsetXPx = centerXPx - with(density) { (handleDiameterDp / 2).toPx() }
    val offsetYPx = centerYPx - with(density) { (handleDiameterDp / 2).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
            .size(handleDiameterDp)
            .shadow(elevation = if (isDragging) 4.dp else 2.dp, shape = CircleShape)
            .background(Color.White, CircleShape)
            .border(2.5.dp, Color(0xFF0B0B0E), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(5.5.dp)
                .background(Color(0xFF0B0B0E), CircleShape)
        )
    }
}


