package com.example.data.pdf

import android.graphics.RectF
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Shared in-memory bounded LRU cache for OCR and page transcription results.
 * Bounded strictly to 100 entries to prevent memory growth while eliminating
 * duplicate Vision calls across repeated book import and passage selection workflows.
 */
object OcrSnippetCache {
    private val cache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > 100
            }
        }
    )

    fun get(key: String): String? = cache[key]

    fun put(key: String, value: String) {
        cache[key] = value
    }

    fun size(): Int = cache.size

    fun clear() {
        cache.clear()
    }

    fun makeCropKey(
        pdfFilePath: String,
        pageIndex: Int,
        cropRectNormalized: RectF,
        purpose: String
    ): String {
        return "$pdfFilePath:$pageIndex:${(cropRectNormalized.left * 1000).toInt()}:${(cropRectNormalized.top * 1000).toInt()}:${(cropRectNormalized.right * 1000).toInt()}:${(cropRectNormalized.bottom * 1000).toInt()}:$purpose"
    }

    fun makePageKey(
        pdfFilePath: String,
        pageIndex: Int,
        purpose: String = "PDF_STRUCTURE_FRONT_MATTER"
    ): String {
        return "$pdfFilePath:$pageIndex:0:0:1000:1000:$purpose"
    }
}
