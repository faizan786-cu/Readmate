package com.example.data.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PdfImportResult(
    val filePath: String,
    val fileName: String,
    val totalPages: Int
)

object PdfStorageManager {

    private const val PDF_DIR_NAME = "readmate_pdfs"

    private fun getPdfDirectory(context: Context): File {
        val dir = File(context.filesDir, PDF_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun importPdfForChapter(
        context: Context,
        uri: Uri,
        bookId: Long,
        chapterId: Long
    ): Result<PdfImportResult> = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val originalName = queryFileName(context, uri).ifBlank { "chapter_${chapterId}.pdf" }
            val sanitizedName = sanitizeFileName(originalName)
            val targetFileName = "book_${bookId}_chapter_${chapterId}_${System.currentTimeMillis()}_$sanitizedName"
            val targetFile = File(getPdfDirectory(context), targetFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open selected PDF file."))

            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext Result.failure(Exception("PDF file is empty or corrupted."))
            }

            val pageCount = getPdfPageCount(targetFile)
            if (pageCount <= 0) {
                targetFile.delete()
                return@withContext Result.failure(Exception("The selected PDF file has no readable pages."))
            }

            Result.success(
                PdfImportResult(
                    filePath = targetFile.absolutePath,
                    fileName = originalName,
                    totalPages = pageCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importPdfForBook(
        context: Context,
        uri: Uri,
        bookId: Long
    ): Result<PdfImportResult> = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val originalName = queryFileName(context, uri).ifBlank { "book_${bookId}.pdf" }
            val sanitizedName = sanitizeFileName(originalName)
            val targetFileName = "book_${bookId}_${System.currentTimeMillis()}_$sanitizedName"
            val targetFile = File(getPdfDirectory(context), targetFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open selected PDF file."))

            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext Result.failure(Exception("PDF file is empty or corrupted."))
            }

            val pageCount = getPdfPageCount(targetFile)
            if (pageCount <= 0) {
                targetFile.delete()
                return@withContext Result.failure(Exception("The selected PDF file has no readable pages."))
            }

            Result.success(
                PdfImportResult(
                    filePath = targetFile.absolutePath,
                    fileName = originalName,
                    totalPages = pageCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPdfPageCount(file: File): Int {
        if (!file.exists() || file.length() == 0L) return 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            0
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) { }
            try {
                pfd?.close()
            } catch (_: Exception) { }
        }
    }

    fun deletePdfFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) { }
    }

    fun queryFileName(context: Context, uri: Uri): String {
        var name = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = cursor.getString(nameIndex) ?: ""
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        }
        if (!name.endsWith(".pdf", ignoreCase = true)) {
            name = "$name.pdf"
        }
        return name
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }
}
