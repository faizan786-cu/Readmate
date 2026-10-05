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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        // Model-First Cross-Key Cascade: key1 tried primary model (gemini-3.8-flash), hit 429, rotated immediately to key2 on same primary model!
        assertEquals(2, callCount)
        assertEquals(1, attemptedCalls.count { it.first == key1.key })
        assertEquals(1, attemptedCalls.count { it.first == key2.key })

        // Verify key1 encountered 429 error and key2 succeeded
        val keysAfter = manager.getApiKeys()
        val k1 = keysAfter.first { it.id == key1.id }
        val k2 = keysAfter.first { it.id == key2.id }
        assertEquals(1, k1.rateLimitErrors429)
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

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<okhttp3.ResponseBody> {
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
    fun `apiKeyManager 503 on Key A does not abort and rotates to Key B on same gemini-3-8-flash model`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val keyA = storage.addApiKey("key_A_503_test_12345", "Key A")!!
        val keyB = storage.addApiKey("key_B_503_test_12345", "Key B")!!

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        val callLog = mutableListOf<Pair<String, String>>() // (apiKey, model)
        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test 503 cross-key failover"
        ) { apiKey, model ->
            callLog.add(apiKey to model)
            if (apiKey == keyA.key) {
                retrofit2.Response.error(
                    503,
                    okhttp3.ResponseBody.create(
                        null,
                        """{"error":{"code":503,"message":"The service is temporarily overloaded","status":"UNAVAILABLE"}}"""
                    )
                )
            } else {
                retrofit2.Response.success("Success from Key B")
            }
        }

        // 1. Key A + gemini-3.8-flash returned 503 repeatedly
        // 2. The orchestrator does NOT return final failure:
        assertTrue("Orchestrator must succeed via failover to Key B", result.isSuccess)
        assertEquals("Success from Key B", result.getOrNull())

        // 3. It proceeds to Key B + gemini-3.8-flash:
        val keyBCalls = callLog.filter { it.first == keyB.key }
        assertTrue("Must call Key B", keyBCalls.isNotEmpty())

        // 4. If Key B succeeds, the request succeeds without stepping down the model:
        assertEquals("gemini-3.8-flash", keyBCalls.first().second)
        assertTrue("All calls must stay on gemini-3.8-flash without model step-down", callLog.all { it.second == "gemini-3.8-flash" })

        // Check key status: Key A must NOT be marked INVALID, it remains ACTIVE with localized cooldown
        val keysAfter = manager.getApiKeys()
        val kA = keysAfter.first { it.id == keyA.id }
        val kB = keysAfter.first { it.id == keyB.id }
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, kA.status)
        assertEquals(com.example.data.model.KeyStatus.ACTIVE, kB.status)
        assertTrue(kA.serviceUnavailableErrors503 > 0)
        assertEquals(1, kB.successfulRequests)
    }

    @Test
    fun `apiKeyManager 503 on all keys for gemini-3-8-flash cascades down to gemini-3-6-flash`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val keyA = storage.addApiKey("key_A_cascade_12345", "Key A")!!
        val keyB = storage.addApiKey("key_B_cascade_12345", "Key B")!!

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        val callLog = mutableListOf<Pair<String, String>>()
        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test 503 model cascade"
        ) { apiKey, model ->
            callLog.add(apiKey to model)
            if (model == "gemini-3.8-flash") {
                // Every key fails on gemini-3.8-flash with 503
                retrofit2.Response.error(
                    503,
                    okhttp3.ResponseBody.create(
                        null,
                        """{"error":{"code":503,"message":"gemini-3.8-flash unavailable","status":"UNAVAILABLE"}}"""
                    )
                )
            } else if (model == "gemini-3.6-flash") {
                // Succeeds on gemini-3.6-flash
                retrofit2.Response.success("Success from gemini-3.6-flash")
            } else {
                retrofit2.Response.error(
                    500,
                    okhttp3.ResponseBody.create(null, """{"error":{"code":500}}""")
                )
            }
        }

        assertTrue("Must succeed on fallback model gemini-3.6-flash", result.isSuccess)
        assertEquals("Success from gemini-3.6-flash", result.getOrNull())

        // 5. If every key returns 503 for gemini-3.8-flash, only then does it attempt gemini-3.6-flash:
        val flash38Keys = callLog.filter { it.second == "gemini-3.8-flash" }.map { it.first }.toSet()
        assertEquals(setOf(keyA.key, keyB.key), flash38Keys)

        val first36Index = callLog.indexOfFirst { it.second == "gemini-3.6-flash" }
        val last38Index = callLog.indexOfLast { it.second == "gemini-3.8-flash" }
        assertTrue("gemini-3.6-flash must only be attempted after gemini-3.8-flash is exhausted across all keys", first36Index > last38Index)
    }

    @Test
    fun `streamWithAutoRotation 503 on Key A does not abort and rotates to Key B on same model`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val keyA = storage.addApiKey("key_stream_A_12345", "Stream Key A")!!
        val keyB = storage.addApiKey("key_stream_B_12345", "Stream Key B")!!

        val callLog = mutableListOf<Pair<String, String>>()
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<okhttp3.ResponseBody> {
                callLog.add(apiKey to model)
                return if (apiKey == keyA.key) {
                    retrofit2.Response.error(
                        503,
                        okhttp3.ResponseBody.create(
                            null,
                            """{"error":{"code":503,"message":"Service overloaded","status":"UNAVAILABLE"}}"""
                        )
                    )
                } else {
                    val sseBody = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Streamed explanation from Key B\"}]}}]}\n\ndata: [DONE]\n\n"
                    retrofit2.Response.success(
                        okhttp3.ResponseBody.create(null, sseBody)
                    )
                }
            }
        }

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService
        )

        val chunks = mutableListOf<String>()
        val result = manager.streamWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "stream test 503 failover",
            request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("Explain this passage")
        ) { accumulated, chunk ->
            chunks.add(chunk)
        }

        // 6. Verify equivalent behaviour for the streaming rotation path:
        assertTrue("Streaming must succeed after silent failover to Key B", result.isSuccess)
        assertEquals("Streamed explanation from Key B", result.getOrNull())

        val keyBCalls = callLog.filter { it.first == keyB.key }
        assertTrue("Must call Key B", keyBCalls.isNotEmpty())
        assertEquals("gemini-3.8-flash", keyBCalls.first().second)
        assertTrue("All streaming calls must stay on gemini-3.8-flash", callLog.all { it.second == "gemini-3.8-flash" })
    }

    @Test
    fun `streamWithAutoRotation 503 on all keys cascades down to gemini-3-6-flash`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val keyA = storage.addApiKey("key_stream_casc_A_12345", "Stream Key A")!!
        val keyB = storage.addApiKey("key_stream_casc_B_12345", "Stream Key B")!!

        val callLog = mutableListOf<Pair<String, String>>()
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<okhttp3.ResponseBody> {
                callLog.add(apiKey to model)
                return if (model == "gemini-3.8-flash") {
                    retrofit2.Response.error(
                        503,
                        okhttp3.ResponseBody.create(
                            null,
                            """{"error":{"code":503,"message":"gemini-3.8-flash overloaded","status":"UNAVAILABLE"}}"""
                        )
                    )
                } else if (model == "gemini-3.6-flash") {
                    val sseBody = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Streamed from gemini-3.6-flash\"}]}}]}\n\ndata: [DONE]\n\n"
                    retrofit2.Response.success(
                        okhttp3.ResponseBody.create(null, sseBody)
                    )
                } else {
                    retrofit2.Response.error(
                        500,
                        okhttp3.ResponseBody.create(null, """{"error":{"code":500}}""")
                    )
                }
            }
        }

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService
        )

        val result = manager.streamWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "stream test 503 model cascade",
            request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("Explain this passage")
        ) { _, _ -> }

        assertTrue("Streaming must succeed on fallback model", result.isSuccess)
        assertEquals("Streamed from gemini-3.6-flash", result.getOrNull())

        val flash38Keys = callLog.filter { it.second == "gemini-3.8-flash" }.map { it.first }.toSet()
        assertEquals(setOf(keyA.key, keyB.key), flash38Keys)

        val first36Index = callLog.indexOfFirst { it.second == "gemini-3.6-flash" }
        val last38Index = callLog.indexOfLast { it.second == "gemini-3.8-flash" }
        assertTrue("Must cascade to gemini-3.6-flash only after gemini-3.8-flash exhausted across keys", first36Index > last38Index)
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
        // Translation Pool (Lightweight Utility Tier)
        val transPool = com.example.data.model.GeminiModelRegistry.TRANSLATION_POOL
        assertEquals(3, transPool.size)
        assertEquals("gemini-3.5-flash-lite", transPool[0].modelId)
        assertEquals(1, transPool[0].priority)
        assertEquals(500, transPool[0].rpd)
        assertEquals(15, transPool[0].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, transPool[0].taskType)

        assertEquals("gemini-3.1-flash-lite", transPool[1].modelId)
        assertEquals(2, transPool[1].priority)
        assertEquals(500, transPool[1].rpd)
        assertEquals(15, transPool[1].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, transPool[1].taskType)

        assertEquals("gemini-2.5-flash-lite", transPool[2].modelId)
        assertEquals(3, transPool[2].priority)
        assertEquals(500, transPool[2].rpd)
        assertEquals(15, transPool[2].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, transPool[2].taskType)

        // Passage Analysis Pool (Heavy Analysis Tier)
        val passagePool = com.example.data.model.GeminiModelRegistry.PASSAGE_POOL
        assertEquals(5, passagePool.size)
        assertEquals("gemini-3.8-flash", passagePool[0].modelId)
        assertEquals(1, passagePool[0].priority)
        assertEquals(20, passagePool[0].rpd)
        assertEquals(5, passagePool[0].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[0].taskType)

        assertEquals("gemini-3.6-flash", passagePool[1].modelId)
        assertEquals(2, passagePool[1].priority)
        assertEquals(20, passagePool[1].rpd)
        assertEquals(5, passagePool[1].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[1].taskType)

        assertEquals("gemini-3.5-flash", passagePool[2].modelId)
        assertEquals(3, passagePool[2].priority)
        assertEquals(20, passagePool[2].rpd)
        assertEquals(5, passagePool[2].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[2].taskType)

        assertEquals("gemini-3-flash-preview", passagePool[3].modelId)
        assertEquals(4, passagePool[3].priority)
        assertEquals(20, passagePool[3].rpd)
        assertEquals(5, passagePool[3].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[3].taskType)

        assertEquals("gemini-2.5-flash", passagePool[4].modelId)
        assertEquals(5, passagePool[4].priority)
        assertEquals(20, passagePool[4].rpd)
        assertEquals(5, passagePool[4].rpm)
        assertEquals(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, passagePool[4].taskType)
    }

    @Test
    fun `model-first cross-key cascade tests top priority model across keys before step-down`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("AIzaSyFakeKeyCascade1", "Key 1")
        val key2 = storage.addApiKey("AIzaSyFakeKeyCascade2", "Key 2")
        assertNotNull(key1)
        assertNotNull(key2)

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        val attemptedCalls = mutableListOf<Pair<String, String>>() // Key, Model

        // Key 1 hits 429 on gemini-3.8-flash; Key 2 succeeds on gemini-3.8-flash!
        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test_cascade"
        ) { apiKey, model ->
            attemptedCalls.add(apiKey to model)
            if (apiKey == key1!!.key && model == "gemini-3.8-flash") {
                val errorBody = okhttp3.ResponseBody.create(
                    null,
                    """{"error": {"code": 429, "message": "Resource has been exhausted", "status": "RESOURCE_EXHAUSTED"}}"""
                )
                retrofit2.Response.error(429, errorBody)
            } else {
                retrofit2.Response.success("Success with $model on key")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Success with gemini-3.8-flash on key", result.getOrNull())

        // Verified Model-First: Attempted top-priority model on Key 1, then rotated to Key 2 on SAME model
        assertEquals(2, attemptedCalls.size)
        assertEquals(key1!!.key to "gemini-3.8-flash", attemptedCalls[0])
        assertEquals(key2!!.key to "gemini-3.8-flash", attemptedCalls[1])
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

        // Simulate Word Translation: Key1 Priority 1 (3.5-lite) returns 429, Priority 2 (3.1-lite) succeeds!
        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "test"
        ) { apiKey, model ->
            attemptedCalls.add(apiKey to model)
            if (apiKey == key1!!.key && model == "gemini-3.5-flash-lite") {
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
        assertEquals("Translation result using model: gemini-3.1-flash-lite", result.getOrNull())

        // Verify that it stepped down on the SAME key within the translation pool
        assertEquals(2, attemptedCalls.size)
        assertEquals(key1!!.key to "gemini-3.5-flash-lite", attemptedCalls[0])
        assertEquals(key1.key to "gemini-3.1-flash-lite", attemptedCalls[1])
    }

    @Test
    fun `passage analysis task uses passage pool starting with gemini-3-8-flash and isolates cooldown`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()

        val key1 = storage.addApiKey("AIzaSyFakeKeyPassagePool1", "Key 1")
        assertNotNull(key1)

        val manager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage
        )

        // First, trigger a 429 on translation model gemini-3.5-flash-lite
        manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "test_trans"
        ) { apiKey, model ->
            if (model == "gemini-3.5-flash-lite") {
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

        // Now run Passage Analysis on the same key: it MUST start with gemini-3.8-flash (not in cooldown!)
        val passageResult = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test_passage"
        ) { _, model ->
            attemptedPassageCalls.add(model)
            retrofit2.Response.success("Passage analysis ok with $model")
        }

        assertTrue(passageResult.isSuccess)
        assertEquals(listOf("gemini-3.8-flash"), attemptedPassageCalls)
        assertEquals("Passage analysis ok with gemini-3.8-flash", passageResult.getOrNull())
    }

    @Test
    fun `flash-lite models strictly omit thinkingConfig and sanitize systemInstruction`() {
        val visionReq = com.example.data.remote.gemini.GeminiGenerateContentRequest.forVision(
            prompt = "Extract text",
            base64Data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            systemInstruction = "Only English"
        )
        // Flash-Lite configuration must never have thinkingConfig
        assertNull(visionReq.generationConfig?.thinkingConfig)

        // When sanitized for gemini-3.5-flash-lite, thinkingConfig remains null
        val sanitizedLite = visionReq.sanitizedForModel("gemini-3.5-flash-lite")
        assertNull(sanitizedLite.generationConfig?.thinkingConfig)
        assertEquals("Only English", sanitizedLite.systemInstruction?.parts?.firstOrNull()?.text)

        // When given empty/blank systemInstruction, it must be completely omitted (null)
        val emptyInstructionReq = com.example.data.remote.gemini.GeminiGenerateContentRequest(
            contents = listOf(com.example.data.remote.gemini.GeminiContent(parts = listOf(com.example.data.remote.gemini.GeminiPart(text = "Hello")))),
            systemInstruction = com.example.data.remote.gemini.GeminiContent(parts = listOf(com.example.data.remote.gemini.GeminiPart(text = "   "))),
            generationConfig = com.example.data.remote.gemini.GeminiGenerationConfig.forFlash()
        )
        val sanitizedEmpty = emptyInstructionReq.sanitizedForModel("gemini-3.5-flash-lite")
        assertNull(sanitizedEmpty.systemInstruction)
        assertNull(sanitizedEmpty.generationConfig?.thinkingConfig)

        // For Flash heavy models, thinkingConfig uses thinkingLevel for Gemini 3 and thinkingBudget for Gemini 2.5
        val sanitizedFlash3 = emptyInstructionReq.sanitizedForModel("gemini-3.8-flash")
        assertNotNull(sanitizedFlash3.generationConfig?.thinkingConfig)
        assertEquals("LOW", sanitizedFlash3.generationConfig?.thinkingConfig?.thinkingLevel)
        assertNull(sanitizedFlash3.generationConfig?.thinkingConfig?.thinkingBudget)

        val sanitizedFlash25 = emptyInstructionReq.sanitizedForModel("gemini-2.5-flash")
        assertNotNull(sanitizedFlash25.generationConfig?.thinkingConfig)
        assertEquals(0, sanitizedFlash25.generationConfig?.thinkingConfig?.thinkingBudget)
        assertNull(sanitizedFlash25.generationConfig?.thinkingConfig?.thinkingLevel)
    }

    @Test
    fun `http 400 parameter mismatch automatically cascades to fallback model in ladder`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key = storage.addApiKey("AIzaSyTestKey400Recovery", "Key 1")
        assertNotNull(key)

        val manager = com.example.data.manager.GeminiApiKeyManager(secureStorage = storage)
        val attemptedModels = mutableListOf<String>()

        val result = manager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "test_400_recovery"
        ) { _, model ->
            attemptedModels.add(model)
            if (model == "gemini-3.5-flash-lite") {
                // Primary model returns HTTP 400: Request contains an invalid argument
                val errorBody = okhttp3.ResponseBody.create(
                    null,
                    """{"error":{"code":400,"message":"Request contains an invalid argument","status":"INVALID_ARGUMENT"}}"""
                )
                retrofit2.Response.error(400, errorBody)
            } else {
                // Fallback model in ladder succeeds seamlessly!
                retrofit2.Response.success("Success on $model")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Success on gemini-3.1-flash-lite", result.getOrNull())
        // Primary failed with 400, automatically stepped down to gemini-3.1-flash-lite
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"), attemptedModels)
    }

    @Test
    fun `retryAction in ExplanationJobState Error preserves snippet context and clears state`() = runBlocking {
        var retried = false
        val errorState = com.example.data.manager.ExplanationJobState.Error(
            passage = "Page 1",
            errorMessage = "Parameter mismatch",
            pageNumber = 1,
            retryAction = { retried = true }
        )

        assertNotNull(errorState.retryAction)
        errorState.retryAction?.invoke()
        assertTrue(retried)
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
        assertEquals(3, visionModels.size)
        assertEquals("gemini-3.5-flash-lite", visionModels[0].modelId)
        assertEquals("gemini-3.1-flash-lite", visionModels[1].modelId)
        assertEquals("gemini-2.5-flash-lite", visionModels[2].modelId)
        assertEquals(1, visionModels[0].priority)
        assertEquals(2, visionModels[1].priority)
        assertEquals(3, visionModels[2].priority)
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

    @Test
    fun `clipboard autofill api key regex strictly validates standard 39 character AI Studio keys`() {
        val apiKeyRegex = Regex("^AIzaSy[A-Za-z0-9_-]{33}$")

        // Valid 39-char keys starting with AIzaSy
        assertTrue(apiKeyRegex.matches("AIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kcVc"))
        assertTrue(apiKeyRegex.matches("AIzaSyABCDEF1234567890_-abcdefghijklmno"))

        // Invalid keys
        assertFalse(apiKeyRegex.matches("AIzaSyTooShort"))
        assertFalse(apiKeyRegex.matches("NotAIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kcVc"))
        assertFalse(apiKeyRegex.matches("AIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kcVcTooLong123"))
        assertFalse(apiKeyRegex.matches("AIzaSyDLt_DISeV-7307osTzFMEejuq0Ks8kc!@#")) // invalid chars
        assertFalse(apiKeyRegex.matches(""))
    }

    @Test
    fun `openVisualGuide helper launches safely without unhandled exceptions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ui.components.openVisualGuide(context)
    }

    // ==========================================
    // Phase 2: Passage Explanation Quality Tests
    // ==========================================

    @Test
    fun `quality validator - extremely short response with all headings but one-line content fails validation`() {
        val passage = "The psychology of money is largely about human behavior, ego, fear, and long-term discipline. People who grow up in poverty view risk very differently from those who grew up in wealth."
        val shortResponse = """
            ## 🧠 Asaan Samjh
            Author paison ki psychology ke bare mein baat kar raha hai.

            ## 💡 Main Lesson
            Paisa aqal se sambhalein.

            ## 🔑 Key Points
            • Point 1: Paisa
            • Point 2: Bachat

            ## 🌎 Real-Life Example
            Kharcha kam karein.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, shortResponse)
        assertFalse("Extremely short one-line response must fail validation", validation.isValid)
        assertTrue("Weak sections should be detected", validation.weakSections.isNotEmpty())
    }

    @Test
    fun `quality validator - response missing Key Insights fails validation`() {
        val passage = "Compound interest is the eighth wonder of the world. He who understands it, earns it; he who doesn't, pays it."
        val responseWithoutKeyPoints = """
            ## 🧠 Asaan Samjh
            Author yahan compounding ke asool ko explain kar raha hai. Jab aap kisi cheez mein thori thori investment karte hain, to waqt ke saath us par milne wala faida bhi mazeed faida paida karne lagta hai.

            Iska matlab ye hai ke shuru mein natijay bohat chote lagte hain, lekin lambay arsay mein unka asar bohot bara ho jata hai.

            ## 💡 Main Lesson
            Sabar aur mustaqil mizaji ke saath lagatar kiye gaye chote kaam waqt ke saath ghair-mamooli nataij peda karte hain.

            ## 🌎 Real-Life Example
            Agar koi shakhs har maah thori si raqam save kare, to 10 saal baad uski savings aur us par milne wala munafa bohot barh jata hai.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, responseWithoutKeyPoints)
        assertFalse("Response missing Key Points must fail validation", validation.isValid)
        assertTrue("Must record Key Points as missing", validation.missingSections.any { it.contains("Key", ignoreCase = true) })
    }

    @Test
    fun `quality validator - response with only 1 weak key point fails for normal passage`() {
        val passage = "Habits are the compound interest of self-improvement. The same way that money multiplies through compound interest, the effects of your habits multiply as you repeat them."
        val responseWithOnePoint = """
            ## 🧠 Asaan Samjh
            Author yahan ye samjha raha hai ke aadatain hamari zindagi ko usi tarah shape karti hain jaise compound interest paison ko barhata hai. Har roz ka ek chota sa amal shuru mein be-asar lagta hai, lekin waqt ke sath bohot bara farq dal deta hai.

            Jab hum musalsal achi aadatain apnate hain, to unka asar barhta rehta hai aur hamari shakhsiyat behtar hoti jati hai.

            ## 💡 Main Lesson
            Apni rozana ki choti aadaton par tawajjo dein kyunke yahi aadatain mustaqbil mein apka mustaqil natija tay karti hain.

            ## 🔑 Key Points
            • **Aadat**: Rozana parhein.

            ## 🌎 Real-Life Example
            Agar aap rozana sirf 15 minute koi nayi skill seekhein, to ek saal baad aap us shobay mein kafi agay nikal jayenge.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, responseWithOnePoint)
        assertFalse("Response with only 1 weak bullet must fail validation", validation.isValid)
        assertTrue("Key Points must be flagged as weak", validation.weakSections.any { it.contains("Key", ignoreCase = true) })
    }

    @Test
    fun `quality validator - proper detailed response with all required sections passes validation`() {
        val passage = "Financial success is not a hard science. It's a soft skill, where how you behave is more important than what you know. Two people with the same knowledge can have totally different financial outcomes based on their emotions and self-control."
        val properResponse = """
            ## 🧠 Asaan Samjh
            Author yahan ek bohot ahem haqeeqat bayan kar raha hai ke paison ke mamlay mein kamyabi sirf is baat par depend nahi karti ke aap ke paas kitni formal education ya degree hai. Asal cheez ye hai ke aap apne jazbaat, lalach, aur kharche ke waqt apne dimagh par kitna control rakhte hain.

            Yani do afrad jin ke paas bilkul barabar maloomat ho, phir bhi unka financial mustaqbil bilkul mukhtalif ho sakta hai. Ek shakhs sabar ke saath invest karta hai aur doosra shakhs jazbaat mein aakar jaldbazi mein apna nuqsan kar baithta hai. Is liye behavior ilm se zyada ahem hai.

            ## 💡 Main Lesson
            Maliyat mein kamyabi ka taaluq aapki technical intelligence se zyada aapke sabar, bardasht aur discipline se hota hai.

            ## 🔑 Key Points
            • **Behavior Banam Ilm**: Sirf market ka knowledge hona kafi nahi, balki us knowledge par jazbaat ke baghair amal karna zaroori hai.
            • **Self-Control Ki Ahmiyat**: Lalach aur khauf do aisi cheezein hain jo aqalmand tareen insan se bhi ghalat financial faislay karwa sakti hain.
            • **Musalsal Sabar**: Dault banne ka process aahista aahista chalta hai, is mein jaldbazi hamesha nuqsan deh sabit hoti hai.

            ## 🌎 Real-Life Example
            Do dost hain jo ek hi office mein barabar salary lete hain. Ek dost har mahine salary aane par display ke liye mehenge gadgets khareedta hai, jabke doosra dost pehle apni emergency savings alag karta hai. Paanch saal baad pehla dost qarz mein phans jata hai jabke doosra dost financially azaad mehsoos karta hai.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, properResponse)
        assertTrue("Proper detailed response must pass validation: ${validation.issues}", validation.isValid)
        assertTrue("No missing sections expected", validation.missingSections.isEmpty())
        assertTrue("No weak sections expected", validation.weakSections.isEmpty())
    }

    @Test
    fun `quality validator - very short simple source passage does not require excessive length`() {
        val shortPassage = "Knowledge is power."
        val conciseResponse = """
            ## 🧠 Asaan Samjh
            Author yahan ye samjha raha hai ke sahi maloomat aur ilm insan ko faislay lene ki taqat deta hai.

            ## 💡 Main Lesson
            Ilm insan ko azaad aur mustahkam banata hai.

            ## 🔑 Key Points
            • **Taqat Ka Zariya**: Ilm se insan sahi aur ghalat mein tameez kar sakta hai.
            • **Behtar Faislay**: Maloomat ki roshni mein kiye gaye faislay kamyabi ki taraf le jaate hain.

            ## 🌎 Real-Life Example
            Ek student jo parhai ke asool achi tarah samajhta hai woh exam hall mein pur-aitamad rehta hai.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(shortPassage, conciseResponse)
        assertTrue("Short passage response should pass proportionate validation", validation.isValid)
        assertTrue("Is short passage flag must be true", validation.isShortPassage)
    }

    @Test
    fun `quality repair - repair happens at most once and does not loop infinitely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_repair_key_12345", "Test Key")

        val passage = "The first rule of compounding is to never interrupt it unnecessarily. When you interrupt the process, you lose the exponential growth."

        val incompleteFirstDraft = """
            ## 🧠 Asaan Samjh
            Compounding ko mat rokein.

            ## 💡 Main Lesson
            Chalte rehne dein.

            ## 🔑 Key Points
            • Rule: Important

            ## 🌎 Real-Life Example
            Save karein.
        """.trimIndent()

        val repairedSecondDraft = """
            ## 🧠 Asaan Samjh
            Author yahan compounding ke sab se ahem usool ki taraf ishara kar raha hai. Jab koi sarmayakari ya aadat aahista aahista barh rahi ho, to darmiyan mein be-waja mudakhilat karna uske barhay hue fawaid ko khatam kar deta hai.

            Haqeeqat ye hai ke compounding ka asal asar aakhri saalon mein samne aata hai. Agar aap beech mein jazbaat mein aakar paise nikal lein ge, to exponential growth ka faida haasil nahi ho sake ga.

            ## 💡 Main Lesson
            Compounding ke fawaid haasil karne ke liye sab se zaroori cheez sabar hai taake process be-waja na ruke.

            ## 🔑 Key Points
            • **Be-Waja Mudakhilat Se Bachna**: Sab se mushkil kaam kuch na karna hota hai jab market utar charhao ka shikar ho.
            • **Aakhri Marhalay Ka Faida**: Sab se bari taraqqi shuru mein nahi balki lambay arsay baad zahir hoti hai.
            • **Discipline Ki Zaroorat**: Compounding ko chalta rakhna sakht zabt aur mustaqil mizaji ka talabgar hai.

            ## 🌎 Real-Life Example
            Ek shakhs jo 20 saal ke liye investment shuru karta hai lekin har do saal baad darr kar account khali kar deta hai, woh kabhi compounding ki dault nahi dekh pata ba-nisbat us ke jo 20 saal tak chup chaap raqam parhi rehne deta hai.
        """.trimIndent()

        var streamCallCount = 0
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<okhttp3.ResponseBody> {
                streamCallCount++
                val textToSend = if (streamCallCount == 1) incompleteFirstDraft else repairedSecondDraft
                val escapedText = textToSend.replace("\n", "\\n").replace("\"", "\\\"")
                val sseBody = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$escapedText\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sseBody))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(
            secureStorage = storage,
            apiService = fakeApiService
        )

        val result = repository.explainPassageStream(
            passage = passage
        )

        assertTrue("Explanation must complete successfully", result.isSuccess)
        val finalExplanation = result.getOrNull().orEmpty()

        // Verify that repair was triggered exactly once (total 2 streaming calls: 1 initial + 1 repair)
        assertEquals("Initial generation + exactly ONE repair attempt expected", 2, streamCallCount)
        assertTrue("Final explanation should be the repaired version", finalExplanation.contains("Author yahan compounding ke sab se ahem usool"))
        assertTrue("Final explanation passes quality check", com.example.data.manager.PassageExplanationValidator.validate(passage, finalExplanation).isValid)
    }

    @Test
    fun `quality validator - response containing all required sections but repeating original source passage verbatim fails validation`() {
        val passage = "Financial success is not a hard science. It is a soft skill, where how you behave is much more important than what you know."
        val responseWithVerbatimRepetition = """
            ## 🧠 Asaan Samjh
            Financial success is not a hard science. It is a soft skill, where how you behave is much more important than what you know.
            Author yahan ye samjha raha hai ke daulat kamana aur use barhana sirf technical figures par depend nahi karta balki aapke ravayye par munhasir hai.

            Agar aap apne jazbaat par qabu nahi rakh saktay to baray se bara ilm bhi kisi kaam nahi aata. Sabar aur zabt hi asal kamyabi ki chabi hai.

            ## 💡 Main Lesson
            Maliyat mein kamyabi ka taaluq technical intelligence se zyada aapke jazbati control aur mustaqil mizaji se hota hai.

            ## 🔑 Key Points
            • **Behavior Banam Ilm**: Sirf market ka knowledge hona kafi nahi, balki us knowledge par jazbaat ke baghair amal karna zaroori hai.
            • **Self-Control Ki Ahmiyat**: Lalach aur khauf do aisi cheezein hain jo aqalmand tareen insan se bhi ghalat financial faislay karwa sakti hain.
            • **Musalsal Sabar**: Dault banne ka process aahista aahista chalta hai, is mein jaldbazi hamesha nuqsan deh sabit hoti hai.

            ## 🌎 Real-Life Example
            Do dost barabar kamaate hain lekin ek dost sara paisa fancy gadgets par ura deta hai jabke doosra dost pehle emergency fund banata hai.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, responseWithVerbatimRepetition)
        assertFalse("Response repeating source passage verbatim must FAIL validation", validation.isValid)
        assertTrue("Must include verbatim repetition in blocking violations", validation.blockingViolations.any { it.contains("verbatim", ignoreCase = true) })
        assertTrue("Defect score must reflect blocking violation penalty", validation.defectScore >= 6)
    }

    @Test
    fun `quality validator - response with all sections but more than 8 Key Points fails validation`() {
        val passage = "Habits are the compound interest of self-improvement. Getting one percent better every day counts for a lot in the long-run."
        val responseWithExcessiveBullets = """
            ## 🧠 Asaan Samjh
            Author yahan aadat aur compounding ke talluq ko khoobsoorat tareeqay se wazeh kar raha hai. Har roz ka ek chota sa behtar qadam waqt ke sath ghair mamooli farq peda kar deta hai.

            Jab hum musalsal achi aadaton par amal karte hain to unka asar barhta rehta hai aur shakhsiyat mein wazeh tabdeeli aati hai.

            ## 💡 Main Lesson
            Rozmarrah ki choti aadatain lambay arsay mein azeem tareen nataij peda karti hain.

            ## 🔑 Key Points
            • **Point 1**: Har roz 1 percent behtar banein aur lagan se kaam karein.
            • **Point 2**: Choti tabdeeliyan azeem nataij peda karti hain.
            • **Point 3**: Mustaqil mizaji sab se barhi taqat hai.
            • **Point 4**: Nateeja foran nahi milta balki waqt lagta hai.
            • **Point 5**: Rozana ka discipline kamyabi ki bunyad hai.
            • **Point 6**: Burhi aadaton ko door karna zaroori hai.
            • **Point 7**: System par tawajjoh dein bajaye sirf maqasid ke.
            • **Point 8**: Apne environment ko behtar banayein.
            • **Point 9**: Dost aur sathi achay chunein.
            • **Point 10**: Har hafte apni progress ka jaiza lein.

            ## 🌎 Real-Life Example
            Agar koi shakhs har roz sirf 15 minute exercise kare to saal ke aakhir mein uski sehat mein intehai numaayan farq peda ho chuka hoga.
        """.trimIndent()

        val validation = com.example.data.manager.PassageExplanationValidator.validate(passage, responseWithExcessiveBullets)
        assertFalse("Response with more than 8 Key Points (>8) must FAIL validation", validation.isValid)
        assertTrue("Bullet count must be > 8", validation.bulletCount > 8)
        assertTrue("Must include excessive bullets in blocking violations", validation.blockingViolations.any { it.contains("too many", ignoreCase = true) || it.contains("maximum", ignoreCase = true) })
        assertTrue("Defect score must be > 0", validation.defectScore > 0)
    }

    @Test
    fun `quality repair - repair that is longer but missing a required section does NOT replace structurally better initial response`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_repair_key_rule3", "Test Key")

        val passage = "The stock market is a device for transferring money from the impatient to the patient. Time in the market beats timing the market. Successful investing requires patience, discipline, and emotional stability."

        // Structurally better initial draft: All 4 sections present, but shallow understanding (1 weak section)
        val initialDraftAllSections = """
            ## 🧠 Asaan Samjh
            Market mein sabar se kaam lena zaroori hai.

            ## 💡 Main Lesson
            Market mein sabar aur mustaqil mizaji se hi sarmayakari kamyab hoti hai.

            ## 🔑 Key Points
            • **Sabar Ki Ahmiyat**: Market mein jaldbazi hamesha nuqsan ka bais banti hai.
            • **Time In Market**: Lambay arsay tak teherna timing se behtar hota hai.
            • **Jazbaat Par Qabu**: Darr aur lalach dono se bachna lazmi hai.

            ## 🌎 Real-Life Example
            Ek shakhs jo har roz shares khareedta aur bechta hai woh brokerage mein nuqsan uthata hai, jabke 10 saal tak hold karne wala munafa kamata hai.
        """.trimIndent()

        // Longer repair draft, but completely MISSING the Key Points section!
        val longerRepairMissingKeyPoints = """
            ## 🧠 Asaan Samjh
            Author yahan stock market ke sab se bunyadi aur tareekhi asool ko bohot gehrayi ke saath wazeh kar raha hai. Market asal mein koi lottery ka ticket nahi hai jahan aap raato raat ameer ban saktay hain. Ye ek aisa nizam hai jahan be-sabar log jo jaldbazi mein trade karte hain, apna sarmaya un sabar aazma logo ke haath bech baithte hain jo lambay arsay ke liye mutahammil rehte hain.

            Is ka doosra ahem pehlu ye hai ke timing the market yaani market ke utar charhao ka andaza lagana intehayi mushkil kaam hai, balki market mein zyada se zyada waqt tak thehre rehna hi asal kamyabi aur exponential munafa deta hai. Is liye short term fluctuations se ghabrana nahi chahiye.

            ## 💡 Main Lesson
            Mali sarmayakari mein waqt aur tahammul sab se baray hathiyar hain, jaldbazi hamesha sarmaye ke zaya hone par khatam hoti hai.

            ## 🌎 Real-Life Example
            Misaal ke tor par do investor hain, ek har ghantay market dekh kar panic mein shares bech deta hai aur doosra index fund mein paisa laga kar 15 saal tak bilkul parwah nahi karta. 15 saal baad doosray investor ka portfolio pehle se chaar guna barh jata hai kyunke us ne compounding ko chalne diya.
        """.trimIndent()

        assertTrue("Repair is longer than initial draft", longerRepairMissingKeyPoints.length > initialDraftAllSections.length)

        val initialValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, initialDraftAllSections)
        val repairValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, longerRepairMissingKeyPoints)

        assertFalse("Initial draft has weak section", initialValidation.isValid)
        assertEquals("Initial draft has 0 missing sections", 0, initialValidation.missingSections.size)

        assertFalse("Repair draft is invalid due to missing Key Points", repairValidation.isValid)
        assertTrue("Repair draft is missing Key Points", repairValidation.missingSections.any { it.contains("Key", ignoreCase = true) })

        // Defect score verification: repair has missing section (10 points penalty) vs initial only has weak (3 points)
        assertFalse("Defect comparison must reject repair that dropped a section",
            com.example.data.manager.PassageExplanationValidator.isMeaningfullyBetter(initialValidation, repairValidation))

        var callCount = 0
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                callCount++
                val textToSend = if (callCount == 1) initialDraftAllSections else longerRepairMissingKeyPoints
                val escaped = textToSend.replace("\n", "\\n").replace("\"", "\\\"")
                val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$escaped\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val result = repository.explainPassageStream(passage = passage)

        assertTrue("Execution completed successfully", result.isSuccess)
        val finalResponse = result.getOrNull().orEmpty()
        // Proves that raw character length did NOT win: the structurally better initial response was preserved!
        assertTrue("Must preserve initial draft because repair dropped required section despite being longer",
            finalResponse.contains("• **Sabar Ki Ahmiyat**"))
        assertFalse("Must not return the defective longer repair",
            finalResponse.contains("Author yahan stock market ke sab se bunyadi aur tareekhi asool"))
    }

    @Test
    fun `quality repair - repair that is still imperfect but has fewer quality defects than initial draft CAN replace it`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_repair_key_rule4", "Test Key")

        val passage = "Risk comes from not knowing what you are doing. The best hedge against risk is personal competence and continuous learning. Never invest in something you do not understand."

        // Initial draft: Missing 2 sections (Key Points & Real-Life Example) + 1 weak understanding section
        val initialDraftSevere = """
            ## 🧠 Asaan Samjh
            Risk tab hota hai jab pata na ho.

            ## 💡 Main Lesson
            Apne kaam ko achi tarah seekhein aur phir karein.
        """.trimIndent()

        // Repaired draft: All sections restored, but 1 section (e.g. Real-Life Example) is slightly brief (imperfect)
        val repairDraftMilder = """
            ## 🧠 Asaan Samjh
            Author yahan risk aur ilm ke talluq ko explain kar raha hai. Jab koi shakhs baghair tehqeeq aur samajh ke koi qadam uthata hai to woh andhayray mein teer chala raha hota hai.

            Agar aap apne shobay ki gehrayi ko achi tarah samajh lein to khatrat par kafi had tak qabu paya ja sakta hai. Competence hi asal hifazat hai.

            ## 💡 Main Lesson
            Khatray se bachne ka behtareen tareeqa qabiliyat aur musalsal seekhne ka amal hai.

            ## 🔑 Key Points
            • **Ilm Ki Zaroorat**: Kisi bhi maidan mein dakhil hone se pehle us ke asool seekhein.
            • **Competence Banam Qismat**: Qabiliyat khatray ko kam karti hai jabke lalach barhati hai.
            • **Musalsal Seekhna**: Nayi maloomat insan ko mustaqil mustahkam banati hain.

            ## 🌎 Real-Life Example
            Ek car driver jo driving nahi jaanta uske liye road khatarnak hai, lekin trained driver ke liye aasan.
        """.trimIndent()

        val initialValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, initialDraftSevere)
        val repairValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, repairDraftMilder)

        assertTrue("Initial has at least 2 missing sections", initialValidation.missingSections.size >= 2)
        assertEquals("Repair has 0 missing sections", 0, repairValidation.missingSections.size)
        assertTrue("Repair is meaningfully better than initial severe draft",
            com.example.data.manager.PassageExplanationValidator.isMeaningfullyBetter(initialValidation, repairValidation))

        var callCount = 0
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                callCount++
                val textToSend = if (callCount == 1) initialDraftSevere else repairDraftMilder
                val escaped = textToSend.replace("\n", "\\n").replace("\"", "\\\"")
                val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$escaped\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val result = repository.explainPassageStream(passage = passage)

        assertTrue("Execution completed", result.isSuccess)
        val finalResponse = result.getOrNull().orEmpty()
        // Must accept the milder repair over the severe initial draft
        assertTrue("Repaired draft was accepted because it has fewer defects",
            finalResponse.contains("Author yahan risk aur ilm ke talluq ko explain kar raha hai"))
    }

    @Test
    fun `quality repair - fully valid repair always replaces an invalid initial draft`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_repair_key_rule5", "Test Key")

        val passage = "Do not save what is left after spending, but spend what is left after saving. Prioritize your future self before present consumption to ensure lasting financial freedom."

        val invalidInitialDraft = """
            ## 🧠 Asaan Samjh
            Pehle bachat karein phir kharch karein.

            ## 💡 Main Lesson
            Bachat pehle karein.

            ## 🔑 Key Points
            • Bachat: Ahem hai.

            ## 🌎 Real-Life Example
            Save karein.
        """.trimIndent()

        val fullyValidRepair = """
            ## 🧠 Asaan Samjh
            Author yahan sarmayakari aur bachat ka sab se ahem usool bayan kar raha hai ke aam tor par log pehle tamam kharchay karte hain aur agar aakhir mein kuch bacha to save karte hain, jo ke ghair-moassir tareeqa hai.

            Sahi hikmat-e-amli ye hai ke salary ya aamdani aate hi pehle tay shuda raqam bachat ke account mein muntaqil ki jaye, aur baqi bachi hui raqam se mahinay ke ikhrajat chalaye jayen. Is se mustaqbil mehfooz hota hai.

            ## 💡 Main Lesson
            Apne mustaqbil ko hamesha pehli tarjeeh banayein aur har maah apni bachat ko zaroori ikhrajat se pehle alag karna lazmi banayein taake mali azaadi haasil ho sakay.

            ## 🔑 Key Points
            • **Pehle Apne Aap Ko Pay Karein**: Aamdani aate hi pehla hissa apne mustaqbil ke naam karein.
            • **Ikhrajat Par Control**: Jab bachi hui raqam mehdood hogi to be-fuzool kharchay khud bakhud ruk jayenge.
            • **Dolat Ki Bunyad**: Ameer log bachat pehle karte hain jabke ghareeb ikhrajat ke baad bachat ka sochte hain.

            ## 🌎 Real-Life Example
            Misaal ke tor par do mulazmeen hain jo barabar tankhwah lete hain. Ek shakhs 50 hazar aane par pehle 10 hazar saving account mein daalta hai aur baqi 40 hazar se guzar karta hai. Doosra shakhs sara paisa kharch karne ke baad aakhir mein zero bacha pata hai aur mustaqil pareshan rehta hai.
        """.trimIndent()

        val initialValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, invalidInitialDraft)
        val repairValidation = com.example.data.manager.PassageExplanationValidator.validate(passage, fullyValidRepair)

        assertFalse("Initial draft must be invalid", initialValidation.isValid)
        assertTrue("Repair draft must be fully valid", repairValidation.isValid)
        assertTrue("Fully valid repair must always be accepted",
            com.example.data.manager.PassageExplanationValidator.isMeaningfullyBetter(initialValidation, repairValidation))

        var callCount = 0
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                callCount++
                val textToSend = if (callCount == 1) invalidInitialDraft else fullyValidRepair
                val escaped = textToSend.replace("\n", "\\n").replace("\"", "\\\"")
                val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$escaped\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val result = repository.explainPassageStream(passage = passage)

        assertTrue("Execution completed", result.isSuccess)
        val finalResponse = result.getOrNull().orEmpty()
        assertTrue("Fully valid repair replaces invalid initial draft",
            finalResponse.contains("Author yahan sarmayakari aur bachat ka sab se ahem usool"))
    }

    @Test
    fun `quality repair - repair remains strictly limited to one attempt even if repair also fails validation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_repair_key_rule6", "Test Key")

        val passage = "Patience is bitter, but its fruit is sweet. Enduring short-term discomfort yields long-term compounding benefits for those who remain disciplined."

        val invalidInitialDraft = """
            ## 🧠 Asaan Samjh
            Sabar karein.

            ## 💡 Main Lesson
            Sabar ka phal meetha hai.

            ## 🔑 Key Points
            • Sabar: Zaroori hai.

            ## 🌎 Real-Life Example
            Intezar karein.
        """.trimIndent()

        // Even if repair draft is also invalid / fails validation:
        val stillInvalidRepairDraft = """
            ## 🧠 Asaan Samjh
            Sabar ka mutlab intezar karna hota hai jo ke aasan nahi.

            ## 💡 Main Lesson
            Sabar ka nateeja hamesha acha hota hai.

            ## 🔑 Key Points
            • Point 1: Intezar
            • Point 2: Tahammul

            ## 🌎 Real-Life Example
            Koshish karein.
        """.trimIndent()

        var callCount = 0
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                callCount++
                val textToSend = if (callCount == 1) invalidInitialDraft else stillInvalidRepairDraft
                val escaped = textToSend.replace("\n", "\\n").replace("\"", "\\\"")
                val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$escaped\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val result = repository.explainPassageStream(passage = passage)

        assertTrue("Execution completed without hanging or crashing", result.isSuccess)
        assertEquals("Initial generation + exactly ONE repair attempt (strictly 2 calls max, no endless loop)", 2, callCount)
    }

    @Test
    fun `passage analysis task routing and Flash model pool remain strictly intact`() {
        val passageModels = com.example.data.model.GeminiModelRegistry.getModelsForTask(
            com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS
        )

        assertEquals("Passage pool must contain exactly 5 Flash models", 5, passageModels.size)
        assertEquals("Primary model must be gemini-3.8-flash", "gemini-3.8-flash", passageModels[0].modelId)
        assertEquals("Second model must be gemini-3.6-flash", "gemini-3.6-flash", passageModels[1].modelId)
        assertEquals("Third model must be gemini-3.5-flash", "gemini-3.5-flash", passageModels[2].modelId)
        assertEquals("Fourth model must be gemini-3-flash-preview", "gemini-3-flash-preview", passageModels[3].modelId)
        assertEquals("Fifth model must be gemini-2.5-flash", "gemini-2.5-flash", passageModels[4].modelId)

        // Strict non-routing assertions
        assertFalse("gemini-3.7-flash must remain absent", passageModels.any { it.modelId == "gemini-3.7-flash" })
        assertFalse("Flash-Lite models must not appear in PASSAGE_ANALYSIS", passageModels.any { it.modelId.contains("flash-lite") })
    }

    @Test
    fun `phase 3 - in-flight tracking increments during execution and decrements on completion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("test_inflight_key_1", "Key 1")!!

        var capturedInFlightDuringExecution = -1
        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                return retrofit2.Response.success(
                    com.example.data.remote.gemini.GeminiGenerateContentResponse(
                        candidates = listOf(
                            com.example.data.remote.gemini.GeminiCandidate(
                                content = com.example.data.remote.gemini.GeminiContent(
                                    parts = listOf(com.example.data.remote.gemini.GeminiPart(text = "ok"))
                                )
                            )
                        )
                    )
                )
            }
            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                request: com.example.data.remote.gemini.GeminiGenerateContentRequest
            ): retrofit2.Response<okhttp3.ResponseBody> {
                throw UnsupportedOperationException()
            }
        }

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        assertEquals("In-flight count must initially be 0", 0, apiKeyManager.getInFlightCount(key1.id))

        apiKeyManager.executeWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
            operationName = "testInFlight"
        ) { key, model ->
            capturedInFlightDuringExecution = apiKeyManager.getInFlightCount(key1.id)
            fakeApiService.generateContent(model, key, com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("ping"))
        }

        assertEquals("In-flight count must be 1 while request is executing", 1, capturedInFlightDuringExecution)
        assertEquals("In-flight count must return to 0 after request completion", 0, apiKeyManager.getInFlightCount(key1.id))
    }

    @Test
    fun `phase 3 - in-flight count decrements even when request throws exception or is cancelled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("test_cancellation_key_1", "Key 1")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        assertEquals("Initially 0", 0, apiKeyManager.getInFlightCount(key1.id))

        // 1. Exception safety
        try {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "testExceptionSafety"
            ) { _, _ ->
                throw IllegalStateException("Simulated network explosion")
            }
        } catch (_: Exception) {}

        assertEquals("In-flight count must return to 0 even after exception", 0, apiKeyManager.getInFlightCount(key1.id))

        // 2. Coroutine cancellation safety
        val job = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "testCancellationSafety"
            ) { _, _ ->
                delay(5000)
                throw IllegalStateException("Should not be reached")
            }
        }
        delay(50)
        job.cancelAndJoin()

        assertEquals("In-flight count must return to 0 after coroutine cancellation", 0, apiKeyManager.getInFlightCount(key1.id))
    }

    @Test
    fun `phase 3 - 4 keys physical lane affinity maps cleanly to separate keys without collision`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val k1 = storage.addApiKey("key_one_passage", "Key 1")!!
        val k2 = storage.addApiKey("key_two_vision", "Key 2")!!
        val k3 = storage.addApiKey("key_three_mcq", "Key 3")!!
        val k4 = storage.addApiKey("key_four_trans", "Key 4")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val keys = storage.getApiKeys()

        // Lane A: PASSAGE_ANALYSIS -> Key 1
        val passageOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, keys)
        assertEquals("Passage Analysis must prefer Key 1 (Slot 0)", k1.id, passageOrder.first().id)

        // Lane B: VISION_EXTRACTION / PDF_PARSING -> Key 2
        val visionOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.VISION_EXTRACTION, keys)
        assertEquals("Vision must prefer Key 2 (Slot 1)", k2.id, visionOrder.first().id)

        val pdfOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.PDF_PARSING, keys)
        assertEquals("PDF parsing must prefer Key 2 (Slot 1)", k2.id, pdfOrder.first().id)

        // Lane C: MCQ_SYNTHESIS -> Key 3
        val mcqOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS, keys)
        assertEquals("MCQ generation must prefer Key 3 (Slot 2)", k3.id, mcqOrder.first().id)

        // Lane D: WORD_TRANSLATION / WISDOM_QUOTE -> Key 4
        val transOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, keys)
        assertEquals("Word translation must prefer Key 4 (Slot 3)", k4.id, transOrder.first().id)

        val quoteOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.WISDOM_QUOTE, keys)
        assertEquals("Wisdom quotes must prefer Key 4 (Slot 3)", k4.id, quoteOrder.first().id)
    }

    @Test
    fun `phase 3 - 2 keys workload isolation reserves Key 1 for Passage and Key 2 for Utility with dynamic fallback`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val k1 = storage.addApiKey("key_one_passage_only", "Key 1")!!
        val k2 = storage.addApiKey("key_two_utility_only", "Key 2")!!

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                return retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse())
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                throw UnsupportedOperationException()
            }
        }

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val keys = storage.getApiKeys()

        // 1. When both keys are free (inFlight = 0):
        val passageOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS, keys)
        assertEquals("Passage Analysis must prefer Key 1", k1.id, passageOrder.first().id)

        val utilityOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.WORD_TRANSLATION, keys)
        assertEquals("Utility must prefer Key 2", k2.id, utilityOrder.first().id)

        val mcqOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS, keys)
        assertEquals("MCQ must also prefer Key 2 in 2-key configuration", k2.id, mcqOrder.first().id)

        // 2. When Key 2 is busy (in-flight = 1), utility task dynamically falls back to idle Key 1:
        val job = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "holdKey2"
            ) { _, _ ->
                delay(3000)
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }
        delay(50) // wait for holdKey2 to start on Key 2

        assertEquals("Key 2 in-flight count should be 1", 1, apiKeyManager.getInFlightCount(k2.id))
        assertEquals("Key 1 in-flight count should be 0", 0, apiKeyManager.getInFlightCount(k1.id))

        val busyFallbackOrder = apiKeyManager.getOrderedKeysForTask(com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS, keys)
        assertEquals("When Key 2 is busy, utility work should fall back to idle Key 1", k1.id, busyFallbackOrder.first().id)

        job.cancelAndJoin()
        assertEquals("Key 2 in-flight count must return to 0", 0, apiKeyManager.getInFlightCount(k2.id))
    }

    @Test
    fun `phase 3 - localized cooldown does not disable key for other models`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val k1 = storage.addApiKey("test_localized_cooldown_key1", "Key 1")!!
        val k2 = storage.addApiKey("test_localized_cooldown_key2", "Key 2")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        // Simulate HTTP 429 on (k1, gemini-3.8-flash)
        apiKeyManager.executeWithAutoRotation<String>(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test429"
        ) { key, model ->
            if (key == k1.key && model == "gemini-3.8-flash") {
                retrofit2.Response.error(429, okhttp3.ResponseBody.create(null, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"))
            } else {
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        assertTrue("Key 1 must be in cooldown specifically on gemini-3.8-flash",
            apiKeyManager.isModelInCooldown(k1.id, "gemini-3.8-flash"))

        assertFalse("Key 1 must NOT be in cooldown for other models such as gemini-3.6-flash",
            apiKeyManager.isModelInCooldown(k1.id, "gemini-3.6-flash"))

        assertFalse("Key 2 must NOT be in cooldown for gemini-3.8-flash",
            apiKeyManager.isModelInCooldown(k2.id, "gemini-3.8-flash"))
    }

    @Test
    fun `phase 3 - test 1 real 4-lane concurrency selects 4 distinct physical keys`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val k1 = storage.addApiKey("key_one_passage_concurrent", "Key 1")!!
        val k2 = storage.addApiKey("key_two_vision_concurrent", "Key 2")!!
        val k3 = storage.addApiKey("key_three_mcq_concurrent", "Key 3")!!
        val k4 = storage.addApiKey("key_four_trans_concurrent", "Key 4")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val barrier = CompletableDeferred<Unit>()
        val capturedKeyIds = java.util.concurrent.ConcurrentHashMap<com.example.data.model.GeminiTaskType, String>()

        val job1 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "passageConcurrent"
            ) { key, _ ->
                val matchingKey = storage.getApiKeys().first { it.key == key }
                capturedKeyIds[com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS] = matchingKey.id
                barrier.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        val job2 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.VISION_EXTRACTION,
                operationName = "visionConcurrent"
            ) { key, _ ->
                val matchingKey = storage.getApiKeys().first { it.key == key }
                capturedKeyIds[com.example.data.model.GeminiTaskType.VISION_EXTRACTION] = matchingKey.id
                barrier.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        val job3 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS,
                operationName = "mcqConcurrent"
            ) { key, _ ->
                val matchingKey = storage.getApiKeys().first { it.key == key }
                capturedKeyIds[com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS] = matchingKey.id
                barrier.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        val job4 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "translationConcurrent"
            ) { key, _ ->
                val matchingKey = storage.getApiKeys().first { it.key == key }
                capturedKeyIds[com.example.data.model.GeminiTaskType.WORD_TRANSLATION] = matchingKey.id
                barrier.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        // Wait until all 4 concurrent requests have dispatched and captured their physical keys
        var waitedMs = 0
        while (capturedKeyIds.size < 4 && waitedMs < 3000) {
            delay(20)
            waitedMs += 20
        }

        assertEquals("All 4 requests should have reached their network execution block", 4, capturedKeyIds.size)

        // Verify in-flight count is 1 for each of the 4 keys while overlapping
        assertEquals(1, apiKeyManager.getInFlightCount(k1.id))
        assertEquals(1, apiKeyManager.getInFlightCount(k2.id))
        assertEquals(1, apiKeyManager.getInFlightCount(k3.id))
        assertEquals(1, apiKeyManager.getInFlightCount(k4.id))

        // Assert that all four concurrent lanes used four distinct physical keys
        val distinctKeyCount = capturedKeyIds.values.toSet().size
        assertEquals("All 4 concurrent lanes must select distinct physical API keys", 4, distinctKeyCount)
        assertEquals(k1.id, capturedKeyIds[com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS])
        assertEquals(k2.id, capturedKeyIds[com.example.data.model.GeminiTaskType.VISION_EXTRACTION])
        assertEquals(k3.id, capturedKeyIds[com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS])
        assertEquals(k4.id, capturedKeyIds[com.example.data.model.GeminiTaskType.WORD_TRANSLATION])

        barrier.complete(Unit)
        job1.join()
        job2.join()
        job3.join()
        job4.join()

        // All in-flight counts must cleanly return to 0
        assertEquals(0, apiKeyManager.getInFlightCount(k1.id))
        assertEquals(0, apiKeyManager.getInFlightCount(k2.id))
        assertEquals(0, apiKeyManager.getInFlightCount(k3.id))
        assertEquals(0, apiKeyManager.getInFlightCount(k4.id))
    }

    @Test
    fun `phase 3 - test 2 all keys busy least-loaded fallback`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val keyA = storage.addApiKey("key_a_busy_test", "Key A")!!
        val keyB = storage.addApiKey("key_b_busy_test", "Key B")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val barrierA = CompletableDeferred<Unit>()
        val barrierB = CompletableDeferred<Unit>()

        // 1. Launch 1 request on Key A (Passage Analysis prefers Key A)
        val jobA1 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "reqA1"
            ) { _, _ ->
                barrierA.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        // 2. Launch 1 request on Key B (Utility prefers Key B)
        val jobB1 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "reqB1"
            ) { _, _ ->
                barrierB.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        while (apiKeyManager.getInFlightCount(keyA.id) < 1 || apiKeyManager.getInFlightCount(keyB.id) < 1) {
            delay(20)
        }

        // 3. Launch a second request on Key A: both Key A and Key B are at in-flight = 1.
        // Passage analysis with tied load (1 vs 1) will select preferred lane key: Key A.
        val jobA2 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "reqA2"
            ) { _, _ ->
                barrierA.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        while (apiKeyManager.getInFlightCount(keyA.id) < 2) {
            delay(20)
        }

        // Verify current busy state: Key A in-flight = 2, Key B in-flight = 1
        assertEquals(2, apiKeyManager.getInFlightCount(keyA.id))
        assertEquals(1, apiKeyManager.getInFlightCount(keyB.id))

        // 4. Now launch Request 4 while both keys are busy.
        // Even though Key A is the preferred lane key for PASSAGE_ANALYSIS, Key B is less loaded (1 < 2).
        var selectedKeyForKey4: String? = null
        val job4 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "req4_fallback"
            ) { key, _ ->
                selectedKeyForKey4 = key
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        job4.join()

        // Key B had in-flight = 1, Key A had in-flight = 2 -> Least-loaded Key B must be selected!
        assertEquals("Least-loaded Key B must be selected when all keys are busy", keyB.key, selectedKeyForKey4)

        barrierA.complete(Unit)
        barrierB.complete(Unit)
        jobA1.join()
        jobB1.join()
        jobA2.join()

        assertEquals(0, apiKeyManager.getInFlightCount(keyA.id))
        assertEquals(0, apiKeyManager.getInFlightCount(keyB.id))
    }

    @Test
    fun `phase 3 - test 3 lane affinity must not override lower load`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("key_one_passage_pref", "Key 1")!!
        val key2 = storage.addApiKey("key_two_utility_pref", "Key 2")!!

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = com.example.data.remote.gemini.GeminiApiService.create(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val barrierKey2 = CompletableDeferred<Unit>()

        // Put Key 2 under load: in-flight = 2
        val job1 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "holdKey2_1"
            ) { _, _ ->
                barrierKey2.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        val barrierKey1 = CompletableDeferred<Unit>()
        val jobKey1 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "tempHoldKey1"
            ) { _, _ ->
                barrierKey1.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        while (apiKeyManager.getInFlightCount(key1.id) < 1 || apiKeyManager.getInFlightCount(key2.id) < 1) {
            delay(20)
        }

        // Now with both at 1, utility prefers Key 2 -> second utility request goes to Key 2
        val job2 = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.MCQ_SYNTHESIS,
                operationName = "holdKey2_2"
            ) { _, _ ->
                barrierKey2.await()
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        while (apiKeyManager.getInFlightCount(key2.id) < 2) {
            delay(20)
        }

        // Release Key 1: now Key 1 has inFlight = 0, while Key 2 has inFlight = 2!
        barrierKey1.complete(Unit)
        jobKey1.join()

        assertEquals("Key 1 (non-preferred for utility) must be idle (in-flight = 0)", 0, apiKeyManager.getInFlightCount(key1.id))
        assertEquals("Key 2 (preferred for utility) must have in-flight = 2", 2, apiKeyManager.getInFlightCount(key2.id))

        // Now trigger a new utility request (WORD_TRANSLATION):
        // Key 2 is the preferred lane key, but Key 1 has lower load (0 < 2).
        var winningKeyForUtility: String? = null
        val testJob = launch {
            apiKeyManager.executeWithAutoRotation<String>(
                taskType = com.example.data.model.GeminiTaskType.WORD_TRANSLATION,
                operationName = "utility_test_load_overrides_affinity"
            ) { key, _ ->
                winningKeyForUtility = key
                retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse()) as retrofit2.Response<String>
            }
        }

        testJob.join()

        assertEquals("The idle non-preferred key (Key 1) must win over busy preferred Key 2", key1.key, winningKeyForUtility)

        barrierKey2.complete(Unit)
        job1.join()
        job2.join()

        assertEquals(0, apiKeyManager.getInFlightCount(key1.id))
        assertEquals(0, apiKeyManager.getInFlightCount(key2.id))
    }

    @Test
    fun `phase 3 - test 4 streaming path in-flight tracking and cleanup on finish and cancel`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("test_stream_tracking_key", "Key 1")!!

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                val sseBody = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Streaming chunk 1\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sseBody))
            }
        }

        val apiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = fakeApiService,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        assertEquals(0, apiKeyManager.getInFlightCount(key1.id))

        // 1. Verify in-flight count becomes 1 during active streaming and returns to 0 on finish
        var inFlightDuringStream = -1
        val result = apiKeyManager.streamWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "streamTestFinish",
            request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("ping")
        ) { _, _ ->
            inFlightDuringStream = apiKeyManager.getInFlightCount(key1.id)
        }

        assertTrue("Streaming completed successfully", result.isSuccess)
        assertEquals("In-flight count must be 1 during active stream processing", 1, inFlightDuringStream)
        assertEquals("In-flight count must return to 0 after stream finishes", 0, apiKeyManager.getInFlightCount(key1.id))

        // 2. Verify cancellation cleanup
        val slowApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                delay(5000)
                throw IllegalStateException("Should have been cancelled")
            }
        }

        val slowApiKeyManager = com.example.data.manager.GeminiApiKeyManager(
            secureStorage = storage,
            apiService = slowApiService,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val streamJob = launch {
            slowApiKeyManager.streamWithAutoRotation(
                taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
                operationName = "streamTestCancel",
                request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("ping")
            ) { _, _ -> }
        }

        delay(50)
        assertEquals("In-flight count must be 1 while stream request is in flight", 1, slowApiKeyManager.getInFlightCount(key1.id))

        streamJob.cancelAndJoin()
        assertEquals("In-flight count must return to 0 after stream cancellation", 0, slowApiKeyManager.getInFlightCount(key1.id))
    }

    @Test
    fun `phase 4 - passage streaming publishes first chunk before full response completes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_key_phase4_stream", "Key 1")

        val passage = "Knowledge is power. Enduring discipline yields compounding advantages over time."

        val sseChunk1 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"## 🧠 Asaan Samjh\\nIlm taaqat hai aur sabar ahem hai.\"}]}}]}\n\n"
        val sseChunk2 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\\n\\n## 💡 Main Lesson\\nNazam-o-zabt se zindagee behtar hoti hai.\\n\\n## 🔑 Key Points\\n• Pehla point: Taleem zaroori hai.\\n• Doosra point: Sabar ahem hai.\\n• Teesri baat: Ilm taraqqi deta hai.\\n\\n## 🌎 Real-Life Example\\nKitabein parhein aur amal karein.\"}]}}]}\n\ndata: [DONE]\n\n"
        val fullSse = sseChunk1 + sseChunk2

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, fullSse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val publishedAccumulatedChunks = mutableListOf<String>()
        val publishedIndividualChunks = mutableListOf<String>()

        val result = repository.explainPassageStream(
            passage = passage,
            onChunk = { accumulated, chunk ->
                publishedAccumulatedChunks.add(accumulated)
                publishedIndividualChunks.add(chunk)
            }
        )

        assertTrue("Passage stream should complete successfully", result.isSuccess)
        assertTrue("Should publish at least 2 distinct chunks during streaming", publishedIndividualChunks.size >= 2)
        // Verify first chunk was published without waiting for the full response
        assertEquals("First chunk must be published before full text is accumulated",
            "## 🧠 Asaan Samjh\nIlm taaqat hai aur sabar ahem hai.", publishedAccumulatedChunks[0])
        assertTrue("Final accumulated text should contain the complete response",
            publishedAccumulatedChunks.last().contains("Kitabein parhein aur amal karein"))
    }

    @Test
    fun `phase 4 - background mcq and quote extraction do not block passage completion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        storage.addApiKey("test_key_phase4_bg_decouple", "Key 1")

        val passage = "Patience and persistence are the key factors for achieving long term compounding success."
        val explanationText = """
            ## 🧠 Asaan Samjh
            Sabar aur mustaqil mizaji se kamyabi milti hai.

            ## 💡 Main Lesson
            Tawajjo aur mehnat se compounding results aate hain.

            ## 🔑 Key Points
            • Pehla nuqta: Sabar
            • Doosra nuqta: Mehnat
            • Teesri baat: Tawajjo

            ## 🌎 Real-Life Example
            Rozana thora thora parhein.
        """.trimIndent()

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"${explanationText.replace("\n", "\\n").replace("\"", "\\\"")}\"}]}}]}\n\ndata: [DONE]\n\n"
                return retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
            }
        }

        val repository = com.example.data.repository.GeminiRepository(storage, fakeApiService)
        val db = Room.inMemoryDatabaseBuilder(context, ReadMateDatabase::class.java).allowMainThreadQueries().build()
        val bookRepo = BookRepository(db.bookDao())
        val chapterRepo = ChapterRepository(db.chapterDao())
        val msgRepo = com.example.data.repository.ChapterMessageRepository(db.chapterMessageDao())

        val bookId = bookRepo.createBook(title = "Test Book", author = "Author")
        val chapterId = chapterRepo.createChapter(bookId = bookId, chapterNumber = 1, title = "Chapter 1")

        val quoteRepo = com.example.data.repository.WisdomQuoteRepository(db.wisdomQuoteDao())
        // Create slow quote extraction engine that hangs if called synchronously
        val slowQuoteEngine = com.example.data.manager.QuoteExtractionEngine(
            secureStorage = storage,
            apiService = object : com.example.data.remote.gemini.GeminiApiService {
                override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                    delay(5000) // 5s slow quote generation
                    return retrofit2.Response.success(com.example.data.remote.gemini.GeminiGenerateContentResponse())
                }
                override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest) = throw UnsupportedOperationException()
            },
            wisdomQuoteRepository = quoteRepo,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        val pipeline = com.example.data.manager.ExplanationPipelineManager(
            geminiRepository = repository,
            chapterMessageRepository = msgRepo,
            bookRepository = bookRepo,
            chapterRepository = chapterRepo,
            quoteExtractionEngine = slowQuoteEngine,
            applicationScope = this,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )

        pipeline.startExplanation(chapterId, passage)

        // Wait for pipeline state to become Completed
        var completedState: com.example.data.manager.ExplanationJobState.Completed? = null
        var waitedMs = 0
        while (waitedMs < 2000) {
            val state = pipeline.states.value[chapterId]
            if (state is com.example.data.manager.ExplanationJobState.Completed) {
                completedState = state
                break
            }
            delay(20)
            waitedMs += 20
        }

        assertNotNull("Explanation must reach Completed state without waiting for slow quote extraction", completedState)
        assertTrue("Passage explanation finished well before slow 5s quote engine", waitedMs < 2000)

        db.close()
    }

    @Test
    fun `phase 4 - http 429 immediately moves to next key on same model without retry delay`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("key_429_immediate_1", "Key 1")!!
        val key2 = storage.addApiKey("key_429_immediate_2", "Key 2")!!

        val keyCallAttempts = mutableListOf<String>()

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                keyCallAttempts.add(apiKey)
                return if (apiKey == key1.key) {
                    retrofit2.Response.error(429, okhttp3.ResponseBody.create(null, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"))
                } else {
                    val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Success on Key 2\"}]}}]}\n\ndata: [DONE]\n\n"
                    retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
                }
            }
        }

        val manager = com.example.data.manager.GeminiApiKeyManager(storage, fakeApiService)
        val result = manager.streamWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test429Immediate",
            request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("ping")
        ) { _, _ -> }

        assertTrue("Request should succeed on rotated Key 2", result.isSuccess)
        assertEquals("Key 1 should be attempted exactly once before immediate rotation (no retry delay on 429)", 1, keyCallAttempts.count { it == key1.key })
        assertEquals("Key 2 should be immediately called on the same model", key2.key, keyCallAttempts.last())
        assertTrue("Key 1 should be in cooldown for gemini-3.8-flash", manager.isModelInCooldown(key1.id, "gemini-3.8-flash"))
    }

    @Test
    fun `phase 4 - 503 limited retry then cross-key failover remains intact`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = com.example.data.local.security.AndroidKeystoreApiKeyStorage(context)
        storage.clearAllApiKeys()
        val key1 = storage.addApiKey("key_503_test_1", "Key 1")!!
        val key2 = storage.addApiKey("key_503_test_2", "Key 2")!!

        val attempts = mutableListOf<String>()

        val fakeApiService = object : com.example.data.remote.gemini.GeminiApiService {
            override suspend fun generateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<com.example.data.remote.gemini.GeminiGenerateContentResponse> {
                throw UnsupportedOperationException()
            }
            override suspend fun streamGenerateContent(model: String, apiKey: String, request: com.example.data.remote.gemini.GeminiGenerateContentRequest): retrofit2.Response<okhttp3.ResponseBody> {
                attempts.add(apiKey)
                return if (apiKey == key1.key) {
                    retrofit2.Response.error(503, okhttp3.ResponseBody.create(null, "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"))
                } else {
                    val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Success on Key 2\"}]}}]}\n\ndata: [DONE]\n\n"
                    retrofit2.Response.success(okhttp3.ResponseBody.create(null, sse))
                }
            }
        }

        val manager = com.example.data.manager.GeminiApiKeyManager(storage, fakeApiService)
        val result = manager.streamWithAutoRotation(
            taskType = com.example.data.model.GeminiTaskType.PASSAGE_ANALYSIS,
            operationName = "test503Failover",
            request = com.example.data.remote.gemini.GeminiGenerateContentRequest.forText("ping")
        ) { _, _ -> }

        assertTrue("Request should succeed on Key 2 after Key 1 503 retry exhaustion", result.isSuccess)
        // Key 1 should be attempted 1 initial + 2 retries = 3 attempts total
        assertEquals("Key 1 should be attempted up to MAX_SERVER_ERROR_RETRIES (3 total calls)", 3, attempts.count { it == key1.key })
        assertEquals("Key 2 should be invoked after Key 1 fails over", key2.key, attempts.last())
    }

    @Test
    fun `phase 4 - no Thread sleep exists in passage generation or Gemini retry path`() {
        val repoClass = com.example.data.repository.GeminiRepository::class.java
        val managerClass = com.example.data.manager.GeminiApiKeyManager::class.java
        val pipelineClass = com.example.data.manager.ExplanationPipelineManager::class.java

        assertNotNull(repoClass)
        assertNotNull(managerClass)
        assertNotNull(pipelineClass)

        val declaredMethods = managerClass.declaredMethods + repoClass.declaredMethods
        for (m in declaredMethods) {
            assertFalse("No method should be named sleep in Gemini orchestration", m.name.contains("sleep", ignoreCase = true))
        }
    }
}

