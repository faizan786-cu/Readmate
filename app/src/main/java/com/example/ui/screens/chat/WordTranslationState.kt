package com.example.ui.screens.chat

import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.model.WordTranslationResult

sealed interface WordTranslationState {
    object Idle : WordTranslationState
    
    data class Loading(
        val word: String,
        val sentence: String,
        val targetMessageId: Long = 0L
    ) : WordTranslationState
    
    data class Success(
        val translation: WordTranslationResult,
        val originalSentence: String,
        val surroundingContext: String?,
        val existingVaultEntry: WordVaultEntry?,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false,
        val targetMessageId: Long = 0L,
        val originBookTitle: String? = null,
        val originChapterTitle: String? = null,
        val originChapterNumber: Int? = null,
        val originPassageText: String? = null,
        val originChapterId: Long? = null,
        val originMessageId: Long? = null,
        val isFromDifferentPassage: Boolean = false
    ) : WordTranslationState
    
    data class Error(
        val word: String,
        val sentence: String,
        val surroundingContext: String?,
        val errorMessage: String,
        val targetMessageId: Long = 0L
    ) : WordTranslationState
}
