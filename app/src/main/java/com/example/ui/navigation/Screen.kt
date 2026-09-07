package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")

    data object Dashboard : Screen("dashboard")

    data object Library : Screen("library")

    data object BookCreate : Screen("book/create")

    data object BookDetail : Screen("book/{bookId}") {
        fun createRoute(bookId: Long) = "book/$bookId"
    }

    data object BookEdit : Screen("book/{bookId}/edit") {
        fun createRoute(bookId: Long) = "book/$bookId/edit"
    }

    data object ChapterCreate : Screen("book/{bookId}/chapter/create") {
        fun createRoute(bookId: Long) = "book/$bookId/chapter/create"
    }

    data object ChapterEdit : Screen("chapter/{chapterId}/edit") {
        fun createRoute(chapterId: Long) = "chapter/$chapterId/edit"
    }

    data object ChapterChat : Screen("chapter/{chapterId}/chat?targetMessageId={targetMessageId}") {
        fun createRoute(chapterId: Long, targetMessageId: Long? = null): String {
            return if (targetMessageId != null) {
                "chapter/$chapterId/chat?targetMessageId=$targetMessageId"
            } else {
                "chapter/$chapterId/chat"
            }
        }
    }

    data object ChapterResponse : Screen("chapter/{chapterId}/response?initialIndex={initialIndex}") {
        fun createRoute(chapterId: Long, initialIndex: Int = 0): String {
            return "chapter/$chapterId/response?initialIndex=$initialIndex"
        }
    }

    data object WordVault : Screen("wordvault?chapterId={chapterId}&bookId={bookId}") {
        fun createRoute(chapterId: Long? = null, bookId: Long? = null): String {
            val cId = chapterId ?: -1L
            val bId = bookId ?: -1L
            return "wordvault?chapterId=$cId&bookId=$bId"
        }
    }

    data object Settings : Screen("settings")

    data object ApiKeyManagement : Screen("settings/api-management")

    data object GeminiConfig : Screen("settings/gemini")

    data object WisdomReels : Screen("wisdom_reels?quoteId={quoteId}") {
        fun createRoute(quoteId: Long? = null): String {
            return if (quoteId != null && quoteId > 0L) {
                "wisdom_reels?quoteId=$quoteId"
            } else {
                "wisdom_reels"
            }
        }
    }

    data object Progress : Screen("progress")

    data object DailyRecall : Screen("daily_recall")

    data object Quiz : Screen("daily_recall")

    data object WordVaultQuiz : Screen("word_vault_quiz")

    data object ChapterMasteryQuiz : Screen("chapter_mastery_quiz/{chapterId}") {
        fun createRoute(chapterId: Long) = "chapter_mastery_quiz/$chapterId"
    }

    data object About : Screen("about_screen")
}
