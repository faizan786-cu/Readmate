package com.example.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.ChapterMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Result of the PDF export operation.
 */
data class ExportPdfResult(
    val uri: Uri,
    val fileName: String,
    val publicPathDescription: String,
    val totalPassages: Int,
    val totalPages: Int
)

/**
 * Clean data model for parsed AI explanation sections.
 */
data class ParsedTurnSection(
    val passageNumber: Int,
    val originalText: String,
    val asaanSamjh: String,
    val mainLesson: String,
    val keyPoints: List<String>,
    val realLifeExample: String,
    val otherSections: List<Pair<String, String>>
)

object ChapterPdfExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points (72 dpi)
    private const val MARGIN_LEFT = 42f
    private const val MARGIN_RIGHT = 42f
    private const val MARGIN_TOP = 48f
    private const val MARGIN_BOTTOM = 48f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT).toInt()

    // Professional Color Palette
    private val COLOR_PRIMARY_DARK = Color.rgb(30, 27, 75) // Deep Indigo #1E1B4B
    private val COLOR_ACCENT_BLUE = Color.rgb(37, 99, 235) // ReadMate Blue #2563EB
    private val COLOR_TEXT_PRIMARY = Color.rgb(15, 23, 42) // Slate 900 #0F172A
    private val COLOR_TEXT_SECONDARY = Color.rgb(71, 85, 105) // Slate 600 #475569
    private val COLOR_TEXT_MUTED = Color.rgb(148, 163, 184) // Slate 400 #94A3B8
    private val COLOR_DIVIDER = Color.rgb(226, 232, 240) // Slate 200 #E2E8F0

    // Card Colors
    private val COLOR_BADGE_BG = Color.rgb(239, 246, 255) // Blue 50
    private val COLOR_BADGE_BORDER = Color.rgb(191, 219, 254) // Blue 200
    private val COLOR_BADGE_TEXT = Color.rgb(29, 78, 216) // Blue 700

    private val COLOR_PASSAGE_BG = Color.rgb(248, 250, 252) // Slate 50
    private val COLOR_PASSAGE_BORDER = Color.rgb(226, 232, 240) // Slate 200
    private val COLOR_PASSAGE_BAR = Color.rgb(59, 130, 246) // Blue 500

    private val COLOR_LESSON_BG = Color.rgb(254, 243, 199) // Amber 100 #FEF3C7
    private val COLOR_LESSON_BORDER = Color.rgb(253, 230, 138) // Amber 200 #FDE68A
    private val COLOR_LESSON_TITLE = Color.rgb(180, 83, 9) // Amber 700 #B45309
    private val COLOR_LESSON_TEXT = Color.rgb(120, 53, 15) // Amber 900 #78350F

    private val COLOR_EXAMPLE_BG = Color.rgb(240, 253, 244) // Green 50 #F0FDF4
    private val COLOR_EXAMPLE_BORDER = Color.rgb(187, 247, 208) // Green 200 #BBF7D0
    private val COLOR_EXAMPLE_TITLE = Color.rgb(21, 128, 61) // Green 700 #15803D
    private val COLOR_EXAMPLE_TEXT = Color.rgb(20, 83, 45) // Green 900 #14532D

    /**
     * Generates a beautifully styled study guide PDF and saves it to local device storage.
     */
    suspend fun exportChapterToPdf(
        context: Context,
        book: Book,
        chapter: Chapter,
        messages: List<ChapterMessage>
    ): Result<ExportPdfResult> = withContext(Dispatchers.IO) {
        try {
            if (messages.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No passage explanations in this chapter to export."))
            }

            val sanitizedBookTitle = sanitizeFileName(book.title.ifBlank { "Book" })
            val sanitizedChapterTitle = sanitizeFileName(chapter.title.ifBlank { "Chapter_${chapter.chapterNumber}" })
            val fileName = "${sanitizedBookTitle}_${sanitizedChapterTitle}_Summary.pdf"

            // 1. Parse all messages into structured turns
            val parsedTurns = messages.mapIndexed { index, msg ->
                parseMessageTurn(index + 1, msg)
            }

            // 2. Generate PDF document
            val pdfDocument = PdfDocument()
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            val exportTimestamp = SimpleDateFormat("MMMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date())

            var currentY = MARGIN_TOP

            // Draw Header on First Page
            currentY = drawDocumentHeader(
                canvas = canvas,
                book = book,
                chapter = chapter,
                exportDate = exportTimestamp,
                startY = currentY
            )

            // Function to advance to a new page
            fun advanceToNewPage() {
                // Draw footer on current page
                drawPageFooter(canvas, currentPageNumber)
                pdfDocument.finishPage(currentPage)

                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                currentY = MARGIN_TOP

                // Draw running top header on subsequent pages
                currentY = drawRunningPageHeader(
                    canvas = canvas,
                    bookTitle = book.title,
                    chapterTitle = "Chapter ${chapter.chapterNumber}: ${chapter.title}",
                    startY = currentY
                )
            }

            // Draw each passage turn
            for (turn in parsedTurns) {
                // Estimated height check for turn header + initial passage
                val neededHeightForTurnStart = 110f
                if (currentY + neededHeightForTurnStart > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    advanceToNewPage()
                }

                // 1. Passage Header Badge (e.g. "Passage 1")
                currentY = drawPassageBadge(canvas, turn.passageNumber, currentY)

                // 2. Original English Passage Quote Box (if available)
                if (turn.originalText.isNotBlank()) {
                    val passageHeight = measureQuoteBoxHeight(turn.originalText, CONTENT_WIDTH)
                    if (currentY + passageHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    }
                    currentY = drawQuoteBox(
                        canvas = canvas,
                        title = "Original English Passage",
                        text = turn.originalText,
                        startY = currentY,
                        width = CONTENT_WIDTH
                    )
                }

                // 3. Asaan Samjh (Simple Roman Urdu Explanation)
                if (turn.asaanSamjh.isNotBlank()) {
                    val explanationLayout = createMarkdownLayout(
                        text = turn.asaanSamjh,
                        width = CONTENT_WIDTH,
                        textSize = 10.5f,
                        textColor = COLOR_TEXT_PRIMARY,
                        lineSpacingExtra = 3.5f
                    )
                    val sectionHeight = 24f + explanationLayout.height + 12f
                    if (currentY + sectionHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    }
                    currentY = drawSectionTitle(canvas, "Asaan Samjh (Simple Urdu Explanation)", COLOR_ACCENT_BLUE, currentY)
                    canvas.save()
                    canvas.translate(MARGIN_LEFT, currentY)
                    explanationLayout.draw(canvas)
                    canvas.restore()
                    currentY += explanationLayout.height + 14f
                }

                // 4. Main Lesson / Key Takeaway Callout Box
                if (turn.mainLesson.isNotBlank()) {
                    val lessonHeight = measureCalloutBoxHeight(
                        title = "Main Lesson",
                        body = turn.mainLesson,
                        width = CONTENT_WIDTH
                    )
                    if (currentY + lessonHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    }
                    currentY = drawCalloutBox(
                        canvas = canvas,
                        title = "Main Lesson",
                        body = turn.mainLesson,
                        bgColor = COLOR_LESSON_BG,
                        borderColor = COLOR_LESSON_BORDER,
                        titleColor = COLOR_LESSON_TITLE,
                        textColor = COLOR_LESSON_TEXT,
                        startY = currentY,
                        width = CONTENT_WIDTH
                    )
                }

                // 5. Key Points (Bulleted list)
                if (turn.keyPoints.isNotEmpty()) {
                    val bulletLayouts = turn.keyPoints.map { point ->
                        createMarkdownLayout(
                            text = "• $point",
                            width = CONTENT_WIDTH - 8,
                            textSize = 10f,
                            textColor = COLOR_TEXT_PRIMARY,
                            lineSpacingExtra = 3f
                        )
                    }
                    val totalPointsHeight = 24f + bulletLayouts.sumOf { it.height + 6 } + 10f
                    if (currentY + totalPointsHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    }
                    currentY = drawSectionTitle(canvas, "Key Points", COLOR_PRIMARY_DARK, currentY)
                    for (layout in bulletLayouts) {
                        if (currentY + layout.height + 6 > PAGE_HEIGHT - MARGIN_BOTTOM) {
                            advanceToNewPage()
                        }
                        canvas.save()
                        canvas.translate(MARGIN_LEFT + 8f, currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 6f
                    }
                    currentY += 8f
                }

                // 6. Real-Life Example Callout Box
                if (turn.realLifeExample.isNotBlank()) {
                    val exampleHeight = measureCalloutBoxHeight(
                        title = "Real-Life Example",
                        body = turn.realLifeExample,
                        width = CONTENT_WIDTH
                    )
                    if (currentY + exampleHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    }
                    currentY = drawCalloutBox(
                        canvas = canvas,
                        title = "Real-Life Example",
                        body = turn.realLifeExample,
                        bgColor = COLOR_EXAMPLE_BG,
                        borderColor = COLOR_EXAMPLE_BORDER,
                        titleColor = COLOR_EXAMPLE_TITLE,
                        textColor = COLOR_EXAMPLE_TEXT,
                        startY = currentY,
                        width = CONTENT_WIDTH
                    )
                }

                // 7. Other custom sections (if any)
                for ((title, body) in turn.otherSections) {
                    if (body.isNotBlank()) {
                        val otherLayout = createMarkdownLayout(
                            text = body,
                            width = CONTENT_WIDTH,
                            textSize = 10f,
                            textColor = COLOR_TEXT_PRIMARY,
                            lineSpacingExtra = 3f
                        )
                        if (currentY + otherLayout.height + 30f > PAGE_HEIGHT - MARGIN_BOTTOM) {
                            advanceToNewPage()
                        }
                        currentY = drawSectionTitle(canvas, title, COLOR_PRIMARY_DARK, currentY)
                        canvas.save()
                        canvas.translate(MARGIN_LEFT, currentY)
                        otherLayout.draw(canvas)
                        canvas.restore()
                        currentY += otherLayout.height + 12f
                    }
                }

                // Divider between passages
                if (turn != parsedTurns.last()) {
                    if (currentY + 24f > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        advanceToNewPage()
                    } else {
                        val divPaint = Paint().apply {
                            color = COLOR_DIVIDER
                            strokeWidth = 1f
                            style = Paint.Style.STROKE
                        }
                        canvas.drawLine(MARGIN_LEFT, currentY + 8f, PAGE_WIDTH - MARGIN_RIGHT, currentY + 8f, divPaint)
                        currentY += 24f
                    }
                }
            }

            // Finish the last page
            drawPageFooter(canvas, currentPageNumber)
            pdfDocument.finishPage(currentPage)

            // 3. Save PDF to cache directory first (for reliable FileProvider URI)
            val cacheDir = File(context.cacheDir, "exported_pdfs").apply { mkdirs() }
            val cacheFile = File(cacheDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // 4. Save to device Public Downloads folder via MediaStore on Android 10+ / File on older
            var publicDescription = "Downloads/ReadMate/$fileName"
            saveToPublicDownloads(context, cacheFile, fileName)

            val fileUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
            } catch (_: Exception) {
                Uri.fromFile(cacheFile)
            }

            Result.success(
                ExportPdfResult(
                    uri = fileUri,
                    fileName = fileName,
                    publicPathDescription = publicDescription,
                    totalPassages = parsedTurns.size,
                    totalPages = currentPageNumber
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves a copy of the PDF to the device's public Downloads directory.
     */
    private fun saveToPublicDownloads(context: Context, sourceFile: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ReadMate")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val readMateDir = File(downloadsDir, "ReadMate").apply { mkdirs() }
                val targetFile = File(readMateDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        } catch (_: Exception) {
            // Silently handle if MediaStore write has minor storage restriction; cache file with FileProvider remains available
        }
    }

    /**
     * Creates an Intent to open the generated PDF with any PDF reader app installed on the device.
     */
    fun getOpenPdfIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to share the generated PDF via messaging, email, or drive apps.
     */
    fun getSharePdfIntent(uri: Uri, title: String): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Here is the ReadMate study guide summary: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Chapter Summary PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // =========================================================================
    // CANVAS RENDERING HELPERS
    // =========================================================================

    private fun drawDocumentHeader(
        canvas: Canvas,
        book: Book,
        chapter: Chapter,
        exportDate: String,
        startY: Float
    ): Float {
        var y = startY

        // Brand Pill / App Tag
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT_BLUE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("READMATE • CHAPTER STUDY GUIDE", MARGIN_LEFT, y, brandPaint)
        y += 18f

        // Book Title (Large Display)
        val bookTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY_DARK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bookTitleLayout = StaticLayout.Builder.obtain(
            book.title,
            0,
            book.title.length,
            bookTitlePaint,
            CONTENT_WIDTH
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        canvas.save()
        canvas.translate(MARGIN_LEFT, y)
        bookTitleLayout.draw(canvas)
        canvas.restore()
        y += bookTitleLayout.height + 6f

        // Author Name
        if (!book.author.isNullOrBlank()) {
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("By ${book.author}", MARGIN_LEFT, y, authorPaint)
            y += 18f
        }

        // Chapter Number & Title
        val chapterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT_BLUE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val chapterText = "Chapter ${chapter.chapterNumber}: ${chapter.title}"
        val chapterLayout = StaticLayout.Builder.obtain(
            chapterText,
            0,
            chapterText.length,
            chapterPaint,
            CONTENT_WIDTH
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        canvas.save()
        canvas.translate(MARGIN_LEFT, y)
        chapterLayout.draw(canvas)
        canvas.restore()
        y += chapterLayout.height + 6f

        // Export Date
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 9f
        }
        canvas.drawText("Exported on $exportDate", MARGIN_LEFT, y, datePaint)
        y += 14f

        // Horizontal Accent Divider Bar
        val dividerPaint = Paint().apply {
            color = COLOR_ACCENT_BLUE
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + 60f, y, dividerPaint)

        val thinDivPaint = Paint().apply {
            color = COLOR_DIVIDER
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN_LEFT + 65f, y, PAGE_WIDTH - MARGIN_RIGHT, y, thinDivPaint)
        y += 24f

        return y
    }

    private fun drawRunningPageHeader(
        canvas: Canvas,
        bookTitle: String,
        chapterTitle: String,
        startY: Float
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val headerText = "$bookTitle • $chapterTitle"
        val truncatedHeader = if (headerText.length > 70) headerText.take(67) + "..." else headerText
        canvas.drawText(truncatedHeader, MARGIN_LEFT, startY, paint)

        val linePaint = Paint().apply {
            color = COLOR_DIVIDER
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN_LEFT, startY + 6f, PAGE_WIDTH - MARGIN_RIGHT, startY + 6f, linePaint)

        return startY + 22f
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val y = PAGE_HEIGHT - 28f
        val linePaint = Paint().apply {
            color = COLOR_DIVIDER
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN_LEFT, y - 10f, PAGE_WIDTH - MARGIN_RIGHT, y - 10f, linePaint)

        val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8.5f
        }
        canvas.drawText("ReadMate • Book Study Guide", MARGIN_LEFT, y, leftPaint)

        val rightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN_RIGHT, y, rightPaint)
    }

    private fun drawPassageBadge(canvas: Canvas, passageNumber: Int, startY: Float): Float {
        val badgeText = "Passage $passageNumber"
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BADGE_TEXT
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textWidth = textPaint.measureText(badgeText)
        val badgeWidth = textWidth + 24f
        val badgeHeight = 22f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BADGE_BG
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BADGE_BORDER
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val rect = RectF(MARGIN_LEFT, startY, MARGIN_LEFT + badgeWidth, startY + badgeHeight)
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        canvas.drawText(badgeText, MARGIN_LEFT + 12f, startY + 15f, textPaint)
        return startY + badgeHeight + 12f
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        title: String,
        color: Int,
        startY: Float
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, MARGIN_LEFT, startY, paint)
        return startY + 16f
    }

    private fun measureQuoteBoxHeight(text: String, width: Int): Float {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            width - 32
        ).setLineSpacing(3f, 1f).build()

        return 24f + layout.height + 16f
    }

    private fun drawQuoteBox(
        canvas: Canvas,
        title: String,
        text: String,
        startY: Float,
        width: Int
    ): Float {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            width - 32
        ).setLineSpacing(3f, 1f).build()

        val boxHeight = 22f + layout.height + 16f
        val rect = RectF(MARGIN_LEFT, startY, MARGIN_LEFT + width, startY + boxHeight)

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PASSAGE_BG
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        // Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PASSAGE_BORDER
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        // Left accent bar
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PASSAGE_BAR
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(MARGIN_LEFT, startY, MARGIN_LEFT + 4f, startY + boxHeight), 4f, 4f, barPaint)

        // Title
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, MARGIN_LEFT + 16f, startY + 16f, titlePaint)

        // Text
        canvas.save()
        canvas.translate(MARGIN_LEFT + 16f, startY + 24f)
        layout.draw(canvas)
        canvas.restore()

        return startY + boxHeight + 14f
    }

    private fun measureCalloutBoxHeight(
        title: String,
        body: String,
        width: Int
    ): Float {
        val layout = createMarkdownLayout(
            text = body,
            width = width - 28,
            textSize = 10f,
            textColor = COLOR_TEXT_PRIMARY,
            lineSpacingExtra = 3f
        )
        return 24f + layout.height + 16f
    }

    private fun drawCalloutBox(
        canvas: Canvas,
        title: String,
        body: String,
        bgColor: Int,
        borderColor: Int,
        titleColor: Int,
        textColor: Int,
        startY: Float,
        width: Int
    ): Float {
        val layout = createMarkdownLayout(
            text = body,
            width = width - 28,
            textSize = 10f,
            textColor = textColor,
            lineSpacingExtra = 3f
        )

        val boxHeight = 24f + layout.height + 14f
        val rect = RectF(MARGIN_LEFT, startY, MARGIN_LEFT + width, startY + boxHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        // Title
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, MARGIN_LEFT + 14f, startY + 17f, titlePaint)

        // Content
        canvas.save()
        canvas.translate(MARGIN_LEFT + 14f, startY + 24f)
        layout.draw(canvas)
        canvas.restore()

        return startY + boxHeight + 14f
    }

    private fun createMarkdownLayout(
        text: String,
        width: Int,
        textSize: Float,
        textColor: Int,
        lineSpacingExtra: Float
    ): StaticLayout {
        val spannable = parseMarkdownSpannable(text, textColor)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = textColor
        }
        return StaticLayout.Builder.obtain(
            spannable,
            0,
            spannable.length,
            textPaint,
            width.coerceAtLeast(50)
        ).setLineSpacing(lineSpacingExtra, 1f).build()
    }

    private fun parseMarkdownSpannable(text: String, defaultColor: Int): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val pattern = Pattern.compile("\\*\\*(.*?)\\*\\*")
        val matcher = pattern.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val boldContent = matcher.group(1) ?: ""

            if (start > lastIndex) {
                builder.append(text.substring(lastIndex, start))
            }

            val styleStart = builder.length
            builder.append(boldContent)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                styleStart,
                builder.length,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            lastIndex = end
        }

        if (lastIndex < text.length) {
            builder.append(text.substring(lastIndex))
        }

        return builder
    }

    // =========================================================================
    // MESSAGE PARSING
    // =========================================================================

    internal fun parseMessageTurn(turnNumber: Int, message: ChapterMessage): ParsedTurnSection {
        val originalText = if (message.originalText.isNotBlank()) {
            message.originalText.trim()
        } else {
            ""
        }

        val rawAi = message.aiResponse
        val rawSections = parseRawResponseSections(rawAi)

        var asaanSamjh = ""
        var mainLesson = ""
        val keyPoints = mutableListOf<String>()
        var realLifeExample = ""
        val otherSections = mutableListOf<Pair<String, String>>()

        var extractedOriginal = originalText

        for (section in rawSections) {
            val title = section.title
            val body = section.body

            when {
                title.contains("Original English Passage", ignoreCase = true) ||
                title.contains("Original Passage", ignoreCase = true) ||
                title.contains("English Passage", ignoreCase = true) -> {
                    if (extractedOriginal.isBlank()) {
                        extractedOriginal = body.trim()
                    }
                }
                title.contains("Asaan Samjh", ignoreCase = true) ||
                title.contains("Samjh", ignoreCase = true) ||
                title.contains("Explanation", ignoreCase = true) ||
                title.contains("1.", ignoreCase = true) -> {
                    asaanSamjh = body.trim()
                }
                title.contains("Main Lesson", ignoreCase = true) ||
                title.contains("Lesson", ignoreCase = true) ||
                title.contains("Sabaq", ignoreCase = true) ||
                title.contains("2.", ignoreCase = true) -> {
                    mainLesson = body.trim()
                }
                title.contains("Key Points", ignoreCase = true) ||
                title.contains("Points", ignoreCase = true) ||
                title.contains("Nuqaat", ignoreCase = true) ||
                title.contains("3.", ignoreCase = true) -> {
                    // Extract bullet lines
                    val points = body.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            line.removePrefix("•")
                                .removePrefix("-")
                                .removePrefix("*")
                                .trim()
                        }
                        .filter { it.isNotBlank() }
                    keyPoints.addAll(points)
                }
                title.contains("Real-World Example", ignoreCase = true) ||
                title.contains("Real-Life Example", ignoreCase = true) ||
                title.contains("Example", ignoreCase = true) ||
                title.contains("Misaal", ignoreCase = true) ||
                title.contains("4.", ignoreCase = true) -> {
                    realLifeExample = body.trim()
                }
                else -> {
                    if (body.isNotBlank()) {
                        if (title.isBlank() && asaanSamjh.isBlank()) {
                            asaanSamjh = body.trim()
                        } else if (title.isNotBlank()) {
                            otherSections.add(Pair(title.trim(), body.trim()))
                        }
                    }
                }
            }
        }

        // If structured sections were completely empty (fallback raw text)
        if (asaanSamjh.isBlank() && mainLesson.isBlank() && keyPoints.isEmpty() && realLifeExample.isBlank()) {
            asaanSamjh = rawAi.trim()
        }

        return ParsedTurnSection(
            passageNumber = turnNumber,
            originalText = extractedOriginal,
            asaanSamjh = asaanSamjh,
            mainLesson = mainLesson,
            keyPoints = keyPoints,
            realLifeExample = realLifeExample,
            otherSections = otherSections
        )
    }

    private data class RawSection(val title: String, val body: String)

    private fun parseRawResponseSections(raw: String): List<RawSection> {
        val lines = raw.lines()
        val sections = mutableListOf<RawSection>()
        var currentTitle = ""
        val currentBodyLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                continue
            }
            if (trimmed.startsWith("#")) {
                val headerText = trimmed.trimStart('#').trim()
                if (currentTitle.isNotBlank() || currentBodyLines.isNotEmpty()) {
                    sections.add(
                        RawSection(
                            title = currentTitle.trim(),
                            body = currentBodyLines.joinToString("\n").trim()
                        )
                    )
                    currentBodyLines.clear()
                }
                currentTitle = headerText
            } else {
                currentBodyLines.add(line)
            }
        }

        if (currentTitle.isNotBlank() || currentBodyLines.isNotEmpty()) {
            sections.add(
                RawSection(
                    title = currentTitle.trim(),
                    body = currentBodyLines.joinToString("\n").trim()
                )
            )
        }

        return sections
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(40)
    }
}
