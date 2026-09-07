package com.example

import android.app.Application
import com.example.data.local.database.ReadMateDatabase
import com.example.data.local.security.AndroidKeystoreApiKeyStorage
import com.example.data.local.security.AndroidKeystoreAuthSessionStorage
import com.example.data.local.security.AuthSessionStorage
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.DailyRetentionManager
import com.example.data.manager.ExplanationPipelineManager
import com.example.data.manager.McqGenerationEngine
import com.example.data.manager.QuoteExtractionEngine
import com.example.data.manager.QuizSessionCacheManager
import com.example.data.manager.SessionChallengeManager
import com.example.data.manager.WordVaultQuizEngine
import com.example.data.remote.auth.AuthApiService
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.notification.NotificationHelper
import com.example.data.repository.AuthRepository
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterMessageRepository
import com.example.data.repository.ChapterRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.McqRepository
import com.example.data.repository.QuizRepository
import com.example.data.repository.UserGamificationRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WisdomQuoteRepository
import com.example.data.repository.WordQuizRepository
import com.example.data.repository.WordVaultRepository
import com.example.data.worker.RetentionWorkerScheduler
import com.example.data.worker.WisdomReminderScheduler

class ReadMateApplication : Application() {
    val database: ReadMateDatabase by lazy { ReadMateDatabase.getDatabase(this) }
    val userPreferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao(), quizDao = database.quizDao()) }
    val chapterRepository: ChapterRepository by lazy { ChapterRepository(database.chapterDao()) }
    val chapterMessageRepository: ChapterMessageRepository by lazy { ChapterMessageRepository(database.chapterMessageDao()) }
    val wordVaultRepository: WordVaultRepository by lazy { WordVaultRepository(database.wordVaultDao()) }
    val wisdomQuoteRepository: WisdomQuoteRepository by lazy { WisdomQuoteRepository(database.wisdomQuoteDao()) }
    val mcqRepository: McqRepository by lazy {
        McqRepository(
            mcqQuestionDao = database.mcqQuestionDao(),
            userMcqAttemptDao = database.userMcqAttemptDao()
        )
    }
    val quizRepository: QuizRepository by lazy {
        QuizRepository(database.quizDao())
    }
    val dailyRetentionManager: DailyRetentionManager by lazy {
        DailyRetentionManager(
            dailyRetentionStateDao = database.dailyRetentionStateDao(),
            quizRepository = quizRepository
        )
    }
    val userGamificationRepository: UserGamificationRepository by lazy {
        UserGamificationRepository(database.userGamificationDao())
    }
    val wordQuizRepository: WordQuizRepository by lazy {
        WordQuizRepository(database.wordQuizAttemptDao())
    }
    val wordVaultQuizEngine: WordVaultQuizEngine by lazy {
        WordVaultQuizEngine()
    }
    val sessionChallengeManager: SessionChallengeManager by lazy {
        SessionChallengeManager()
    }
    val quizSessionCacheManager: QuizSessionCacheManager by lazy {
        QuizSessionCacheManager(this)
    }
    val secureApiKeyStorage: SecureApiKeyStorage by lazy { AndroidKeystoreApiKeyStorage(this) }
    val authSessionStorage: AuthSessionStorage by lazy { AndroidKeystoreAuthSessionStorage(this) }
    val authApiService: AuthApiService by lazy { AuthApiService.create() }
    val authRepository: AuthRepository by lazy { AuthRepository(authApiService = authApiService, authSessionStorage = authSessionStorage) }
    val geminiRepository: GeminiRepository by lazy { GeminiRepository(secureApiKeyStorage) }
    val quoteExtractionEngine: QuoteExtractionEngine by lazy {
        QuoteExtractionEngine(
            secureStorage = secureApiKeyStorage,
            apiService = GeminiApiService.create(),
            wisdomQuoteRepository = wisdomQuoteRepository
        )
    }
    val mcqGenerationEngine: McqGenerationEngine by lazy {
        McqGenerationEngine(
            secureStorage = secureApiKeyStorage,
            apiService = GeminiApiService.create(),
            mcqRepository = mcqRepository,
            quizRepository = quizRepository
        )
    }
    val explanationPipelineManager: ExplanationPipelineManager by lazy {
        ExplanationPipelineManager(
            geminiRepository = geminiRepository,
            chapterMessageRepository = chapterMessageRepository,
            bookRepository = bookRepository,
            chapterRepository = chapterRepository,
            quoteExtractionEngine = quoteExtractionEngine,
            mcqGenerationEngine = mcqGenerationEngine
        )
    }
    val inAppNotificationManager: com.example.data.manager.InAppNotificationManager by lazy {
        com.example.data.manager.InAppNotificationManager(
            context = this,
            quizRepository = quizRepository,
            dailyRetentionManager = dailyRetentionManager,
            wordVaultRepository = wordVaultRepository,
            bookRepository = bookRepository,
            userPreferencesRepository = userPreferencesRepository,
            userGamificationRepository = userGamificationRepository,
            chapterMessageRepository = chapterMessageRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        try {
            NotificationHelper.createRetentionNotificationChannel(this)
        } catch (_: Exception) {}

        try {
            WisdomReminderScheduler.scheduleDailyReminders(this)
        } catch (_: Exception) {}

        try {
            RetentionWorkerScheduler.scheduleAllWorkers(this)
        } catch (_: Exception) {}
    }
}

