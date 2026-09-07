package com.example.ui.components.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PdfPageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

object PdfPageRendererHelper {
    private val cache = PdfPageCache()
    private val renderMutex = Mutex()

    suspend fun renderPageBitmap(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null
        val cacheKey = "${filePath}_${pageIndex}_${targetWidthPx}_${targetHeightPx}"
        val cached = cache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() <= 0) return@withContext null

        renderMutex.withLock {
            // Check cache again inside lock to avoid redundant rendering
            val cachedInLock = cache.get(cacheKey)
            if (cachedInLock != null && !cachedInLock.isRecycled) {
                return@withContext cachedInLock
            }

            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null

            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                if (pfd == null) return@withContext null
                renderer = PdfRenderer(pfd)
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                page = renderer.openPage(pageIndex)

                // Maintain aspect ratio while rendering sharply
                val pageAspect = (page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)).coerceIn(0.1f, 10f)
                val finalWidth: Int
                val finalHeight: Int

                if (targetWidthPx > 0 && targetHeightPx > 0) {
                    val containerAspect = targetWidthPx.toFloat() / targetHeightPx.toFloat()
                    if (pageAspect > containerAspect) {
                        finalWidth = targetWidthPx
                        finalHeight = (targetWidthPx / pageAspect).roundToInt()
                    } else {
                        finalHeight = targetHeightPx
                        finalWidth = (targetHeightPx * pageAspect).roundToInt()
                    }
                } else {
                    finalWidth = (page.width * 2).coerceAtLeast(300)
                    finalHeight = (page.height * 2).coerceAtLeast(400)
                }

                val safeW = finalWidth.coerceIn(100, 3840)
                val safeH = finalHeight.coerceIn(100, 3840)

                val bitmap = Bitmap.createBitmap(
                    safeW,
                    safeH,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(cacheKey, bitmap)
                bitmap
            } catch (t: Throwable) {
                null
            } finally {
                try { page?.close() } catch (_: Throwable) {}
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }
    }
}

@Composable
fun ZoomablePdfPageView(
    filePath: String,
    pageIndex: Int,
    isSnipActive: Boolean,
    onCenterTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(filePath, pageIndex) { mutableStateOf(true) }
    var loadError by remember(filePath, pageIndex) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFFE2E2E5)),
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(filePath, pageIndex) {
            isLoading = true
            loadError = false
            val rendered = PdfPageRendererHelper.renderPageBitmap(
                filePath = filePath,
                pageIndex = pageIndex,
                targetWidthPx = 1440,
                targetHeightPx = 2560
            )
            if (rendered != null) {
                bitmap = rendered
                isLoading = false
            } else {
                loadError = true
                isLoading = false
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        } else if (loadError || bitmap == null) {
            Text(
                text = "Could not render Page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val bmp = bitmap!!

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ZoomablePdfPageTouchView(ctx).apply {
                        this.onCenterTap = onCenterTap
                        setPageBitmap(bmp, isSnipActive)
                    }
                },
                update = { view ->
                    view.onCenterTap = onCenterTap
                    view.setPageBitmap(bmp, isSnipActive)
                }
            )
        }
    }
}
