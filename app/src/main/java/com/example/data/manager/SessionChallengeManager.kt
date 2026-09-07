package com.example.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory manager tracking dismissed daily challenges for the active app session.
 * Prevents full-screen modals from re-triggering repeatedly when switching tabs/screens,
 * while allowing dashboard fallback pill banners to remain accessible.
 */
class SessionChallengeManager {

    private val _dismissedSnippetChallenge = MutableStateFlow(false)
    val dismissedSnippetChallenge: StateFlow<Boolean> = _dismissedSnippetChallenge.asStateFlow()

    private val _dismissedWordVaultChallenge = MutableStateFlow(false)
    val dismissedWordVaultChallenge: StateFlow<Boolean> = _dismissedWordVaultChallenge.asStateFlow()

    private val _dismissedMasteryChapterIds = MutableStateFlow<Set<Long>>(emptySet())
    val dismissedMasteryChapterIds: StateFlow<Set<Long>> = _dismissedMasteryChapterIds.asStateFlow()

    private val _dismissedDailyDoubleBonus = MutableStateFlow(false)
    val dismissedDailyDoubleBonus: StateFlow<Boolean> = _dismissedDailyDoubleBonus.asStateFlow()

    fun dismissSnippetChallenge() {
        _dismissedSnippetChallenge.value = true
    }

    fun dismissWordVaultChallenge() {
        _dismissedWordVaultChallenge.value = true
    }

    fun dismissMasteryChapter(chapterId: Long) {
        _dismissedMasteryChapterIds.update { it + chapterId }
    }

    fun dismissDailyDoubleBonus() {
        _dismissedDailyDoubleBonus.value = true
    }

    fun resetSession() {
        _dismissedSnippetChallenge.value = false
        _dismissedWordVaultChallenge.value = false
        _dismissedMasteryChapterIds.value = emptySet()
        _dismissedDailyDoubleBonus.value = false
    }
}
