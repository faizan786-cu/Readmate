package com.example.data.manager

import com.example.data.local.database.entity.WordVaultEntry
import com.example.data.local.database.model.WordMistakeRecord

sealed interface WordQuizAvailability {
    data class Ready(val totalWords: Int) : WordQuizAvailability
    data class InsufficientWords(
        val currentCount: Int,
        val requiredCount: Int = 4,
        val message: String = "Save at least 4 words in your vault to unlock Word Quizzes"
    ) : WordQuizAvailability
}

data class WordQuizOption(
    val id: String, // "A", "B", "C", "D"
    val text: String,
    val isCorrect: Boolean
)

data class WordQuizQuestion(
    val wordId: Long,
    val word: String,
    val originalSentence: String = "",
    val correctMeaning: String,
    val romanUrduExplanation: String = "",
    val options: List<WordQuizOption>,
    val isRetry: Boolean = false
)

class WordVaultQuizEngine {

    companion object {
        const val REQUIRED_WORDS_MINIMUM = 4
        const val DEFAULT_SESSION_SIZE = 10
        const val PRIMARY_CORRECT_XP = 10
        const val RETRY_CORRECT_XP = 5
        const val DAILY_DOUBLE_BONUS_XP = 50
    }

    /**
     * Checks whether the user has saved enough words to form 4-choice MCQs offline.
     */
    fun checkAvailability(allWords: List<WordVaultEntry>): WordQuizAvailability {
        return if (allWords.size >= REQUIRED_WORDS_MINIMUM) {
            WordQuizAvailability.Ready(totalWords = allWords.size)
        } else {
            WordQuizAvailability.InsufficientWords(
                currentCount = allWords.size,
                requiredCount = REQUIRED_WORDS_MINIMUM,
                message = "Save at least $REQUIRED_WORDS_MINIMUM words in your vault to unlock Word Quizzes"
            )
        }
    }

    /**
     * Generates a 100% offline MCQ session strictly from local Room word vault entries.
     */
    fun generateQuizSession(
        allWords: List<WordVaultEntry>,
        targetCount: Int = DEFAULT_SESSION_SIZE
    ): List<WordQuizQuestion> {
        if (allWords.size < REQUIRED_WORDS_MINIMUM) {
            return emptyList()
        }

        val targetWords = allWords.shuffled().take(targetCount)
        val questions = mutableListOf<WordQuizQuestion>()

        for (target in targetWords) {
            val correctMeaningText = target.meaning.trim().ifBlank {
                target.contextMeaning.trim().ifBlank {
                    target.explanation.trim()
                }
            }

            // Generate intelligent local distractors from other distinct words in the vault
            val otherEntries = allWords.filter {
                it.id != target.id &&
                !it.normalizedWord.equals(target.normalizedWord, ignoreCase = true)
            }

            val candidateDistractors = otherEntries.mapNotNull { other ->
                val meaning = other.meaning.trim()
                if (meaning.isNotBlank() && !meaning.equals(correctMeaningText, ignoreCase = true)) {
                    meaning
                } else {
                    val fallback = other.contextMeaning.trim()
                    if (fallback.isNotBlank() && !fallback.equals(correctMeaningText, ignoreCase = true)) {
                        fallback
                    } else null
                }
            }.distinct()

            val selectedDistractors = candidateDistractors.shuffled().take(3).toMutableList()

            // If fewer than 3 unique meanings found, backfill from other descriptions
            if (selectedDistractors.size < 3) {
                val backfills = otherEntries.mapNotNull { other ->
                    val phrase = other.phraseOrIdiomExplanation.trim()
                    if (phrase.isNotBlank() && phrase != correctMeaningText && !selectedDistractors.contains(phrase)) {
                        phrase
                    } else {
                        val ex = other.explanation.trim()
                        if (ex.isNotBlank() && ex != correctMeaningText && !selectedDistractors.contains(ex)) {
                            ex
                        } else null
                    }
                }.distinct()

                for (bf in backfills) {
                    if (selectedDistractors.size >= 3) break
                    selectedDistractors.add(bf)
                }
            }

            // Build options and shuffle placement
            val rawOptions = mutableListOf<WordQuizOption>()
            rawOptions.add(WordQuizOption(id = "", text = correctMeaningText, isCorrect = true))
            for (distractor in selectedDistractors) {
                rawOptions.add(WordQuizOption(id = "", text = distractor, isCorrect = false))
            }

            // If still somehow fewer than 4 (e.g. extreme duplicates), ensure at least whatever distractors exist
            val shuffled = rawOptions.shuffled()
            val finalOptions = shuffled.mapIndexed { index, option ->
                val letter = ('A' + index).toString()
                option.copy(id = letter)
            }

            val romanUrdu = target.explanation.ifBlank {
                target.phraseOrIdiomExplanation.ifBlank {
                    target.contextMeaning
                }
            }

            questions.add(
                WordQuizQuestion(
                    wordId = target.id,
                    word = target.word,
                    originalSentence = target.originalSentence,
                    correctMeaning = correctMeaningText,
                    romanUrduExplanation = romanUrdu,
                    options = finalOptions,
                    isRetry = false
                )
            )
        }

        return questions
    }

    /**
     * Reshuffles option positions for the mistake retry round to ensure options
     * aren't memorized by simple position.
     */
    fun reshuffleQuestionForRetry(question: WordQuizQuestion): WordQuizQuestion {
        val reshuffledOptions = question.options.shuffled().mapIndexed { index, opt ->
            val letter = ('A' + index).toString()
            opt.copy(id = letter)
        }
        return question.copy(
            options = reshuffledOptions,
            isRetry = true
        )
    }

    /**
     * Calculates base session XP (+10 for primary correct, +5 for recovered retry).
     */
    fun calculateSessionXp(primaryCorrect: Int, retryRecovered: Int): Int {
        return (primaryCorrect * PRIMARY_CORRECT_XP) + (retryRecovered * RETRY_CORRECT_XP)
    }
}
