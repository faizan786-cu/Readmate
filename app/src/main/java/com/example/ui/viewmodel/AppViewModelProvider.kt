package com.example.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ReadMateApplication

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            DashboardViewModel(
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                chapterMessageRepository = app.chapterMessageRepository,
                wisdomQuoteRepository = app.wisdomQuoteRepository,
                wordVaultRepository = app.wordVaultRepository,
                mcqRepository = app.mcqRepository,
                userGamificationRepository = app.userGamificationRepository,
                dailyRetentionManager = app.dailyRetentionManager,
                quizRepository = app.quizRepository,
                wordQuizRepository = app.wordQuizRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            LibraryViewModel(app.bookRepository)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            BookDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                chapterMessageRepository = app.chapterMessageRepository,
                dailyRetentionManager = app.dailyRetentionManager,
                quizRepository = app.quizRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            ChapterListViewModel(
                savedStateHandle = createSavedStateHandle(),
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                chapterMessageRepository = app.chapterMessageRepository,
                dailyRetentionManager = app.dailyRetentionManager,
                quizRepository = app.quizRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            AddBookViewModel(
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                secureApiKeyStorage = app.secureApiKeyStorage,
                application = app
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            BookFormViewModel(
                savedStateHandle = createSavedStateHandle(),
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                secureApiKeyStorage = app.secureApiKeyStorage,
                application = app
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            ChapterFormViewModel(
                savedStateHandle = createSavedStateHandle(),
                chapterRepository = app.chapterRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            ChapterChatViewModel(
                savedStateHandle = createSavedStateHandle(),
                chapterRepository = app.chapterRepository,
                bookRepository = app.bookRepository,
                chapterMessageRepository = app.chapterMessageRepository,
                geminiRepository = app.geminiRepository,
                wordVaultRepository = app.wordVaultRepository,
                quoteExtractionEngine = app.quoteExtractionEngine,
                explanationPipelineManager = app.explanationPipelineManager,
                userPreferencesRepository = app.userPreferencesRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            WordVaultViewModel(
                savedStateHandle = createSavedStateHandle(),
                wordVaultRepository = app.wordVaultRepository,
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                chapterMessageRepository = app.chapterMessageRepository,
                geminiRepository = app.geminiRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            SettingsViewModel(
                geminiRepository = app.geminiRepository,
                userPreferencesRepository = app.userPreferencesRepository,
                database = app.database,
                context = app.applicationContext
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            GeminiConfigViewModel(app.geminiRepository)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            WisdomReelsViewModel(
                savedStateHandle = createSavedStateHandle(),
                wisdomQuoteRepository = app.wisdomQuoteRepository,
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                wordVaultRepository = app.wordVaultRepository,
                geminiRepository = app.geminiRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            DailyRecallViewModel(
                mcqRepository = app.mcqRepository,
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                userGamificationRepository = app.userGamificationRepository,
                wordVaultRepository = app.wordVaultRepository,
                quizSessionCacheManager = app.quizSessionCacheManager,
                dailyRetentionManager = app.dailyRetentionManager,
                quizRepository = app.quizRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            ProgressViewModel(
                mcqRepository = app.mcqRepository,
                bookRepository = app.bookRepository,
                chapterRepository = app.chapterRepository,
                userGamificationRepository = app.userGamificationRepository,
                wordQuizRepository = app.wordQuizRepository,
                chapterMessageRepository = app.chapterMessageRepository
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            WordVaultQuizViewModel(
                wordVaultRepository = app.wordVaultRepository,
                wordVaultQuizEngine = app.wordVaultQuizEngine,
                userGamificationRepository = app.userGamificationRepository,
                wordQuizRepository = app.wordQuizRepository,
                mcqRepository = app.mcqRepository,
                quizSessionCacheManager = app.quizSessionCacheManager
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            ChapterMasteryViewModel(
                savedStateHandle = createSavedStateHandle(),
                chapterRepository = app.chapterRepository,
                bookRepository = app.bookRepository,
                mcqRepository = app.mcqRepository,
                userGamificationRepository = app.userGamificationRepository,
                quizSessionCacheManager = app.quizSessionCacheManager
            )
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ReadMateApplication)
            AuthViewModel(
                authRepository = app.authRepository
            )
        }
    }
}
