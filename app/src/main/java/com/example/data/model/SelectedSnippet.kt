package com.example.data.model

import android.graphics.RectF

/**
 * Data model representing a snipped page region in single or multi-page selection.
 * Preserves physical page number, selection order, cropped coordinates, and extracted text.
 */
data class SelectedSnippet(
    val pageIndex: Int,
    val cropRect: RectF,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val extractedText: String? = null,
    val selectionOrder: Int = 0
) {
    val physicalPageNumber: Int get() = pageIndex + 1
}
