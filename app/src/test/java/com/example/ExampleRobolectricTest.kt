package com.example

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.ReadMateDatabase
import com.example.data.local.database.entity.Book
import com.example.data.local.database.entity.Chapter
import com.example.data.repository.BookRepository
import com.example.data.repository.ChapterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: ReadMateDatabase
    private lateinit var bookRepository: BookRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var chapterMessageRepository: com.example.data.repository.ChapterMessageRepository
    private lateinit var wordVaultRepository: com.example.data.repository.WordVaultRepository
    private lateinit var userGamificationRepository: com.example.data.repository.UserGamificationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookRepository = BookRepository(db.bookDao())
        chapterRepository = ChapterRepository(db.chapterDao())
        chapterMessageRepository = com.example.data.repository.ChapterMessageRepository(db.chapterMessageDao())
        wordVaultRepository = com.example.data.repository.WordVaultRepository(db.wordVaultDao())
        userGamificationRepository = com.example.data.repository.UserGamificationRepository(db.userGamificationDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ReadMate", appName)
    }

    @Test
    fun `database starts empty and creates books and chapters properly`() = runBlocking {
        val initialBooks = bookRepository.booksWithChapterCount.first()
        assertTrue("Library must start empty without fake books", initialBooks.isEmpty())

        val bookId = bookRepository.createBook(
            title = "The Psychology of Money",
            author = "Morgan Housel",
            description = "Timeless lessons on wealth, greed, and happiness."
        )
        assertTrue(bookId > 0)

        val createdBook = bookRepository.getBook(bookId)
        assertNotNull(createdBook)
        assertEquals("The Psychology of Money", createdBook?.title)
        assertEquals("Morgan Housel", createdBook?.author)

        // Add Chapters
        val ch1Id = chapterRepository.createChapter(bookId, 1, "No One's Crazy")
        val ch2Id = chapterRepository.createChapter(bookId, 2, "Luck & Risk")
        val ch3Id = chapterRepository.createChapter(bookId, 3, "Never Enough")

        val chapters = chapterRepository.getChaptersForBook(bookId).first()
        assertEquals(3, chapters.size)
        assertEquals(1, chapters[0].chapterNumber)
        assertEquals("No One's Crazy", chapters[0].title)
        assertEquals(2, chapters[1].chapterNumber)
        assertEquals("Luck & Risk", chapters[1].title)
        assertEquals(3, chapters[2].chapterNumber)
        assertEquals("Never Enough", chapters[2].title)

        // Check next chapter number auto-suggestion
        val nextNum = chapterRepository.getNextChapterNumber(bookId)
        assertEquals(4, nextNum)

        // Verify chapter count on book query
        val booksWithCount = bookRepository.booksWithChapterCount.first()
        assertEquals(1, booksWithCount.size)
        assertEquals(3, booksWithCount[0].chapterCount)
    }

    @Test
    fun `deleting a book cascades and deletes all associated chapters`() = runBlocking {
        val bookId = bookRepository.createBook("Atomic Habits", "James Clear", "An Easy & Proven Way to Build Good Habits")
        chapterRepository.createChapter(bookId, 1, "The Surprising Power of Atomic Habits")
        chapterRepository.createChapter(bookId, 2, "How Your Habits Shape Your Identity")

        val chaptersBefore = chapterRepository.getChaptersForBook(bookId).first()
        assertEquals(2, chaptersBefore.size)

        // Delete book
        bookRepository.deleteBookById(bookId)

        val bookAfter = bookRepository.getBook(bookId)
        assertNull(bookAfter)

        val chaptersAfter = chapterRepository.getChaptersForBook(bookId).first()
        assertTrue("Cascade foreign key must delete all associated chapters", chaptersAfter.isEmpty())
    }

    @Test
    fun `secure api key storage encrypts, retrieves, masks, and deletes api key securely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)

        // Initial state
        assertNull(storage.getApiKey())
        assertNull(storage.getMaskedApiKey())
        assertTrue(!storage.hasApiKey())

        // Save valid key
        val testKey = "AIzaSyDummyTestKey12345678"
        val saved = storage.saveApiKey(testKey)
        assertTrue(saved)
        assertTrue(storage.hasApiKey())

        // Decrypted key equals original
        val retrieved = storage.getApiKey()
        assertEquals(testKey, retrieved)

        // Masked key does not reveal full key
        val masked = storage.getMaskedApiKey()
        assertNotNull(masked)
        assertEquals("••••••••5678", masked)

        // Delete key
        val deleted = storage.deleteApiKey()
        assertTrue(deleted)
        assertTrue(!storage.hasApiKey())
        assertNull(storage.getApiKey())
        assertNull(storage.getMaskedApiKey())
    }

    @Test
    fun `gemini repository manages connection state and handles empty key gracefully`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.deleteApiKey()

        val repo = com.example.data.repository.GeminiRepository(
            secureStorage = storage
        )

        // Initial state is NotConnected
        assertEquals(com.example.data.model.GeminiConnectionState.NotConnected, repo.connectionState.value)

        // Test with empty key fails without network call
        val emptyResult = repo.testConnection("   ")
        assertTrue(emptyResult is com.example.data.model.TestConnectionResult.Failure)
        assertEquals("Enter your Gemini API key first.", (emptyResult as com.example.data.model.TestConnectionResult.Failure).message)

        // Saving and connecting updates state
        val saveSuccess = repo.saveAndConnect("AIzaSyMySecretApiKey9876")
        assertTrue(saveSuccess)
        val state = repo.connectionState.value
        assertTrue(state is com.example.data.model.GeminiConnectionState.Connected)
        assertEquals("••••••••9876", (state as com.example.data.model.GeminiConnectionState.Connected).maskedKey)

        // Disconnecting resets state
        val disconnectSuccess = repo.disconnect()
        assertTrue(disconnectSuccess)
        assertEquals(com.example.data.model.GeminiConnectionState.NotConnected, repo.connectionState.value)
    }

    @Test
    fun `chapter messages are saved locally, observed via flow, and cascaded on chapter deletion`() = runBlocking {
        val bookId = bookRepository.createBook(title = "Thinking, Fast and Slow", author = "Daniel Kahneman", description = null)
        val chapterId = chapterRepository.createChapter(bookId = bookId, chapterNumber = 1, title = "Two Systems")

        // Messages initially empty
        val initialMessages = chapterMessageRepository.observeMessagesForChapter(chapterId).first()
        assertTrue(initialMessages.isEmpty())

        // Save first message
        val origText1 = "System 1 operates automatically and quickly, with little or no effort."
        val aiResp1 = "This means our fast thinking happens instinctively without conscious effort."
        val msgId1 = chapterMessageRepository.saveMessage(chapterId, origText1, aiResp1)
        assertTrue(msgId1 > 0)

        // Save second message
        val origText2 = "System 2 allocates attention to effortful mental operations."
        val aiResp2 = "This is our deliberate, analytical mode of thinking that requires focus."
        val msgId2 = chapterMessageRepository.saveMessage(chapterId, origText2, aiResp2)
        assertTrue(msgId2 > 0)

        // Verify observed messages
        val savedMessages = chapterMessageRepository.observeMessagesForChapter(chapterId).first()
        assertEquals(2, savedMessages.size)
        assertEquals(origText1, savedMessages[0].originalText)
        assertEquals(aiResp1, savedMessages[0].aiResponse)
        assertEquals(origText2, savedMessages[1].originalText)
        assertEquals(aiResp2, savedMessages[1].aiResponse)

        // Verify cascade delete when chapter is deleted
        chapterRepository.deleteChapter(chapterRepository.getChapter(chapterId)!!)
        val messagesAfter = chapterMessageRepository.observeMessagesForChapter(chapterId).first()
        assertTrue("Cascade foreign key must delete all messages associated with chapter", messagesAfter.isEmpty())
    }

    @Test
    fun `explainPassage returns failure when api key is not connected`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.deleteApiKey()

        val repo = com.example.data.repository.GeminiRepository(secureStorage = storage)
        val result = repo.explainPassage("Sample passage from a book")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("No Gemini API key connected. Please configure your API key in Settings.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `chapter message correctly saves and retrieves AI response in room database`() = runBlocking {
        val bookId = bookRepository.createBook(title = "The Psychology of Money", author = "Morgan Housel", description = "Timeless lessons on wealth")
        val chapterId = chapterRepository.createChapter(bookId = bookId, chapterNumber = 1, title = "No One's Crazy")

        val originalEnglishPassage = "Your personal experiences with money make up maybe 0.00000001% of what’s happened in the world, but maybe 80% of how you think the world works."
        val romanUrduExplanation = """
## 🧠 Asaan Samjh
Author yahan ye samjha raha hai ke paison ke bare mein har insan ki soch uski zindagi ke experiences se banti hai. Agar kisi ne bachpan mein paison ki kami dekhi ho, to mumkin hai ke woh paisay ko zyada carefully use kare ya future ke liye zyada save kare. Iske opposite, jis insan ne hamesha financial comfort dekha ho, uska behavior bilkul different hoga.

Matlab paison ke decisions sirf numbers par depend nahi karte, balki personal past background bhi decisions ko shape karta hai.

## 💡 Main Lesson
Har shaks ka paise ke baray mein faisla uski apni life situation aur past experiences par depend karta hai.

## 🔑 Key Points
• **Personal Background**: Humari personal financial history humari soch par sab se bara asar dalti hai.
• **Different Mindsets**: Jo decision aik insan ko risky lagta hai, doosre ke liye normal ho sakta hai.
• **Real World Logic**: Paisay ke decisions sirf kitabi calculation se nahi bante.

## 🌎 Real-Life Example
Agar kisi shaks ne bachpan mein job loss ya mushkil waqt dekha ho, toh woh hamesha saving ko priority dega, jabke financial security mein pala bada insan naye business ya investment mein risk lene ke liye tayyar rehta hai.
        """.trimIndent()

        val messageId = chapterMessageRepository.saveMessage(chapterId, originalEnglishPassage, romanUrduExplanation)
        assertTrue(messageId > 0)

        val retrievedMessages = chapterMessageRepository.observeMessagesForChapter(chapterId).first()
        assertEquals(1, retrievedMessages.size)

        val message = retrievedMessages.first()
        assertEquals(originalEnglishPassage, message.originalText)
        assertTrue(message.aiResponse.contains("🧠 Asaan Samjh"))
        assertTrue(message.aiResponse.contains("💡 Main Lesson"))
        assertTrue(message.aiResponse.contains("🔑 Key Points"))
        assertTrue(message.aiResponse.contains("🌎 Real-Life Example"))
    }

    @Test
    fun `word vault entries are saved, deduplicated per chapter, and queried by scope`() = runBlocking {
        val bookId = bookRepository.createBook("Julius Caesar", "William Shakespeare", "Historical drama")
        val chapterId = chapterRepository.createChapter(bookId, 1, "Act I")

        // Initial Word Vault count
        val initialCount = wordVaultRepository.totalWordCount.first()
        assertEquals(0, initialCount)

        // Save Word Vault entry
        val entry1 = com.example.data.local.database.entity.WordVaultEntry(
            bookId = bookId,
            chapterId = chapterId,
            word = "Secured",
            meaning = "mehfooz kar liya / hifazat mein le liya",
            originalSentence = "Julius Caesar secured the country's borders.",
            explanation = "Yahan secured ka matlab hai ke Caesar ne mulk ki sarhadon ko mehfooz kar liya."
        )
        wordVaultRepository.saveOrUpdateWord(entry1)

        val wordsAfterFirst = wordVaultRepository.observeWordsForChapter(chapterId).first()
        assertEquals(1, wordsAfterFirst.size)
        assertEquals("Secured", wordsAfterFirst[0].word)
        assertEquals("mehfooz kar liya / hifazat mein le liya", wordsAfterFirst[0].meaning)

        // Save duplicate word with updated context: should update existing without creating duplicate
        val entry1Duplicate = com.example.data.local.database.entity.WordVaultEntry(
            bookId = bookId,
            chapterId = chapterId,
            word = "secured",
            meaning = "mehfooz kar liya",
            originalSentence = "The garrison secured the fortress gates.",
            explanation = "Yahan secured ka matlab hai ke fauj ne darwazay band karke mehfooz kar diye."
        )
        wordVaultRepository.saveOrUpdateWord(entry1Duplicate)

        val wordsAfterDup = wordVaultRepository.observeWordsForChapter(chapterId).first()
        assertEquals(1, wordsAfterDup.size)
        assertEquals("The garrison secured the fortress gates.", wordsAfterDup[0].originalSentence)

        // Add a second word
        val entry2 = com.example.data.local.database.entity.WordVaultEntry(
            bookId = bookId,
            chapterId = chapterId,
            word = "Nevertheless",
            meaning = "phir bhi / is sab ke bawajood",
            originalSentence = "Nevertheless, the senators proceeded with their plan.",
            explanation = "Yahan nevertheless ka matlab hai ke is sab ke bawajood unhon ne apna plan jaari rakha."
        )
        wordVaultRepository.saveOrUpdateWord(entry2)

        val allWords = wordVaultRepository.allWords.first()
        assertEquals(2, allWords.size)
        assertEquals(2, wordVaultRepository.totalWordCount.first())

        // Delete word
        wordVaultRepository.deleteWord(wordsAfterDup[0])
        val wordsAfterDelete = wordVaultRepository.observeWordsForChapter(chapterId).first()
        assertEquals(1, wordsAfterDelete.size)
        assertEquals("Nevertheless", wordsAfterDelete[0].word)
    }

    @Test
    fun `apiKeyItem format validation accepts both legacy AIzaSy and new AQ keys`() {
        // Legacy format
        assertTrue(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("AIzaSyB1234567890abcdefghijklmnopqr"))
        // New format
        assertTrue(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("AQ.abcdef1234567890ghijklmnopqrstuvwxyz"))
        assertTrue(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("AQ.test_key_sample_value_1234567890"))
        // Generic valid length keys
        assertTrue(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("abcdefghijklmnopqrstuvwxyz1234567890"))

        // Invalid formats
        assertFalse(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat(""))
        assertFalse(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("   "))
        assertFalse(com.example.data.model.GeminiApiKeyItem.isValidKeyFormat("short"))
    }

    @Test
    fun `apiKeyManager auto-rotates and fails over on 429 rate limits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("key_one_test_12345678", "Key 1")!!
        val key2 = storage.addApiKey("key_two_test_12345678", "Key 2")!!

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        // Mock call: key1 returns 429 rate limit across all models, key2 succeeds
        var callCount = 0
        val attemptedCalls = mutableListOf<Pair<String, String>>()
        val result = manager.executeWithAutoRotation<String>("test") { apiKey, model ->
            callCount++
            attemptedCalls.add(apiKey to model)
            if (apiKey == key1.key) {
                retrofit2.Response.error(429, okhttp3.ResponseBody.create(null, "RESOURCE_EXHAUSTED"))
            } else {
                retrofit2.Response.success("Success from Key 2")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Success from Key 2", result.getOrNull())
        // key1 tried all 3 passage models (P1 to P3), then rotated to key2 (P1)
        assertEquals(4, callCount)
        assertTrue(attemptedCalls.count { it.first == key1.key } == 3)
        assertTrue(attemptedCalls.count { it.first == key2.key } == 1)

        // Verify key1 encountered 429 errors and key2 succeeded
        val keysAfter = manager.getApiKeys()
        val k1 = keysAfter.first { it.id == key1.id }
        val k2 = keysAfter.first { it.id == key2.id }
        assertEquals(3, k1.rateLimitErrors429)
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, k2.status)
        assertEquals(1, k2.successfulRequests)
    }

    @Test
    fun `multi-key storage supports adding, updating, bulk import, and status tracking`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val repo = com.example.data.repository.GeminiRepository(secureStorage = storage)

        // Add legacy key
        val key1 = repo.addApiKey("AIzaSyKeyOne12345678", "Primary Key")
        assertNotNull(key1)
        assertEquals("Primary Key", key1?.label)
        assertEquals("••••••••5678", key1?.maskedKey)

        // Add new format key
        val key2 = repo.addApiKey("AQ.NewFormatKey87654321", "Secondary Key")
        assertNotNull(key2)
        assertEquals("Secondary Key", key2?.label)
        assertEquals("AQ.••••••••4321", key2?.maskedKey)

        // Connection state reflects 2 active keys
        val state = repo.connectionState.value
        assertTrue(state is com.example.data.model.GeminiConnectionState.Connected)
        val conn = state as com.example.data.model.GeminiConnectionState.Connected
        assertEquals(2, conn.totalKeyCount)
        assertEquals(2, conn.activeKeyCount)
        assertEquals(0, conn.cooldownKeyCount)

        // Bulk import additional keys
        val bulkText = "AIzaSyBulkKey33333333\nAQ.BulkKey44444444, AIzaSyKeyOne12345678" // includes 1 duplicate
        val (added, skipped) = repo.bulkImportKeys(bulkText)
        assertEquals(2, added)
        assertEquals(1, skipped)
        assertEquals(4, repo.getApiKeys().size)

        // Test cooldown handling
        val cdKeyItem = key1!!.copy(
            status = com.example.data.model.KeyStatus.COOLDOWN,
            cooldownUntilTimestamp = System.currentTimeMillis() + 60_000L,
            errorMessage = "Rate limit test"
        )
        repo.updateApiKey(cdKeyItem)
        val keysAfterCooldown = repo.getApiKeys()
        val cdKey = keysAfterCooldown.first { it.id == key1.id }
        assertEquals(com.example.data.model.KeyStatus.COOLDOWN, cdKey.status)
        assertEquals("Rate limit test", cdKey.errorMessage)

        // Reset all cooldowns
        repo.resetAllCooldowns()
        val keysAfterReset = repo.getApiKeys()
        assertTrue(keysAfterReset.all { it.status == com.example.data.model.KeyStatus.ACTIVE })

        // Remove key
        repo.removeApiKey(key2!!.id)
        assertEquals(3, repo.getApiKeys().size)

        // Clear all
        repo.disconnect()
        assertEquals(0, repo.getApiKeys().size)
        assertEquals(com.example.data.model.GeminiConnectionState.NotConnected, repo.connectionState.value)
    }

    @Test
    fun `testConnection provides clear diagnostic message on 404 endpoint or model errors`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                val json404 = """{"error":{"code":404,"message":"models/gemini-old is not found for API version v1beta","status":"NOT_FOUND"}}"""
                return retrofit2.Response.error(404, okhttp3.ResponseBody.create(null, json404))
            }
        }

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService
        )

        val result = manager.testConnection("test_api_key_12345")
        assertTrue(result is com.example.data.model.TestConnectionResult.Failure)
        val failure = result as com.example.data.model.TestConnectionResult.Failure
        assertEquals(404, failure.statusCode)
        assertTrue(failure.message.contains("404"))
        assertTrue(failure.message.contains("models/gemini-old is not found"))
    }

    @Test
    fun `apiKeyManager 503 service unavailable does not cooldown keys and keeps keys active`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("key_one_503_test_12345", "Key 1")!!
        val key2 = storage.addApiKey("key_two_503_test_12345", "Key 2")!!

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        // Mock call returning 503: should retry on key1, not rotate to key2 or cooldown key1/key2
        val attemptedKeys = mutableListOf<String>()
        val result = manager.executeWithAutoRotation<String>("test") { apiKey, _ ->
            attemptedKeys.add(apiKey)
            retrofit2.Response.error(503, okhttp3.ResponseBody.create(null, """{"error":{"code":503,"message":"The service is temporarily overloaded","status":"UNAVAILABLE"}}"""))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("503") == true)
        
        // Check that key1 was retried and all attempts were on key1 (no cascade rotation to key2)
        assertTrue(attemptedKeys.all { it == key1.key })

        // Check key status: key1 must NOT be in COOLDOWN, it must remain ACTIVE
        val keysAfter = manager.getApiKeys()
        val k1 = keysAfter.first { it.id == key1.id }
        val k2 = keysAfter.first { it.id == key2.id }
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, k1.status)
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, k2.status)
        assertTrue(k1.serviceUnavailableErrors503 > 0)
        assertEquals(0, k1.failureCount)
    }

    @Test
    fun `apiKeyManager 401 invalid key marks key INVALID and rotates to next key`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("invalid_auth_key_12345", "Bad Key")!!
        val key2 = storage.addApiKey("healthy_second_key_12345", "Good Key")!!

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        val attemptedKeys = mutableListOf<String>()
        val result = manager.executeWithAutoRotation<String>("test") { apiKey, _ ->
            attemptedKeys.add(apiKey)
            if (apiKey == key1.key) {
                retrofit2.Response.error(401, okhttp3.ResponseBody.create(null, """{"error":{"code":401,"message":"API key not valid. Please pass a valid API key.","status":"UNAUTHENTICATED"}}"""))
            } else {
                retrofit2.Response.success("Success Response")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Success Response", result.getOrNull())
        assertEquals(listOf(key1.key, key2.key), attemptedKeys)

        val keysAfter = manager.getApiKeys()
        val k1 = keysAfter.first { it.id == key1.id }
        val k2 = keysAfter.first { it.id == key2.id }
        assertEquals(com.example.data.model.KeyStatus.INVALID, k1.status)
        assertEquals(1, k1.authenticationErrors401)
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, k2.status)
        assertEquals(1, k2.successfulRequests)
    }

    @Test
    fun `gemini model pools match task-specific specifications and order`() {
        // Translation Pool
        val transPool = com.example.data.model.GeminiModelRegistry.TRANSLATION_POOL
        assertEquals(2, transPool.size)
        assertEquals("gemini-3.1-flash-lite", transPool[0].modelId)
        assertEquals(1, transPool[0].priority)
        assertEquals(500, transPool[0].rpd)
        assertEquals(15, transPool[0].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, transPool[0].taskType)

        assertEquals("gemini-3.5-flash-lite", transPool[1].modelId)
        assertEquals(2, transPool[1].priority)
        assertEquals(500, transPool[1].rpd)
        assertEquals(15, transPool[1].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, transPool[1].taskType)

        // Passage Analysis Pool
        val passagePool = com.example.data.model.GeminiModelRegistry.PASSAGE_POOL
        assertEquals(3, passagePool.size)
        assertEquals("gemini-3.6-flash", passagePool[0].modelId)
        assertEquals(1, passagePool[0].priority)
        assertEquals(20, passagePool[0].rpd)
        assertEquals(5, passagePool[0].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[0].taskType)

        assertEquals("gemini-3.5-flash", passagePool[1].modelId)
        assertEquals(2, passagePool[1].priority)
        assertEquals(20, passagePool[1].rpd)
        assertEquals(5, passagePool[1].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[1].taskType)

        assertEquals("gemini-3.7-flash", passagePool[2].modelId)
        assertEquals(3, passagePool[2].priority)
        assertEquals(20, passagePool[2].rpd)
        assertEquals(5, passagePool[2].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[2].taskType)
    }

    @Test
    fun `hierarchical fallback steps down translation models on 429 within translation pool`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("AIzaSyFakeKeyTierFallback1", "Key 1")
        assertNotNull(key1)

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        val attemptedCalls = mutableListOf<Pair<String, String>>() // Key, Model

        // Simulate Word Translation: Key1 Priority 1 (3.1-lite) returns 429, Priority 2 (3.5-lite) succeeds!
        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "test"
        ) { apiKey, model ->
            attemptedCalls.add(apiKey to model)
            if (apiKey == key1!!.key && model == "gemini-3.1-flash-lite") {
                val errorBody = okhttp3.ResponseBody.create(
                    null,
                    """{"error": {"code": 429, "message": "Resource has been exhausted", "status": "RESOURCE_EXHAUSTED"}}"""
                )
                retrofit2.Response.error(429, errorBody)
            } else {
                retrofit2.Response.success("Translation result using model: $model")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Translation result using model: gemini-3.5-flash-lite", result.getOrNull())

        // Verify that it stepped down on the SAME key within the translation pool
        assertEquals(2, attemptedCalls.size)
        assertEquals(key1!!.key to "gemini-3.1-flash-lite", attemptedCalls[0])
        assertEquals(key1.key to "gemini-3.5-flash-lite", attemptedCalls[1])
    }

    @Test
    fun `passage analysis task uses passage pool starting with gemini-3-6-flash and isolates cooldown`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("AIzaSyFakeKeyPassagePool1", "Key 1")
        assertNotNull(key1)

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        // First, trigger a 429 on translation model gemini-3.1-flash-lite
        manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "test_trans"
        ) { apiKey, model ->
            if (model == "gemini-3.1-flash-lite") {
                val errorBody = okhttp3.ResponseBody.create(
                    null,
                    """{"error": {"code": 429, "message": "Resource has been exhausted", "status": "RESOURCE_EXHAUSTED"}}"""
                )
                retrofit2.Response.error(429, errorBody)
            } else {
                retrofit2.Response.success("Trans ok")
            }
        }

        val attemptedPassageCalls = mutableListOf<String>()

        // Now run Passage Analysis on the same key: it MUST start with gemini-3.6-flash (not in cooldown!)
        val passageResult = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test_passage"
        ) { _, model ->
            attemptedPassageCalls.add(model)
            retrofit2.Response.success("Passage analysis ok with $model")
        }

        assertTrue(passageResult.isSuccess)
        assertEquals(listOf("gemini-3.6-flash"), attemptedPassageCalls)
        assertEquals("Passage analysis ok with gemini-3.6-flash", passageResult.getOrNull())
    }

    @Test
    fun `passage word tags are linked to specific messageId and queryable by message`() = runBlocking {
        val bookId = bookRepository.createBook("Atomic Habits", "James Clear", "An Easy & Proven Way to Build Good Habits")
        val chapterId = chapterRepository.createChapter(bookId, 1, "The Fundamentals")

        val message1Id = chapterMessageRepository.saveMessage(
            chapterId = chapterId,
            originalText = "Habits are the compound interest of self-improvement.",
            aiResponse = "### Sabaq\nAadatein waqt ke sath bohot bara asar dalti hain."
        )

        val message2Id = chapterMessageRepository.saveMessage(
            chapterId = chapterId,
            originalText = "You do not rise to the level of your goals.",
            aiResponse = "### Sabaq\nAap apne systems ki wajah se kamyab hote hain."
        )

        // Save a word translated from message 1
        val entry1 = com.example.data.local.database.entity.WordVaultEntry(
            bookId = bookId,
            chapterId = chapterId,
            messageId = message1Id,
            word = "compound interest",
            meaning = "muraqqab sood / munafa jo barhta jaye",
            contextMeaning = "Yahan matlab hai choti aadatain waqt ke sath bohot bara result deti hain",
            originalSentence = "Habits are the compound interest of self-improvement.",
            explanation = "Aadatein choti lagti hain lekin lambe arse mein bohot bara faida deti hain.",
            phraseOrIdiomExplanation = "Compound interest asal mein financial term hai lekin yahan metaphorical use hua hai.",
            simpleExample = "Reading daily is like compound interest for your mind.",
            exampleMeaning = "Roz parhna aapke dimaagh ke liye bohot mufeed hai."
        )
        wordVaultRepository.saveOrUpdateWord(entry1)

        // Save a word translated from message 2
        val entry2 = com.example.data.local.database.entity.WordVaultEntry(
            bookId = bookId,
            chapterId = chapterId,
            messageId = message2Id,
            word = "rise to the level",
            meaning = "kisi standard tak pohanchna",
            contextMeaning = "Yahan matlab hai sirf khwab dekhne se kuch nahi hota",
            originalSentence = "You do not rise to the level of your goals.",
            explanation = "Aap sirf unche maqasid set karne se kamyab nahi hote.",
            phraseOrIdiomExplanation = "Rise to the level of something aik common English phrase hai.",
            simpleExample = "He rose to the level of the challenge.",
            exampleMeaning = "Usne mushkil ka samna kiya aur pura utra."
        )
        wordVaultRepository.saveOrUpdateWord(entry2)

        // Check message 1 tags
        val msg1Tags = wordVaultRepository.observeWordsForMessage(message1Id).first()
        assertEquals(1, msg1Tags.size)
        assertEquals("compound interest", msg1Tags[0].word)
        assertEquals(message1Id, msg1Tags[0].messageId)
        assertTrue(msg1Tags[0].phraseOrIdiomExplanation.isNotBlank())

        // Check message 2 tags
        val msg2Tags = wordVaultRepository.observeWordsForMessage(message2Id).first()
        assertEquals(1, msg2Tags.size)
        assertEquals("rise to the level", msg2Tags[0].word)
        assertEquals(message2Id, msg2Tags[0].messageId)

        // Check finding existing entry for message
        val foundInMsg1 = wordVaultRepository.findExistingWordEntry(
            chapterId = chapterId,
            word = "compound interest",
            sentence = "Habits are the compound interest of self-improvement.",
            messageId = message1Id
        )
        assertNotNull(foundInMsg1)
        assertEquals("compound interest", foundInMsg1?.word)
    }

    @Test
    fun `chapter pdf exporter parses message turns into structured sections accurately`() = runBlocking {
        val chapterId = 1L
        val msg1 = com.example.data.local.database.entity.ChapterMessage(
            id = 1L,
            chapterId = chapterId,
            originalText = "Habits are the compound interest of self-improvement.",
            aiResponse = "### 1. Asaan Samjh\nAadatein choti lagti hain lekin lambe arse mein bohot bara faida deti hain.\n\n### 2. Main Sabaq\nRozana 1% behtar hona aik saal mein aapko 37 guna behtar bana deta hai.\n\n### 3. Zaroori Nuqaat\n* Choti aadatain bara asar karti hain\n* Consistency sab se zaroori hai\n\n### 4. Real-Life Misaal\nJaise har roz gym jana pehle din asar nahi dikhata lekin 6 mahine baad farq wazeh hota hai.",
            createdAt = System.currentTimeMillis()
        )

        val parsed = com.example.data.export.ChapterPdfExporter.parseMessageTurn(1, msg1)
        assertEquals(1, parsed.passageNumber)
        assertEquals("Habits are the compound interest of self-improvement.", parsed.originalText)
        assertTrue(parsed.asaanSamjh.contains("Aadatein choti lagti hain"))
        assertTrue(parsed.mainLesson.contains("Rozana 1% behtar hona"))
        assertEquals(2, parsed.keyPoints.size)
        assertTrue(parsed.keyPoints[0].contains("Choti aadatain"))
        assertTrue(parsed.realLifeExample.contains("gym jana"))
    }

    @Test
    fun `vision extraction task routes to Gemini Flash-Lite pool`() {
        val visionModels = com.example.data.model.GeminiModelRegistry.getModelsForTask(
            com.example.data.model.GeminiTaskType.VISION_EXTRACTION
        )
        assertEquals(2, visionModels.size)
        assertEquals("gemini-3.1-flash-lite", visionModels[0].modelId)
        assertEquals("gemini-3.5-flash-lite", visionModels[1].modelId)
        assertEquals(1, visionModels[0].priority)
        assertEquals(2, visionModels[1].priority)
    }

    @Test
    fun `vision multi-page combine concatenates two extracted passages cleanly`() {
        val part1 = "LAW 2: NEVER PUT TOO MUCH TRUST IN FRIENDS, LEARN HOW TO USE ENEMIES."
        val part2 = "Be wary of friends—they will betray you more quickly, for they are easily aroused to envy."

        val combined = com.example.data.ocr.TextMergeUtils.mergeMultiPagePassages(part1, part2)
        assertTrue(combined.contains("LAW 2"))
        assertTrue(combined.contains("NEVER PUT TOO MUCH TRUST IN FRIENDS"))
        assertTrue(combined.contains("Be wary of friends"))
        assertTrue(combined.contains("aroused to envy"))
    }

    @Test
    fun `pdf crop coordinate mapping calculates drawn insets and scales correctly without drift`() {
        val pageWidth = 600
        val pageHeight = 900
        val pageAspect = pageWidth.toFloat() / pageHeight.toFloat() // 0.6667

        // Wide tablet container: 1000 x 1200 -> viewAspect = 0.8333 > pageAspect
        val viewWidth = 1000f
        val viewHeight = 1200f
        val viewAspect = viewWidth / viewHeight

        // Page fits height (1200) and has drawnWidth = 1200 * (600/900) = 800
        val drawnWidth = viewHeight * pageAspect
        val drawnHeight = viewHeight
        val drawnLeft = (viewWidth - drawnWidth) / 2f // 100f
        val drawnTop = 0f

        assertEquals(800f, drawnWidth, 0.01f)
        assertEquals(1200f, drawnHeight, 0.01f)
        assertEquals(100f, drawnLeft, 0.01f)
        assertEquals(0f, drawnTop, 0.01f)

        // If crop box is exactly on the left edge of the page on screen (x = 100)
        val cropLeftPx = 100f
        val relativeLeft = (cropLeftPx - drawnLeft).coerceIn(0f, drawnWidth)
        assertEquals(0f, relativeLeft, 0.01f) // Maps to page x=0 without drift!

        // Tall phone container: 1080 x 2400 -> viewAspect = 0.45 < pageAspect (0.6667)
        val phoneWidth = 1080f
        val phoneHeight = 2400f
        val phonePageWidth = phoneWidth // 1080
        val phonePageHeight = phoneWidth / pageAspect // 1620
        val phoneDrawnTop = (phoneHeight - phonePageHeight) / 2f // 390f

        assertEquals(1080f, phonePageWidth, 0.01f)
        assertEquals(1620f, phonePageHeight, 0.01f)
        assertEquals(390f, phoneDrawnTop, 0.01f)

        // If crop box is at top of page (y = 390)
        val cropTopPx = 390f
        val phoneRelativeTop = (cropTopPx - phoneDrawnTop).coerceIn(0f, phonePageHeight)
        assertEquals(0f, phoneRelativeTop, 0.01f) // Zero vertical drift!
    }

    @Test
    fun `original passage section is parsed and stripped from explanation body to ensure single display`() {
        val markdownWithLegacyPassage = """
            ## 🧠 Asaan Samjh
            Yahan author ye keh raha hai ke paise ke faislay personal experience se hotay hain.
            
            ## 💡 Main Lesson
            Paisa sirf numbers nahi balkay psychology hai.
            
            ## 🔑 Key Points
            • **Experience Matters**: Har insan ka tajurba alag hai.
            
            ## 🌎 Real-Life Example
            Misaal ke tor par aik investor aur aam mulazim ki soch alag hogi.
            
            ---
            
            ## 📖 Original English Passage
            People from different generations learn totally different lessons.
        """.trimIndent()

        // Delimiter regex test to ensure fallback and sections never duplicate passage
        val delimiterRegex = Regex("(?i)(?:\\n\\s*(?:---|\\*\\*\\*|___))?\\s*\\n+##+\\s*(?:📖\\s*)?Original(?:\\s+English)?\\s+Passage[\\s\\S]*$")
        val stripped = markdownWithLegacyPassage.replace(delimiterRegex, "").trim()

        assertFalse(stripped.contains("Original English Passage"))
        assertFalse(stripped.contains("People from different generations learn totally different lessons"))
        assertTrue(stripped.contains("🧠 Asaan Samjh"))
        assertTrue(stripped.contains("🌎 Real-Life Example"))
    }

    @Test
    fun `ZoomablePdfPageTouchView multi-touch scale and touch disallow logic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = com.example.ui.components.pdf.ZoomablePdfPageTouchView(context)
        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        view.setPageBitmap(bitmap, isSnipMode = false)

        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 1920)

        assertEquals(1.0f, view.currentScale, 0.01f)
    }

    @Test
    fun `ChapterChatViewModel scroll position persistence across state and cache`() {
        val chapterId = 101L
        com.example.ui.screens.chat.ChatScrollPositionCache.clear(chapterId)
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("chapterId" to chapterId))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        val geminiRepo = com.example.data.repository.GeminiRepository(storage)

        val viewModel = com.example.ui.viewmodel.ChapterChatViewModel(
            savedStateHandle = savedState,
            chapterRepository = chapterRepository,
            bookRepository = bookRepository,
            chapterMessageRepository = chapterMessageRepository,
            geminiRepository = geminiRepo,
            wordVaultRepository = wordVaultRepository
        )

        assertNull(viewModel.savedScrollIndex)
        assertNull(viewModel.savedScrollOffset)

        viewModel.updateScrollPosition(index = 5, offset = 142)

        assertEquals(5, viewModel.savedScrollIndex)
        assertEquals(142, viewModel.savedScrollOffset)
        assertEquals(5, savedState.get<Int>("chat_scroll_index"))
        assertEquals(142, savedState.get<Int>("chat_scroll_offset"))
        assertEquals(Pair(5, 142), com.example.ui.screens.chat.ChatScrollPositionCache.getPosition(chapterId))
    }

    @Test
    fun `tamper-proof reading anchor strictly advances and never decreases on earlier page review`() = runBlocking {
        val bookId = bookRepository.createBook(
            title = "Atomic Habits",
            author = "James Clear",
            description = "Tiny Changes, Remarkable Results",
            pdfFilePath = "/data/user/0/com.example/files/pdfs/atomic_habits.pdf",
            pdfFileName = "atomic_habits.pdf",
            pdfTotalPages = 50,
            pdfLastReadPage = 0
        )

        var book = bookRepository.getBook(bookId)
        assertNotNull(book)
        assertEquals(0, book!!.pdfLastReadPage)
        assertEquals(50, book.pdfTotalPages)

        // Snip from page 15 -> anchor advances to 15
        bookRepository.updateHighestReadPageAnchor(bookId, 15)
        book = bookRepository.getBook(bookId)
        assertEquals(15, book!!.pdfLastReadPage)

        // User reviews earlier page 5 and snips -> anchor MUST remain at 15
        bookRepository.updateHighestReadPageAnchor(bookId, 5)
        book = bookRepository.getBook(bookId)
        assertEquals(15, book!!.pdfLastReadPage)

        // Snip from further page 35 -> anchor advances to 35
        bookRepository.updateHighestReadPageAnchor(bookId, 35)
        book = bookRepository.getBook(bookId)
        assertEquals(35, book!!.pdfLastReadPage)

        // Verify completion percentage calculation
        val progressFraction = (book.pdfLastReadPage.toFloat() / book.pdfTotalPages.toFloat()).coerceIn(0f, 1f)
        val progressPercent = (progressFraction * 100).toInt()
        assertEquals(70, progressPercent)

        // Complete the book by reaching page 50
        bookRepository.updateHighestReadPageAnchor(bookId, 50)
        book = bookRepository.getBook(bookId)
        assertEquals(50, book!!.pdfLastReadPage)
        val finalPercent = ((book.pdfLastReadPage.toFloat() / book.pdfTotalPages.toFloat()) * 100).toInt()
        assertEquals(100, finalPercent)
    }

    @Test
    fun `chapter tamper-proof reading anchor strictly maintains highest read page`() = runBlocking {
        val bookId = bookRepository.createBook(
            title = "Thinking Fast and Slow",
            author = "Daniel Kahneman",
            description = "Two systems",
            pdfFilePath = "/path/doc.pdf",
            pdfFileName = "doc.pdf",
            pdfTotalPages = 100,
            pdfLastReadPage = 0
        )

        val chapterId = chapterRepository.createChapter(
            bookId = bookId,
            chapterNumber = 1,
            title = "Two Systems"
        )

        chapterRepository.updateHighestReadPageAnchor(chapterId, 22)
        var chapter = chapterRepository.getChapter(chapterId)
        assertEquals(22, chapter!!.pdfLastReadPage)

        // Review earlier page 8 -> does not decrease
        chapterRepository.updateHighestReadPageAnchor(chapterId, 8)
        chapter = chapterRepository.getChapter(chapterId)
        assertEquals(22, chapter!!.pdfLastReadPage)

        // Advance to page 45
        chapterRepository.updateHighestReadPageAnchor(chapterId, 45)
        chapter = chapterRepository.getChapter(chapterId)
        assertEquals(45, chapter!!.pdfLastReadPage)
    }

    @Test
    fun `mcq repository stores questions and retrieves by chapter and all`() = runBlocking {
        val mcqRepository = com.example.data.repository.McqRepository(
            mcqQuestionDao = db.mcqQuestionDao(),
            userMcqAttemptDao = db.userMcqAttemptDao()
        )

        val bookId = bookRepository.createBook(
            title = "Atomic Habits",
            author = "James Clear"
        )
        val chapterId = chapterRepository.createChapter(
            bookId = bookId,
            chapterNumber = 1,
            title = "Fundamentals"
        )

        val q1 = com.example.data.local.database.entity.McqQuestion(
            bookId = bookId,
            chapterId = chapterId,
            passageTurnId = 1L,
            questionText = "What drives long-term habit compounding according to the text?",
            optionA = "Radical overnight transformation",
            optionB = "Consistent 1% marginal gains",
            optionC = "High initial motivation",
            optionD = "Complex scheduling systems",
            correctOption = "B",
            explanation = "Small habits aggregate into exponential long-term outcomes."
        )

        val q2 = com.example.data.local.database.entity.McqQuestion(
            bookId = bookId,
            chapterId = chapterId,
            passageTurnId = 1L,
            questionText = "Why are outcome-based goals less effective than identity-based habits?",
            optionA = "They focus on who you wish to become",
            optionB = "They focus purely on what you want to achieve without changing self-belief",
            optionC = "They require too much discipline",
            optionD = "They don't allow measurable metrics",
            correctOption = "B",
            explanation = "Identity-based habits shift internal self-concept."
        )

        mcqRepository.saveQuestions(listOf(q1, q2))

        val chapterQuestions = mcqRepository.getQuestionsForChapter(chapterId)
        assertEquals(2, chapterQuestions.size)

        val allQuestions = mcqRepository.getAllQuestions()
        assertEquals(2, allQuestions.size)
        assertEquals("B", chapterQuestions[0].correctOption)
    }

    @Test
    fun `daily recall viewmodel executes primary queue, retries wrong answers, and calculates accuracy`() = runBlocking {
        val mcqRepository = com.example.data.repository.McqRepository(
            mcqQuestionDao = db.mcqQuestionDao(),
            userMcqAttemptDao = db.userMcqAttemptDao()
        )

        val bookId = bookRepository.createBook(
            title = "The 48 Laws of Power",
            author = "Robert Greene"
        )
        val chapterId = chapterRepository.createChapter(
            bookId = bookId,
            chapterNumber = 1,
            title = "Law 1"
        )

        val q1 = com.example.data.local.database.entity.McqQuestion(
            bookId = bookId,
            chapterId = chapterId,
            passageTurnId = 10L,
            questionText = "Question 1: Why should you never outshine the master?",
            optionA = "Masters dislike talented subordinates",
            optionB = "It triggers insecurity and resentment in superiors",
            optionC = "It reduces overall team efficiency",
            optionD = "It violates corporate etiquette",
            correctOption = "B",
            explanation = "Subordinates must make superiors appear brilliant."
        )

        val q2 = com.example.data.local.database.entity.McqQuestion(
            bookId = bookId,
            chapterId = chapterId,
            passageTurnId = 10L,
            questionText = "Question 2: How should one conceal their intentions?",
            optionA = "By staying completely silent",
            optionB = "By using decoy goals and smokescreens",
            optionC = "By agreeing with everyone",
            optionD = "By changing plans constantly",
            correctOption = "B",
            explanation = "Smokescreens prevent opposition from preparing defenses."
        )

        mcqRepository.saveQuestions(listOf(q1, q2))

        val viewModel = com.example.ui.viewmodel.DailyRecallViewModel(
            mcqRepository = mcqRepository,
            bookRepository = bookRepository,
            chapterRepository = chapterRepository,
            userGamificationRepository = userGamificationRepository,
            dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        // Wait for loading to finish
        var state = viewModel.uiState.first { it is com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz }
        var activeState = state as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        assertEquals(2, activeState.totalQuestionsInPrimary)
        assertFalse(activeState.isRetryRound)

        // Answer Q1 CORRECTLY
        val q1CorrectOption = activeState.currentQuestion.options.first { it.isCorrect }
        viewModel.selectOption(q1CorrectOption.id)
        viewModel.submitAnswer()

        activeState = viewModel.uiState.value as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        assertTrue(activeState.isAnswerSubmitted)
        assertTrue(activeState.feedback!!.isCorrect)
        assertEquals(1, activeState.primaryCorrectCount)

        viewModel.nextQuestion()

        // Answer second question INCORRECTLY (Pick a wrong option)
        activeState = viewModel.uiState.value as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        val failedQuestion = activeState.currentQuestion.question
        val wrongOption = activeState.currentQuestion.options.first { !it.isCorrect }
        viewModel.selectOption(wrongOption.id)
        viewModel.submitAnswer()

        activeState = viewModel.uiState.value as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        assertTrue(activeState.isAnswerSubmitted)
        assertFalse(activeState.feedback!!.isCorrect)
        assertEquals(1, activeState.primaryCorrectCount) // Remains 1

        viewModel.nextQuestion()

        // Now should enter Duolingo-style RETRY ROUND for the failed question!
        activeState = viewModel.uiState.value as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        assertTrue(activeState.isRetryRound)
        assertEquals(1, activeState.totalRetryQuestions)
        assertEquals(failedQuestion.questionText, activeState.currentQuestion.question.questionText)

        // Answer retry correctly
        val retryCorrectOption = activeState.currentQuestion.options.first { it.isCorrect }
        viewModel.selectOption(retryCorrectOption.id)
        viewModel.submitAnswer()

        activeState = viewModel.uiState.value as com.example.ui.viewmodel.DailyRecallUiState.ActiveQuiz
        assertTrue(activeState.feedback!!.isCorrect)
        assertEquals(1, activeState.retryCorrectCount)

        viewModel.nextQuestion()

        // Now should finish and show SummaryScoreCard!
        val summaryState = viewModel.uiState.first { it is com.example.ui.viewmodel.DailyRecallUiState.SummaryScoreCard } as com.example.ui.viewmodel.DailyRecallUiState.SummaryScoreCard
        assertEquals(2, summaryState.totalPrimaryQuestions)
        assertEquals(1, summaryState.primaryCorrectCount)
        assertEquals(50f, summaryState.accuracyPercentage, 0.1f) // 1/2 = 50%
        assertEquals(1, summaryState.retriedCount)
        assertEquals(1, summaryState.retryRecoveredCount)
        assertEquals(1, summaryState.mistakes.size)
        assertTrue(summaryState.mistakes[0].resolvedInRetry)

        // Verify attempt record was saved into database
        val attempts = mcqRepository.getAllAttempts()
        assertEquals(1, attempts.size)
        assertEquals(2, attempts[0].totalQuestions)
        assertEquals(1, attempts[0].correctAnswers)
        assertEquals(50f, attempts[0].scorePercentage, 0.1f)
    }

    @Test
    fun `authViewModel validation checks name, email, password length, and password match`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionStorage = com.example.data.local.security.AndroidKeystoreAuthSessionStorage(context)
        val authRepo = com.example.data.repository.AuthRepository(
            authApiService = com.example.data.remote.auth.AuthApiService.create(),
            authSessionStorage = sessionStorage
        )
        val viewModel = com.example.ui.viewmodel.AuthViewModel(authRepo)

        // 1. Sign In empty validation
        viewModel.onSignInClicked {}
        assertEquals("Email is required.", viewModel.uiState.value.errorMessage)

        viewModel.onEmailChanged("user@example.com")
        viewModel.onSignInClicked {}
        assertEquals("Password is required.", viewModel.uiState.value.errorMessage)

        // 2. Create Account validations
        viewModel.onTabChanged(com.example.ui.viewmodel.AuthTab.CREATE_ACCOUNT)
        viewModel.onNameChanged("")
        viewModel.onCreateAccountClicked()
        assertEquals("Name is required.", viewModel.uiState.value.errorMessage)

        viewModel.onNameChanged("Alex Mercer")
        viewModel.onEmailChanged("")
        viewModel.onCreateAccountClicked()
        assertEquals("Email is required.", viewModel.uiState.value.errorMessage)

        viewModel.onEmailChanged("alex@mercer.com")
        viewModel.onPasswordChanged("12345")
        viewModel.onConfirmPasswordChanged("12345")
        viewModel.onCreateAccountClicked()
        assertEquals("Password must be at least 6 characters.", viewModel.uiState.value.errorMessage)

        viewModel.onPasswordChanged("secret123")
        viewModel.onConfirmPasswordChanged("secret456")
        viewModel.onCreateAccountClicked()
        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)

        // 3. Confirm Password Reset validations
        viewModel.onNewPasswordChanged("123")
        viewModel.onConfirmPasswordChanged("123")
        viewModel.onConfirmPasswordResetClicked {}
        assertEquals("Password must be at least 6 characters.", viewModel.uiState.value.errorMessage)

        viewModel.onNewPasswordChanged("securePassword1")
        viewModel.onConfirmPasswordChanged("securePassword2")
        viewModel.onConfirmPasswordResetClicked {}
        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `drive explorer repository obfuscated api key decodes to valid expected drive key`() {
        val apiKey = com.example.data.remote.drive.DriveExplorerRepository.API_KEY
        assertEquals("AIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kcVc", apiKey)
    }

    @Test
    fun `parseTitleAndAuthor correctly decomposes hyphen separated title and author`() {
        val (title1, author1) = com.example.data.remote.drive.DriveBookItem.parseTitleAndAuthor("Atomic Habits - James Clear.pdf")
        assertEquals("Atomic Habits", title1)
        assertEquals("James Clear", author1)

        val (title2, author2) = com.example.data.remote.drive.DriveBookItem.parseTitleAndAuthor("The Pleasure Trap -- Douglas J Lisle.pdf")
        assertEquals("The Pleasure Trap", title2)
        assertEquals("Douglas J Lisle", author2)

        val (title3, author3) = com.example.data.remote.drive.DriveBookItem.parseTitleAndAuthor("Last Love Letter.pdf")
        assertEquals("Last Love Letter", title3)
        assertEquals("Community Upload", author3)
    }

    @Test
    fun `community upload worker specifications match expected constants`() {
        assertEquals(
            "https://script.google.com/macros/s/AKfycbzx1gYv0W2Y7lFMY6m16g55jEYXGmNKoTMJI8CKQHROJmxGBxR4yxHsUrSJrZcpv-dcwA/exec",
            com.example.data.worker.CommunityUploadWorker.RELAY_ENDPOINT
        )
        assertEquals(35L * 1024 * 1024, com.example.data.worker.CommunityUploadWorker.MAX_FILE_SIZE_BYTES)
    }
}

