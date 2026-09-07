package com.example.data.model

enum class NotificationType {
    RETENTION_QUIZ,
    WORD_VAULT_QUIZ,
    STREAK_MILESTONE,
    INACTIVE_RECALL
}

data class InAppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionLabel: String? = null,
    val targetRoute: String? = null,
    val isRead: Boolean = false
)
