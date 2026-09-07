package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

object PdfCropUtils {

    /**
     * Renders a normalized crop region of a PDF page at crisp resolution
     * using [PdfRenderer], compresses the cropped bitmap to a clean JPEG,
     * and returns the Base64 encoded string.
     *
     * [viewWidth] and [viewHeight] are the pixel dimensions of the on-screen container
     * displaying the PDF page with ContentScale.Fit. When provided, coordinate mapping
     * precisely subtracts letterboxing/pillarboxing insets and scales directly to the
     * rendered bitmap canvas with ZERO vertical/horizontal drift.
     */
    suspend fun renderAndCropPageToBase64(
        pdfFilePath: String,
        pageIndex: Int,
        cropRectNormalized: RectF,
        viewWidth: Float = 0f,
        viewHeight: Float = 0f,
        scaleFactor: Float = 2.5f,
        jpegQuality: Int = 85
    ): Result<String> = withContext(Dispatchers.IO) {
        val file = File(pdfFilePath)
        if (!file.exists()) {
            return@withContext Result.failure(Exception("PDF file not found at $pdfFilePath."))
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var fullBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return@withContext Result.failure(Exception("Invalid page index $pageIndex. Total pages: ${renderer.pageCount}"))
            }

            page = renderer.openPage(pageIndex)

            // High resolution render for crystal-clear vector rasterization
            val renderWidth = (page.width * scaleFactor).roundToInt().coerceAtLeast(1000)
            val renderHeight = (page.height * scaleFactor).roundToInt().coerceAtLeast(1400)

            fullBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            fullBitmap.eraseColor(Color.WHITE) // Clean solid white background

            page.render(fullBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val cropX: Int
            val cropY: Int
            val cropW: Int
            val cropH: Int

            if (viewWidth > 0f && viewHeight > 0f) {
                // 1. Calculate the precise drawn bounds of the PDF Page on screen under ContentScale.Fit
                val pageAspect = page.width.toFloat() / page.height.toFloat()
                val viewAspect = viewWidth / viewHeight

                val drawnWidth: Float
                val drawnHeight: Float
                val drawnLeft: Float
                val drawnTop: Float

                if (pageAspect > viewAspect) {
                    // Page is wider than view -> fitted to width, letterboxed top & bottom
                    drawnWidth = viewWidth
                    drawnHeight = viewWidth / pageAspect
                    drawnLeft = 0f
                    drawnTop = (viewHeight - drawnHeight) / 2f
                } else {
                    // Page is taller than view -> fitted to height, letterboxed left & right
                    drawnHeight = viewHeight
                    drawnWidth = viewHeight * pageAspect
                    drawnLeft = (viewWidth - drawnWidth) / 2f
                    drawnTop = 0f
                }

                // 2. Convert normalized crop box coordinates [0..1] to view pixel space
                val cropBoxLeft = cropRectNormalized.left * viewWidth
                val cropBoxTop = cropRectNormalized.top * viewHeight
                val cropBoxRight = cropRectNormalized.right * viewWidth
                val cropBoxBottom = cropRectNormalized.bottom * viewHeight

                // 3. Subtract Absolute Insets (drawnLeft / drawnTop) relative ONLY to the actual drawn PDF canvas
                val relativeLeft = (cropBoxLeft - drawnLeft).coerceIn(0f, drawnWidth)
                val relativeTop = (cropBoxTop - drawnTop).coerceIn(0f, drawnHeight)
                val relativeRight = (cropBoxRight - drawnLeft).coerceIn(0f, drawnWidth)
                val relativeBottom = (cropBoxBottom - drawnTop).coerceIn(0f, drawnHeight)

                // 4. Scale to Full-Resolution Bitmap
                val scaleX = renderWidth.toFloat() / drawnWidth
                val scaleY = renderHeight.toFloat() / drawnHeight

                cropX = (relativeLeft * scaleX).roundToInt().coerceIn(0, renderWidth - 1)
                cropY = (relativeTop * scaleY).roundToInt().coerceIn(0, renderHeight - 1)
                cropW = ((relativeRight - relativeLeft) * scaleX).roundToInt().coerceIn(1, renderWidth - cropX)
                cropH = ((relativeBottom - relativeTop) * scaleY).roundToInt().coerceIn(1, renderHeight - cropY)
            } else {
                // Direct coordinate mapping fallback
                val normLeft = cropRectNormalized.left.coerceIn(0f, 1f)
                val normTop = cropRectNormalized.top.coerceIn(0f, 1f)
                val normRight = cropRectNormalized.right.coerceIn(0f, 1f)
                val normBottom = cropRectNormalized.bottom.coerceIn(0f, 1f)

                cropX = (normLeft * renderWidth).roundToInt().coerceIn(0, renderWidth - 1)
                cropY = (normTop * renderHeight).roundToInt().coerceIn(0, renderHeight - 1)
                cropW = ((normRight - normLeft) * renderWidth).roundToInt().coerceIn(1, renderWidth - cropX)
                cropH = ((normBottom - normTop) * renderHeight).roundToInt().coerceIn(1, renderHeight - cropY)
            }

            croppedBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)

            val outputStream = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            Result.success(base64)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                if (croppedBitmap != null && !croppedBitmap.isRecycled) {
                    croppedBitmap.recycle()
                }
            } catch (_: Exception) { }

            try {
                if (fullBitmap != null && !fullBitmap.isRecycled) {
                    fullBitmap.recycle()
                }
            } catch (_: Exception) { }

            try {
                page?.close()
            } catch (_: Exception) { }

            try {
                renderer?.close()
            } catch (_: Exception) { }

            try {
                pfd?.close()
            } catch (_: Exception) { }
        }
    }
}
