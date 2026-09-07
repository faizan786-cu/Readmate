package com.example.data.model

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class KeyStatus {
    ACTIVE,             // Available / Healthy
    COOLDOWN,           // Rate limited / Quota reached (HTTP 429)
    INVALID,            // Invalid or expired authentication (HTTP 401)
    PERMISSION_ERROR,   // Permission / Access problem (HTTP 403)
    ERROR,              // General error
    TEST_FAILED         // Test connection failed
}

@JsonClass(generateAdapter = true)
data class GeminiApiKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val label: String = "",
    val status: KeyStatus = KeyStatus.ACTIVE,
    val lastUsedTimestamp: Long = 0L,
    val cooldownUntilTimestamp: Long = 0L,
    val errorMessage: String? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val successfulRequests: Int = 0,
    val rateLimitErrors429: Int = 0,
    val serviceUnavailableErrors503: Int = 0,
    val authenticationErrors401: Int = 0,
    val permissionErrors403: Int = 0,
    val configurationErrors404: Int = 0,
    val badRequestErrors400: Int = 0
) {
    val maskedKey: String
        get() {
            val trimmed = key.trim()
            return when {
                trimmed.startsWith("AQ.") -> {
                    if (trimmed.length >= 10) "AQ.••••••••" + trimmed.takeLast(4) else "AQ.••••••••"
                }
                trimmed.length >= 8 -> {
                    "••••••••" + trimmed.takeLast(4)
                }
                else -> {
                    "••••••••"
                }
            }
        }

    val displayLabel: String
        get() = if (label.isNotBlank()) label else "API Key"

    fun isAvailable(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return when (status) {
            KeyStatus.ACTIVE -> true
            KeyStatus.COOLDOWN -> currentTimeMs >= cooldownUntilTimestamp
            KeyStatus.INVALID,
            KeyStatus.PERMISSION_ERROR,
            KeyStatus.ERROR,
            KeyStatus.TEST_FAILED -> false
        }
    }

    fun remainingCooldownSeconds(currentTimeMs: Long = System.currentTimeMillis()): Long {
        return if (status == KeyStatus.COOLDOWN && cooldownUntilTimestamp > currentTimeMs) {
            ((cooldownUntilTimestamp - currentTimeMs + 999) / 1000).coerceAtLeast(1)
        } else {
            0
        }
    }

    companion object {
        fun isValidKeyFormat(rawKey: String): Boolean {
            val trimmed = rawKey.trim()
            // Accept any valid Gemini API key format without prefix restrictions.
            // Keys can begin with AIzaSy, AQ., or any standard format.
            return trimmed.isNotBlank() && trimmed.length >= 8
        }
    }
}
