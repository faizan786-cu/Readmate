package com.example.ui.components.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A custom high-performance Android View for rendering and interacting with a PDF page bitmap.
 *
 * Implements:
 * 1. Fluid Pinch-to-Zoom (1.0x to 4.5x) via [ScaleGestureDetector] with smooth matrix transforms.
 * 2. Multi-touch gesture conflict resolution:
 *    - `parent.requestDisallowInterceptTouchEvent(true)` on multi-touch (`pointerCount > 1`)
 *      so parent ViewPager/HorizontalPager never intercepts or freezes pinch midway.
 *    - `parent.requestDisallowInterceptTouchEvent(true)` when zoomed in (`scale > 1.05f`)
 *      and panning within page boundaries.
 *    - Allows parent horizontal page swipe when at scale 1.0x or reaching left/right edges.
 * 3. Double-tap to instantly zoom in (2.5x) or reset to fit (1.0x).
 */
@SuppressLint("ClickableViewAccessibility")
class ZoomablePdfPageTouchView(context: Context) : View(context) {

    private var pageBitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    // Current transformation state
    var currentScale: Float = 1.0f
        private set
    private var isSnipActive: Boolean = false

    private val minScale = 1.0f
    private val maxScale = 4.5f

    // Bounds of the rendered bitmap on screen
    private val bitmapRect = RectF()
    private val viewRect = RectF()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    var onCenterTap: (() -> Unit)? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                // Request parent to not intercept touch events during scaling
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                if (scaleFactor.isNaN() || scaleFactor.isInfinite()) return true

                val targetScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
                val deltaScale = targetScale / currentScale

                if (deltaScale != 1.0f) {
                    val focusX = detector.focusX
                    val focusY = detector.focusY

                    drawMatrix.postScale(deltaScale, deltaScale, focusX, focusY)
                    currentScale = targetScale
                    checkAndAdjustBounds()
                    invalidate()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (currentScale < 1.05f) {
                    resetZoomAnimated()
                }
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isSnipActive) return false
                if (currentScale > 1.1f) {
                    resetZoomAnimated()
                } else {
                    zoomToAnimated(targetScale = 2.5f, focusX = e.x, focusY = e.y)
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isSnipActive) return false
                val viewW = width.toFloat()
                val viewH = height.toFloat()
                if (viewW <= 0f || viewH <= 0f) return false

                // Center area detection (middle region between 15%-85% horizontal, 10%-90% vertical)
                val isCenterArea = e.x in (viewW * 0.15f)..(viewW * 0.85f) &&
                        e.y in (viewH * 0.10f)..(viewH * 0.90f)

                if (isCenterArea) {
                    onCenterTap?.invoke()
                    return true
                }
                return false
            }
        })
    }

    fun setPageBitmap(bitmap: Bitmap?, isSnipMode: Boolean) {
        this.pageBitmap = bitmap
        this.isSnipActive = isSnipMode
        if (isSnipMode) {
            resetZoomImmediate()
        } else {
            fitBitmapToView()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewRect.set(0f, 0f, w.toFloat(), h.toFloat())
        fitBitmapToView()
    }

    private fun fitBitmapToView() {
        val bmp = pageBitmap ?: return
        if (width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = min(viewW / bmpW, viewH / bmpH)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f

        drawMatrix.reset()
        drawMatrix.postScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)

        currentScale = 1.0f
        checkAndAdjustBounds()
    }

    private fun zoomToAnimated(targetScale: Float, focusX: Float, focusY: Float) {
        val factor = targetScale / currentScale
        drawMatrix.postScale(factor, factor, focusX, focusY)
        currentScale = targetScale
        checkAndAdjustBounds()
        invalidate()
    }

    private fun resetZoomAnimated() {
        fitBitmapToView()
        invalidate()
    }

    fun resetZoomImmediate() {
        fitBitmapToView()
        invalidate()
    }

    private fun checkAndAdjustBounds() {
        val bmp = pageBitmap ?: return
        if (width <= 0 || height <= 0) return

        bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        drawMatrix.mapRect(bitmapRect)

        var deltaX = 0f
        var deltaY = 0f

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // Center or constrain horizontally
        if (bitmapRect.width() <= viewW) {
            deltaX = (viewW - bitmapRect.width()) / 2f - bitmapRect.left
        } else {
            if (bitmapRect.left > 0f) {
                deltaX = -bitmapRect.left
            } else if (bitmapRect.right < viewW) {
                deltaX = viewW - bitmapRect.right
            }
        }

        // Center or constrain vertically
        if (bitmapRect.height() <= viewH) {
            deltaY = (viewH - bitmapRect.height()) / 2f - bitmapRect.top
        } else {
            if (bitmapRect.top > 0f) {
                deltaY = -bitmapRect.top
            } else if (bitmapRect.bottom < viewH) {
                deltaY = viewH - bitmapRect.bottom
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            drawMatrix.postTranslate(deltaX, deltaY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageBitmap == null || isSnipActive) {
            return super.onTouchEvent(event)
        }

        // 1. Double tap & scale detection
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled

        val pointerCount = event.pointerCount

        // 2. CRITICAL TOUCH DISALLOW LOGIC:
        // If 2 or more fingers are on screen (user is pinching), NEVER let parent ViewPager intercept!
        if (pointerCount > 1) {
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (currentScale > 1.05f) {
            // When zoomed in, disallow parent interception unless at horizontal boundaries
            val bmp = pageBitmap
            if (bmp != null) {
                bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                drawMatrix.mapRect(bitmapRect)

                val viewW = width.toFloat()
                val isAtLeftEdge = bitmapRect.left >= -1f
                val isAtRightEdge = bitmapRect.right <= viewW + 1f

                // If user is panning inside the zoomed image, lock touch to this view
                parent?.requestDisallowInterceptTouchEvent(!isAtLeftEdge || !isAtRightEdge)
            } else {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        } else {
            // At 1.0x normal zoom scale with 1 finger, allow parent horizontal swiping
            parent?.requestDisallowInterceptTouchEvent(false)
        }

        // 3. Single-finger pan when zoomed in
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                if (currentScale > 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 1 && isDragging && currentScale > 1.05f) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    lastTouchX = event.x
                    lastTouchY = event.y

                    drawMatrix.postTranslate(dx, dy)
                    checkAndAdjustBounds()
                    invalidate()
                    handled = true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Adjust last touch position to the remaining pointer
                val upIndex = event.actionIndex
                val remainingIndex = if (upIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return handled || currentScale > 1.05f || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = pageBitmap ?: return
        if (bmp.isRecycled) return

        canvas.drawBitmap(bmp, drawMatrix, null)
    }
}
