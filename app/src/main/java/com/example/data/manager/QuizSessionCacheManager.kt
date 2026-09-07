package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.database.model.MistakeRecord
import com.example.data.local.database.model.WordMistakeRecord
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class CachedDailyRecallSession(
    val primaryQuestionIds: List<Long>,
    val primaryIndex: Int,
    val isRetryRound: Boolean,
    val retryQuestionIds: List<Long>,
    val retryIndex: Int,
    val primaryCorrectCount: Int,
    val primaryAnsweredCount: Int,
    val retryCorrectCount: Int,
    val recordedMistakes: List<MistakeRecord>,
    val savedAtTimestamp: Long,
    val expiresAt: Long
)

data class CachedWordVaultSession(
    val questions: List<WordQuizQuestion>,
    val currentIndex: Int,
    val isRetryRound: Boolean,
    val retryQuestions: List<WordQuizQuestion>,
    val currentRetryIndex: Int,
    val primaryCorrectCount: Int,
    val primaryAnsweredCount: Int,
    val retryCorrectCount: Int,
    val recordedMistakes: List<WordMistakeRecord>,
    val savedAtTimestamp: Long,
    val expiresAt: Long
)

data class CachedChapterMasterySession(
    val chapterId: Long,
    val primaryQuestionIds: List<Long>,
    val primaryIndex: Int,
    val isRetryRound: Boolean,
    val retryQuestionIds: List<Long>,
    val retryIndex: Int,
    val primaryCorrectCount: Int,
    val primaryAnsweredCount: Int,
    val retryCorrectCount: Int,
    val recordedMistakes: List<MistakeRecord>,
    val savedAtTimestamp: Long,
    val expiresAt: Long
)

class QuizSessionCacheManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "readmate_quiz_session_cache"
        private const val KEY_DAILY_RECALL = "cached_daily_recall"
        private const val KEY_WORD_VAULT = "cached_word_vault"
        private const val KEY_PREFIX_CHAPTER_MASTERY = "cached_chapter_mastery_"
        const val EXPIRATION_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val dailyRecallAdapter: JsonAdapter<CachedDailyRecallSession> =
        moshi.adapter(CachedDailyRecallSession::class.java)

    private val wordVaultAdapter: JsonAdapter<CachedWordVaultSession> =
        moshi.adapter(CachedWordVaultSession::class.java)

    private val chapterMasteryAdapter: JsonAdapter<CachedChapterMasterySession> =
        moshi.adapter(CachedChapterMasterySession::class.java)

    // ==========================================
    // 1. Daily Recall (Snippet MCQs) Cache
    // ==========================================
    fun saveDailyRecallSession(session: CachedDailyRecallSession) {
        val json = dailyRecallAdapter.toJson(session)
        prefs.edit().putString(KEY_DAILY_RECALL, json).apply()
    }

    fun getDailyRecallSession(): CachedDailyRecallSession? {
        val json = prefs.getString(KEY_DAILY_RECALL, null) ?: return null
        return try {
            val session = dailyRecallAdapter.fromJson(json)
            if (session != null && session.expiresAt > System.currentTimeMillis()) {
                session
            } else {
                clearDailyRecallSession()
                null
            }
        } catch (e: Exception) {
            clearDailyRecallSession()
            null
        }
    }

    fun clearDailyRecallSession() {
        prefs.edit().remove(KEY_DAILY_RECALL).apply()
    }

    // ==========================================
    // 2. Word Vault Quiz Cache
    // ==========================================
    fun saveWordVaultSession(session: CachedWordVaultSession) {
        val json = wordVaultAdapter.toJson(session)
        prefs.edit().putString(KEY_WORD_VAULT, json).apply()
    }

    fun getWordVaultSession(): CachedWordVaultSession? {
        val json = prefs.getString(KEY_WORD_VAULT, null) ?: return null
        return try {
            val session = wordVaultAdapter.fromJson(json)
            if (session != null && session.expiresAt > System.currentTimeMillis()) {
                session
            } else {
                clearWordVaultSession()
                null
            }
        } catch (e: Exception) {
            clearWordVaultSession()
            null
        }
    }

    fun clearWordVaultSession() {
        prefs.edit().remove(KEY_WORD_VAULT).apply()
    }

    // ==========================================
    // 3. Chapter Mastery Quiz Cache
    // ==========================================
    fun saveChapterMasterySession(session: CachedChapterMasterySession) {
        val key = "$KEY_PREFIX_CHAPTER_MASTERY${session.chapterId}"
        val json = chapterMasteryAdapter.toJson(session)
        prefs.edit().putString(key, json).apply()
    }

    fun getChapterMasterySession(chapterId: Long): CachedChapterMasterySession? {
        val key = "$KEY_PREFIX_CHAPTER_MASTERY$chapterId"
        val json = prefs.getString(key, null) ?: return null
        return try {
            val session = chapterMasteryAdapter.fromJson(json)
            if (session != null && session.expiresAt > System.currentTimeMillis()) {
                session
            } else {
                clearChapterMasterySession(chapterId)
                null
            }
        } catch (e: Exception) {
            clearChapterMasterySession(chapterId)
            null
        }
    }

    fun clearChapterMasterySession(chapterId: Long) {
        val key = "$KEY_PREFIX_CHAPTER_MASTERY$chapterId"
        prefs.edit().remove(key).apply()
    }
}
