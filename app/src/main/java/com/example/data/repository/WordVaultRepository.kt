package com.example.data.repository

import com.example.data.local.database.dao.WordVaultDao
import com.example.data.local.database.entity.WordVaultEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WordVaultRepository(
    private val wordVaultDao: WordVaultDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val allWords: Flow<List<WordVaultEntry>> = wordVaultDao.observeAllWords()

    val totalWordCount: Flow<Int> = wordVaultDao.observeTotalWordCount()

    fun observeWordsForBook(bookId: Long): Flow<List<WordVaultEntry>> {
        return wordVaultDao.observeWordsForBook(bookId)
    }

    fun observeWordsForChapter(chapterId: Long): Flow<List<WordVaultEntry>> {
        return wordVaultDao.observeWordsForChapter(chapterId)
    }

    fun observeWordsForMessage(messageId: Long): Flow<List<WordVaultEntry>> {
        return wordVaultDao.observeWordsForMessage(messageId)
    }

    suspend fun findExistingWordEntry(chapterId: Long, word: String, sentence: String? = null, messageId: Long? = null): WordVaultEntry? = withContext(ioDispatcher) {
        val normalized = word.trim().lowercase()
        if (messageId != null) {
            wordVaultDao.getEntryByWordInMessage(messageId, normalized)
        } else if (!sentence.isNullOrBlank()) {
            wordVaultDao.getEntryByWordAndSentenceInChapter(chapterId, normalized, sentence.trim())
                ?: wordVaultDao.getEntryByWordInChapter(chapterId, normalized)
        } else {
            wordVaultDao.getEntryByWordInChapter(chapterId, normalized)
        }
    }

    /**
     * Finds a saved WordVaultEntry across all books/chapters that contextually matches the selected word
     * and sentence context.
     * 1. Checks matching normalizedWord and exact/similar sentence context.
     * 2. If no exact sentence match, checks if words in sentence overlap significantly with saved sentence.
     * 3. Returns the best contextual match or null if it's a new contextual meaning.
     */
    suspend fun findMatchingContextEntry(
        word: String,
        sentence: String,
        currentChapterId: Long? = null,
        currentMessageId: Long? = null
    ): WordVaultEntry? = withContext(ioDispatcher) {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return@withContext null

        val entries = wordVaultDao.getEntriesByNormalizedWord(normalized)
        if (entries.isEmpty()) return@withContext null

        // 1. If in the current message
        if (currentMessageId != null && currentMessageId > 0) {
            val inMessage = entries.firstOrNull { it.messageId == currentMessageId }
            if (inMessage != null) return@withContext inMessage
        }

        // 2. Exact sentence match in current chapter or anywhere
        val cleanSentence = sentence.trim()
        if (cleanSentence.isNotBlank()) {
            // First check same chapter exact sentence
            if (currentChapterId != null && currentChapterId > 0) {
                val exactInChapter = entries.firstOrNull {
                    it.chapterId == currentChapterId && it.originalSentence.equals(cleanSentence, ignoreCase = true)
                }
                if (exactInChapter != null) return@withContext exactInChapter
            }

            // Check exact sentence match in any chapter
            val exactAnywhere = entries.firstOrNull {
                it.originalSentence.equals(cleanSentence, ignoreCase = true)
            }
            if (exactAnywhere != null) return@withContext exactAnywhere

            // Context similarity matching: if sentences share majority of significant words (length > 3)
            val currentWords = cleanSentence.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }.toSet()
            if (currentWords.isNotEmpty()) {
                val bestMatch = entries.firstOrNull { entry ->
                    val entryWords = entry.originalSentence.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }.toSet()
                    val overlap = currentWords.intersect(entryWords)
                    // If at least 2 significant words overlap or sentence contains the entry's original sentence
                    overlap.size >= 2 || cleanSentence.contains(entry.originalSentence, ignoreCase = true) || entry.originalSentence.contains(cleanSentence, ignoreCase = true)
                }
                if (bestMatch != null) return@withContext bestMatch
            }
        }

        // 3. If there is a saved entry in the current chapter
        if (currentChapterId != null && currentChapterId > 0) {
            val inChapter = entries.firstOrNull { it.chapterId == currentChapterId }
            if (inChapter != null) return@withContext inChapter
        }

        // 4. Fallback to the most recent entry for this word if only one contextual meaning exists
        if (entries.size == 1) {
            return@withContext entries.first()
        }

        entries.firstOrNull()
    }

    suspend fun saveOrUpdateWord(entry: WordVaultEntry): Long = withContext(ioDispatcher) {
        val normalized = entry.word.trim().lowercase()
        val existing = if (entry.messageId != null) {
            wordVaultDao.getEntryByWordInMessage(entry.messageId, normalized)
        } else if (entry.originalSentence.isNotBlank()) {
            wordVaultDao.getEntryByWordAndSentenceInChapter(entry.chapterId, normalized, entry.originalSentence.trim())
                ?: wordVaultDao.getEntryByWordInChapter(entry.chapterId, normalized)
        } else {
            wordVaultDao.getEntryByWordInChapter(entry.chapterId, normalized)
        }

        if (existing != null) {
            // Update existing entry with latest context and details
            val updated = existing.copy(
                word = entry.word,
                meaning = entry.meaning.ifBlank { existing.meaning },
                contextMeaning = entry.contextMeaning.ifBlank { existing.contextMeaning },
                originalSentence = entry.originalSentence.ifBlank { existing.originalSentence },
                explanation = entry.explanation.ifBlank { existing.explanation },
                phraseOrIdiomExplanation = entry.phraseOrIdiomExplanation.ifBlank { existing.phraseOrIdiomExplanation },
                simpleExample = entry.simpleExample.ifBlank { existing.simpleExample },
                exampleMeaning = entry.exampleMeaning.ifBlank { existing.exampleMeaning },
                messageId = entry.messageId ?: existing.messageId,
                createdAt = System.currentTimeMillis()
            )
            wordVaultDao.updateEntry(updated)
            existing.id
        } else {
            wordVaultDao.insertEntry(entry.copy(normalizedWord = normalized))
        }
    }

    suspend fun saveOrUpdateWords(entries: List<WordVaultEntry>) = withContext(ioDispatcher) {
        entries.forEach { entry ->
            saveOrUpdateWord(entry)
        }
    }

    suspend fun deleteWord(entry: WordVaultEntry) = withContext(ioDispatcher) {
        wordVaultDao.deleteEntry(entry)
    }

    suspend fun deleteWordById(id: Long) = withContext(ioDispatcher) {
        wordVaultDao.deleteEntryById(id)
    }

    suspend fun getFlaggedMistakeWords(limit: Int): List<WordVaultEntry> = withContext(ioDispatcher) {
        wordVaultDao.getFlaggedMistakeWords(limit)
    }

    fun observeFlaggedMistakeWords(): Flow<List<WordVaultEntry>> =
        wordVaultDao.observeFlaggedMistakeWords()

    fun observeFlaggedMistakeWordsCount(): Flow<Int> =
        wordVaultDao.observeFlaggedMistakeWordsCount()

    suspend fun updateWordMistakeStatus(wordId: Long, isFlagged: Boolean, incrementMistake: Int, timestamp: Long) = withContext(ioDispatcher) {
        wordVaultDao.updateWordMistakeStatus(wordId, isFlagged, incrementMistake, timestamp)
    }

    suspend fun resolveWordMistake(wordId: Long) = withContext(ioDispatcher) {
        wordVaultDao.resolveWordMistake(wordId)
    }

    suspend fun deleteWordsForChapter(chapterId: Long) = withContext(ioDispatcher) {
        wordVaultDao.deleteWordsForChapter(chapterId)
    }
}
