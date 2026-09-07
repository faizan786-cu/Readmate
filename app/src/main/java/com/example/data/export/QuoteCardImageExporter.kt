package com.example.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.database.entity.WisdomQuote
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QuoteCardImageExporter {

    private const val TAG = "QuoteCardImageExporter"
    private const val IMAGE_WIDTH = 1080
    private const val IMAGE_HEIGHT = 1620

    // Strict Monochrome Palette
    private const val COLOR_OBSIDIAN_BG = 0xFF0D0D11.toInt()
    private const val COLOR_CARD_SURFACE = 0xFF121215.toInt()
    private const val COLOR_ZINC_BORDER = 0xFF27272A.toInt()
    private const val COLOR_WATERMARK = 0x3327272A.toInt()
    private const val COLOR_PURE_WHITE = 0xFFFFFFFF.toInt()
    private const val COLOR_SILVER_URDU = 0xFFE4E4E7.toInt()
    private const val COLOR_MUTED_META = 0xFFA1A1AA.toInt()
    private const val COLOR_SUBTLE_BRAND = 0xFF52525B.toInt()

    /**
     * Saves an exact captured Bitmap (from Compose GraphicsLayer snapshot) directly to the device's public Pictures/ReadMate gallery.
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        quote: WisdomQuote,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            val fileName = "ReadMate_Wisdom_${quote.id}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ReadMate")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to insert MediaStore image record.")

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                } ?: throw IllegalStateException("Failed to open output stream for gallery save.")

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "Exact bitmap successfully saved to Pictures/ReadMate: $uri")
                Result.success(uri)
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val readMateDir = File(picturesDir, "ReadMate")
                if (!readMateDir.exists()) {
                    readMateDir.mkdirs()
                }
                val destFile = File(readMateDir, fileName)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                val fileUri = Uri.fromFile(destFile)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                Result.success(fileUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save captured bitmap to Gallery: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Shares an exact captured Bitmap (from Compose GraphicsLayer snapshot) using system share sheet.
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        quote: WisdomQuote,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            val file = saveBitmapToCache(context, bitmap, quote.id)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                val shareText = buildString {
                    append("\"${quote.englishQuote}\"\n\n")
                    if (quote.romanUrduPunchline.isNotBlank()) {
                        append("“${quote.romanUrduPunchline}”\n\n")
                    }
                    val author = quote.author?.takeIf { it.isNotBlank() } ?: "ReadMate Wisdom"
                    append("— $author, ${quote.bookTitle} (Ch. ${quote.chapterNumber})")
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Wisdom Quote").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share captured bitmap: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Renders and saves the quote card directly into the device's public Pictures/ReadMate gallery.
     */
    suspend fun saveQuoteToGallery(
        context: Context,
        quote: WisdomQuote,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            val bitmap = renderQuoteBitmap(quote)
            val fileName = "ReadMate_Wisdom_${quote.id}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ReadMate")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to insert MediaStore image record.")

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                } ?: throw IllegalStateException("Failed to open output stream for gallery save.")

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "Quote successfully saved to Pictures/ReadMate: $uri")
                Result.success(uri)
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val readMateDir = File(picturesDir, "ReadMate")
                if (!readMateDir.exists()) {
                    readMateDir.mkdirs()
                }
                val destFile = File(readMateDir, fileName)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                val fileUri = Uri.fromFile(destFile)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                Result.success(fileUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save quote card to Gallery: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Renders the quote card bitmap, writes it to the app cache, and launches the system share sheet.
     */
    suspend fun renderAndShareQuoteCard(
        context: Context,
        quote: WisdomQuote,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            val bitmap = renderQuoteBitmap(quote)
            val file = saveBitmapToCache(context, bitmap, quote.id)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                val shareText = buildString {
                    append("\"${quote.englishQuote}\"\n\n")
                    if (quote.romanUrduPunchline.isNotBlank()) {
                        append("“${quote.romanUrduPunchline}”\n\n")
                    }
                    val author = quote.author?.takeIf { it.isNotBlank() } ?: "Timeless Wisdom"
                    append("— $author, ${quote.bookTitle} (Ch. ${quote.chapterNumber})")
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Wisdom Quote").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render and share quote card: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun renderQuoteBitmap(quote: WisdomQuote): Bitmap {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Full Canvas Solid Jet Obsidian Background
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_OBSIDIAN_BG
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), bgPaint)

        // 2. Centered Elegant Card Container
        val margin = 54f
        val cardRect = RectF(margin, margin, IMAGE_WIDTH - margin, IMAGE_HEIGHT - margin)
        val cardRadius = 36f

        val cardBgPaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_CARD_SURFACE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBgPaint)

        val cardBorderPaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_ZINC_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBorderPaint)

        val contentPadding = 64f
        val contentLeft = margin + contentPadding
        val contentRight = IMAGE_WIDTH - margin - contentPadding
        val contentWidth = (contentRight - contentLeft).toInt()
        val centerX = IMAGE_WIDTH / 2f

        // 3. Subtle Ambient Top-Left Serif Watermark ("“")
        val watermarkPaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_WATERMARK
            textSize = 210f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("“", contentLeft - 10f, margin + 175f, watermarkPaint)

        // 4. Content Dynamic Sizing & Layout
        val textLength = quote.englishQuote.length
        val englishQuoteTextSize = when {
            textLength < 90 -> 52f
            textLength < 160 -> 44f
            textLength < 250 -> 38f
            else -> 32f
        }

        val englishPaint = TextPaint().apply {
            isAntiAlias = true
            color = COLOR_PURE_WHITE
            textSize = englishQuoteTextSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val englishLayout = StaticLayout.Builder.obtain(
            quote.englishQuote,
            0,
            quote.englishQuote.length,
            englishPaint,
            contentWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.2f)
            .setIncludePad(false)
            .build()

        val urduText = quote.romanUrduPunchline.trim()
        val urduLayout = if (urduText.isNotBlank()) {
            val urduPaint = TextPaint().apply {
                isAntiAlias = true
                color = COLOR_SILVER_URDU
                textSize = if (textLength > 200) 32f else 36f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            StaticLayout.Builder.obtain(
                urduText,
                0,
                urduText.length,
                urduPaint,
                contentWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(12f, 1.25f)
                .setIncludePad(false)
                .build()
        } else null

        // 5. Vertical Content Calculation & Layout
        val dividerHeight = if (urduLayout != null) 36f else 0f
        val totalTextHeight = englishLayout.height + (urduLayout?.height ?: 0) + dividerHeight
        val bottomSectionHeight = 160f
        val topStart = margin + 120f
        val availableHeight = (IMAGE_HEIGHT - margin - bottomSectionHeight) - topStart
        val verticalOffset = ((availableHeight - totalTextHeight) / 2.2f).coerceIn(10f, 180f)

        var currentY = topStart + verticalOffset

        // Draw English Quote
        canvas.save()
        canvas.translate(contentLeft, currentY)
        englishLayout.draw(canvas)
        canvas.restore()

        currentY += englishLayout.height

        // Draw Minimalist 1px Zinc Divider & Roman Urdu Insight
        if (urduLayout != null) {
            currentY += 28f
            val dividerPaint = Paint().apply {
                isAntiAlias = true
                color = COLOR_ZINC_BORDER
                strokeWidth = 2f
            }
            canvas.drawLine(contentLeft, currentY, contentLeft + 120f, currentY, dividerPaint)
            currentY += 34f

            canvas.save()
            canvas.translate(contentLeft, currentY)
            urduLayout.draw(canvas)
            canvas.restore()
        }

        // 6. Footer Metadata (Author, Book Title, Chapter)
        val author = quote.author?.takeIf { it.isNotBlank() } ?: "ReadMate Wisdom"
        val metaBookLine = "${quote.bookTitle} • Ch. ${quote.chapterNumber}"

        val authorPaint = TextPaint().apply {
            isAntiAlias = true
            color = COLOR_PURE_WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val bookMetaPaint = TextPaint().apply {
            isAntiAlias = true
            color = COLOR_MUTED_META
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        val footerY = IMAGE_HEIGHT - margin - 100f
        canvas.drawText(author, contentLeft, footerY, authorPaint)
        canvas.drawText(metaBookLine, contentLeft, footerY + 34f, bookMetaPaint)

        // 7. Minimal Bottom Watermark "READMATE"
        val brandPaint = TextPaint().apply {
            isAntiAlias = true
            color = COLOR_SUBTLE_BRAND
            textSize = 20f
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("READMATE", contentRight, footerY + 34f, brandPaint)

        return bitmap
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, quoteId: Long): File {
        val cacheDir = File(context.cacheDir, "wisdom_quotes")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val file = File(cacheDir, "wisdom_quote_${quoteId}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }
}

