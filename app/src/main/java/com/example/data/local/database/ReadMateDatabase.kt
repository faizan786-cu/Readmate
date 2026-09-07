package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.database.dao.BookDao
import com.example.data.local.database.dao.ChapterDao
import com.example.data.local.database.dao.ChapterMessageDao
import com.example.data.local.database.dao.DailyRetentionStateDao
import com.example.data.local.database.dao.McqQuestionDao
import com.example.data.local.database.dao.QuizDao
import com.example.data.local.database.dao.UserGamificationDao
import com.example.data.local.database.dao.UserMcqAttemptDao
import com.example.data.local.database.dao.WisdomQuoteDao
import com.example.data.local.database.dao.WordQuizAttemptDao
import com.example.data.local.database.dao.WordVaultDao
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.local.database.entity.ChapterMessage
import com.example.data.local.database.entity.DailyRetentionStateEntity
import com.example.data.local.database.entity.McqQuestion
import com.example.data.local.database.entity.QuizEntity
import com.example.data.local.database.entity.UserGamificationStats
import com.example.data.local.database.entity.UserMcqAttempt
import com.example.data.local.database.entity.WisdomQuote
import com.example.data.local.database.entity.WordQuizAttempt
import com.example.data.local.database.entity.WordVaultEntry

@Database(
    entities = [
        Book::class,
        Chapter::class,
        ChapterMessage::class,
        WordVaultEntry::class,
        WisdomQuote::class,
        McqQuestion::class,
        UserMcqAttempt::class,
        UserGamificationStats::class,
        WordQuizAttempt::class,
        QuizEntity::class,
        DailyRetentionStateEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ReadMateDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterMessageDao(): ChapterMessageDao
    abstract fun wordVaultDao(): WordVaultDao
    abstract fun wisdomQuoteDao(): WisdomQuoteDao
    abstract fun mcqQuestionDao(): McqQuestionDao
    abstract fun userMcqAttemptDao(): UserMcqAttemptDao
    abstract fun userGamificationDao(): UserGamificationDao
    abstract fun wordQuizAttemptDao(): WordQuizAttemptDao
    abstract fun quizDao(): QuizDao
    abstract fun dailyRetentionStateDao(): DailyRetentionStateDao

    /**
     * Asynchronously wipes all local database tables, resets gamification stats to 0,
     * and clears cached PDF files and exported temporary files on Dispatchers.IO.
     */
    suspend fun clearAllData(context: Context? = null) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runInTransaction {
            val db = openHelper.writableDatabase
            db.execSQL("PRAGMA foreign_keys = OFF")
            try {
                db.execSQL("DELETE FROM `quiz_bank`")
                db.execSQL("DELETE FROM `daily_retention_state`")
                db.execSQL("DELETE FROM `word_quiz_attempts`")
                db.execSQL("DELETE FROM `user_mcq_attempts`")
                db.execSQL("DELETE FROM `mcq_questions`")
                db.execSQL("DELETE FROM `wisdom_quotes`")
                db.execSQL("DELETE FROM `word_vault`")
                db.execSQL("DELETE FROM `chapter_messages`")
                db.execSQL("DELETE FROM `chapters`")
                db.execSQL("DELETE FROM `books`")
                db.execSQL("DELETE FROM `user_gamification_stats`")
                db.execSQL("INSERT OR REPLACE INTO `user_gamification_stats` (`id`, `totalXp`, `todaySnippetQuizCompleted`, `todayWordQuizCompleted`, `lastActiveDate`, `updatedAt`) VALUES (1, 0, 0, 0, '', 0)")
            } finally {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        if (context != null) {
            try {
                val pdfDir = java.io.File(context.filesDir, "readmate_pdfs")
                if (pdfDir.exists()) {
                    pdfDir.deleteRecursively()
                }
            } catch (_: Exception) {}
            try {
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            } catch (_: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ReadMateDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wisdom_quotes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `bookTitle` TEXT NOT NULL,
                        `author` TEXT,
                        `chapterId` INTEGER NOT NULL,
                        `chapterNumber` INTEGER NOT NULL,
                        `chapterTitle` TEXT NOT NULL,
                        `englishQuote` TEXT NOT NULL,
                        `romanUrduPunchline` TEXT NOT NULL,
                        `themeTag` TEXT NOT NULL,
                        `messageId` INTEGER,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wisdom_quotes_bookId` ON `wisdom_quotes` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wisdom_quotes_chapterId` ON `wisdom_quotes` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wisdom_quotes_messageId` ON `wisdom_quotes` (`messageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wisdom_quotes_isFavorite` ON `wisdom_quotes` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wisdom_quotes_createdAt` ON `wisdom_quotes` (`createdAt`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `coverImageUrl` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mcq_questions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `chapterId` INTEGER NOT NULL,
                        `passageTurnId` INTEGER NOT NULL,
                        `questionText` TEXT NOT NULL,
                        `optionA` TEXT NOT NULL,
                        `optionB` TEXT NOT NULL,
                        `optionC` TEXT NOT NULL,
                        `optionD` TEXT NOT NULL,
                        `correctOption` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcq_questions_bookId` ON `mcq_questions` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcq_questions_chapterId` ON `mcq_questions` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcq_questions_passageTurnId` ON `mcq_questions` (`passageTurnId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcq_questions_createdAt` ON `mcq_questions` (`createdAt`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_mcq_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER,
                        `chapterId` INTEGER,
                        `attemptDate` INTEGER NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `correctAnswers` INTEGER NOT NULL,
                        `scorePercentage` REAL NOT NULL,
                        `wrongAnswersJson` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mcq_attempts_bookId` ON `user_mcq_attempts` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mcq_attempts_chapterId` ON `user_mcq_attempts` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mcq_attempts_attemptDate` ON `user_mcq_attempts` (`attemptDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mcq_attempts_createdAt` ON `user_mcq_attempts` (`createdAt`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_gamification_stats` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `totalXp` INTEGER NOT NULL DEFAULT 0,
                        `todaySnippetQuizCompleted` INTEGER NOT NULL DEFAULT 0,
                        `todayWordQuizCompleted` INTEGER NOT NULL DEFAULT 0,
                        `lastActiveDate` TEXT NOT NULL DEFAULT '',
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("INSERT OR IGNORE INTO `user_gamification_stats` (`id`, `totalXp`, `todaySnippetQuizCompleted`, `todayWordQuizCompleted`, `lastActiveDate`, `updatedAt`) VALUES (1, 0, 0, 0, '', 0)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `word_quiz_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `attemptDate` INTEGER NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `correctAnswers` INTEGER NOT NULL,
                        `recoveredCount` INTEGER NOT NULL DEFAULT 0,
                        `xpEarned` INTEGER NOT NULL DEFAULT 0,
                        `scorePercentage` REAL NOT NULL,
                        `wrongAnswersJson` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_quiz_attempts_attemptDate` ON `word_quiz_attempts` (`attemptDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_quiz_attempts_createdAt` ON `word_quiz_attempts` (`createdAt`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mcq_questions` ADD COLUMN `mistakeCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `mcq_questions` ADD COLUMN `isFlaggedForSpacedReview` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `mcq_questions` ADD COLUMN `lastFailedTimestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mcq_questions_isFlaggedForSpacedReview` ON `mcq_questions` (`isFlaggedForSpacedReview`)")

                db.execSQL("ALTER TABLE `word_vault` ADD COLUMN `mistakeCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `word_vault` ADD COLUMN `isFlaggedForSpacedReview` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `word_vault` ADD COLUMN `lastFailedTimestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_vault_isFlaggedForSpacedReview` ON `word_vault` (`isFlaggedForSpacedReview`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `masteryScore` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `isMastered` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `masteredAt` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chapters_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `chapterNumber` INTEGER,
                        `sectionType` TEXT NOT NULL DEFAULT 'CORE_CHAPTER',
                        `startPage` INTEGER NOT NULL DEFAULT 1,
                        `endPage` INTEGER NOT NULL DEFAULT 1,
                        `title` TEXT NOT NULL,
                        `pdfFilePath` TEXT,
                        `pdfFileName` TEXT,
                        `pdfTotalPages` INTEGER NOT NULL DEFAULT 0,
                        `pdfLastReadPage` INTEGER NOT NULL DEFAULT 0,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `completedAt` INTEGER NOT NULL DEFAULT 0,
                        `masteryScore` INTEGER,
                        `isMastered` INTEGER NOT NULL DEFAULT 0,
                        `masteredAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `chapters_new` (`id`, `bookId`, `chapterNumber`, `sectionType`, `startPage`, `endPage`, `title`, `pdfFilePath`, `pdfFileName`, `pdfTotalPages`, `pdfLastReadPage`, `isCompleted`, `completedAt`, `masteryScore`, `isMastered`, `masteredAt`, `createdAt`, `updatedAt`)
                    SELECT `id`, `bookId`, `chapterNumber`, 'CORE_CHAPTER', 1, CASE WHEN `pdfTotalPages` > 0 THEN `pdfTotalPages` ELSE 1 END, `title`, `pdfFilePath`, `pdfFileName`, `pdfTotalPages`, `pdfLastReadPage`, `isCompleted`, `completedAt`, `masteryScore`, `isMastered`, `masteredAt`, `createdAt`, `updatedAt` FROM `chapters`
                """.trimIndent())
                db.execSQL("DROP TABLE `chapters`")
                db.execSQL("ALTER TABLE `chapters_new` RENAME TO `chapters`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_bank` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `chapterId` INTEGER NOT NULL,
                        `question` TEXT NOT NULL,
                        `options` TEXT NOT NULL,
                        `correctAnswerIndex` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `sourceSnippet` TEXT NOT NULL,
                        `createdAtDate` TEXT NOT NULL,
                        `lastServedDate` TEXT,
                        `timesServed` INTEGER NOT NULL DEFAULT 0,
                        `isFromMistakeBank` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_bookId` ON `quiz_bank` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_chapterId` ON `quiz_bank` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_status` ON `quiz_bank` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_createdAtDate` ON `quiz_bank` (`createdAtDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_lastServedDate` ON `quiz_bank` (`lastServedDate`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_retention_state` (
                        `date` TEXT PRIMARY KEY NOT NULL,
                        `quizzesCompleted` INTEGER NOT NULL DEFAULT 0,
                        `totalQuizzesServed` INTEGER NOT NULL DEFAULT 0,
                        `correctCount` INTEGER NOT NULL DEFAULT 0,
                        `retentionIndex` REAL NOT NULL DEFAULT 100.0,
                        `streakStatus` TEXT NOT NULL DEFAULT 'ACTIVE',
                        `streakDays` INTEGER NOT NULL DEFAULT 0,
                        `isFirstDayGracePeriod` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): ReadMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadMateDatabase::class.java,
                    "readmate_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
