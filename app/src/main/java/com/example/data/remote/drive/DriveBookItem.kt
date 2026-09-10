package com.example.data.remote.drive

import java.util.Locale

data class DriveBookItem(
    val id: String,
    val rawName: String,
    val title: String,
    val author: String? = null,
    val sizeBytes: Long? = null,
    val formattedSize: String = "",
    val thumbnailLink: String? = null,
    val highResCoverUrl: String,
    val iconLink: String? = null
) {
    companion object {
        fun fromDriveJson(
            id: String,
            name: String,
            sizeStr: String?,
            thumbnailLink: String?,
            iconLink: String?
        ): DriveBookItem {
            val (title, author) = parseTitleAndAuthor(name)
            val sizeBytes = sizeStr?.toLongOrNull()
            val formattedSize = formatSize(sizeBytes)
            val highResCoverUrl = thumbnailLink?.replace("=s220", "=s600")
                ?: "https://drive.google.com/thumbnail?id=$id&sz=w600"

            return DriveBookItem(
                id = id,
                rawName = name,
                title = title,
                author = author,
                sizeBytes = sizeBytes,
                formattedSize = formattedSize,
                thumbnailLink = thumbnailLink,
                highResCoverUrl = highResCoverUrl,
                iconLink = iconLink
            )
        }

        fun parseTitleAndAuthor(rawName: String): Pair<String, String?> {
            var text = rawName
            if (text.endsWith(".pdf", ignoreCase = true)) {
                text = text.substring(0, text.length - 4)
            }
            text = text.replace('_', ' ')
            text = text.replace("\\s+".toRegex(), " ").trim()

            // 1. Pattern with " -- " separator (common in library datasets)
            if (text.contains(" -- ")) {
                val segments = text.split(" -- ")
                val cleanTitle = segments[0].trim()
                val authorSegment = segments.getOrNull(1)?.trim()
                val cleanAuthor = if (authorSegment != null && !authorSegment.matches("^\\d{4}$".toRegex())) {
                    authorSegment
                } else null
                return Pair(cleanTitle.ifBlank { "Untitled Document" }, cleanAuthor)
            }

            // 2. Pattern with " by " separator
            if (text.contains(" by ", ignoreCase = true)) {
                val parts = text.split(" by ", ignoreCase = true, limit = 2)
                val cleanTitle = parts[0].trim()
                val cleanAuthor = parts.getOrNull(1)?.trim()
                return Pair(cleanTitle.ifBlank { "Untitled Document" }, cleanAuthor)
            }

            // 3. Fallback clean title
            return Pair(text.ifBlank { "Untitled Document" }, null)
        }

        fun formatSize(bytes: Long?): String {
            if (bytes == null || bytes <= 0L) return "PDF"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.0f KB", kb)
            }
        }
    }
}
