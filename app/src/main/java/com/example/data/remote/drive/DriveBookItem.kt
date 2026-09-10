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
    val coverUrl: String
        get() = highResCoverUrl

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

        fun parseTitleAndAuthor(rawFileName: String): Pair<String, String> {
            val cleanName = rawFileName.removeSuffix(".pdf").replace("_", " ").trim()
            return if (cleanName.contains(" - ")) {
                val parts = cleanName.split(" - ", limit = 2)
                Pair(parts[0].trim(), parts[1].trim())
            } else if (cleanName.contains(" -- ")) {
                val parts = cleanName.split(" -- ", limit = 2)
                Pair(parts[0].trim(), parts[1].trim())
            } else if (cleanName.contains(" by ", ignoreCase = true)) {
                val parts = cleanName.split(" by ", ignoreCase = true, limit = 2)
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                Pair(cleanName, "Community Upload")
            }
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
