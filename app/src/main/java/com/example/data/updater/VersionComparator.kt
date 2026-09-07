package com.example.data.updater

object VersionComparator {

    /**
     * Returns true if [remoteTag] is strictly newer than [currentVersionName].
     * E.g.: "v1.0.4" is newer than "1.0.0" or "1.0"
     */
    fun isNewer(remoteTag: String, currentVersionName: String): Boolean {
        val remoteNums = parseVersionNumbers(remoteTag)
        val currentNums = parseVersionNumbers(currentVersionName)

        if (remoteNums.isEmpty() && currentNums.isEmpty()) {
            return remoteTag.trim() != currentVersionName.trim()
        }

        val maxLen = maxOf(remoteNums.size, currentNums.size)
        for (i in 0 until maxLen) {
            val r = remoteNums.getOrElse(i) { 0 }
            val c = currentNums.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Extracts numerical components from version strings (e.g. "v1.0.4" -> [1, 0, 4]).
     */
    private fun parseVersionNumbers(versionStr: String): List<Int> {
        val cleaned = versionStr.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("release-")
            .removePrefix("ver-")
            .substringBefore("-")
            .substringBefore("+")

        return cleaned.split(".")
            .mapNotNull { segment ->
                segment.filter { it.isDigit() }.toIntOrNull()
            }
    }
}
