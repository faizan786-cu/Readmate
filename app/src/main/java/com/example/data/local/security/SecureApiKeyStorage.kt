package com.example.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.KeyStatus
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecureApiKeyStorage {
    // Multi-key operations
    fun getApiKeys(): List<GeminiApiKeyItem>
    fun saveApiKeys(keys: List<GeminiApiKeyItem>): Boolean
    fun addApiKey(key: String, label: String = ""): GeminiApiKeyItem?
    fun updateApiKey(item: GeminiApiKeyItem): Boolean
    fun removeApiKey(id: String): Boolean
    fun clearAllApiKeys(): Boolean

    // Legacy single-key compatibility operations
    fun saveApiKey(apiKey: String): Boolean
    fun getApiKey(): String?
    fun hasApiKey(): Boolean
    fun getMaskedApiKey(): String?
    fun deleteApiKey(): Boolean

    // Dual-Key specific operations
    fun hasValidCredentials(): Boolean
    fun getPrimaryApiKey(): String?
    fun getSecondaryApiKey(): String?
    fun saveDualApiKeys(primaryKey: String, secondaryKey: String? = null): Boolean
}

typealias ApiKeyStorage = SecureApiKeyStorage

class AndroidKeystoreApiKeyStorage(
    private val context: Context
) : SecureApiKeyStorage {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val keyListType = Types.newParameterizedType(List::class.java, GeminiApiKeyItem::class.java)
    private val keyListAdapter: JsonAdapter<List<GeminiApiKeyItem>> = moshi.adapter(keyListType)

    companion object {
        private const val PREFS_NAME = "readmate_secure_prefs"
        private const val KEY_ENCRYPTED_KEYS_JSON = "encrypted_gemini_keys_json"
        private const val KEY_KEYS_IV = "gemini_keys_iv"
        // Legacy single-key keys for migration
        private const val KEY_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
        private const val KEY_IV = "gemini_api_key_iv"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "readmate_gemini_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    @Synchronized
    override fun getApiKeys(): List<GeminiApiKeyItem> {
        return try {
            val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_KEYS_JSON, null)
            val ivBase64 = prefs.getString(KEY_KEYS_IV, null)

            if (encryptedBase64 != null && ivBase64 != null) {
                val decryptedJson = decryptString(encryptedBase64, ivBase64)
                if (!decryptedJson.isNullOrBlank()) {
                    val parsed = keyListAdapter.fromJson(decryptedJson)
                    if (parsed != null && parsed.isNotEmpty()) {
                        return parsed
                    }
                }
            }

            // Automatic Migration: check for legacy single API key
            val legacySingleKey = getLegacyApiKey()
            if (!legacySingleKey.isNullOrBlank()) {
                val migratedItem = GeminiApiKeyItem(
                    key = legacySingleKey,
                    label = "Primary Key",
                    status = KeyStatus.ACTIVE
                )
                val migratedList = listOf(migratedItem)
                saveApiKeys(migratedList)
                // Clean up legacy keys
                prefs.edit().remove(KEY_ENCRYPTED_API_KEY).remove(KEY_IV).apply()
                return migratedList
            }

            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    override fun saveApiKeys(keys: List<GeminiApiKeyItem>): Boolean {
        return try {
            val json = keyListAdapter.toJson(keys)
            val encryptedPair = encryptString(json) ?: return false

            prefs.edit()
                .putString(KEY_ENCRYPTED_KEYS_JSON, encryptedPair.first)
                .putString(KEY_KEYS_IV, encryptedPair.second)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    override fun addApiKey(key: String, label: String): GeminiApiKeyItem? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return null

        val currentKeys = getApiKeys().toMutableList()
        // Check for duplicate key
        val existing = currentKeys.firstOrNull { it.key.trim() == trimmed }
        if (existing != null) {
            // Update label if provided and reset status to active
            val updated = existing.copy(
                label = if (label.isNotBlank()) label.trim() else existing.label,
                status = KeyStatus.ACTIVE,
                errorMessage = null
            )
            val index = currentKeys.indexOf(existing)
            currentKeys[index] = updated
            saveApiKeys(currentKeys)
            return updated
        }

        val nextIndex = currentKeys.size + 1
        val assignedLabel = if (label.isNotBlank()) label.trim() else "Key $nextIndex"
        val newItem = GeminiApiKeyItem(
            key = trimmed,
            label = assignedLabel,
            status = KeyStatus.ACTIVE
        )
        currentKeys.add(newItem)
        val saved = saveApiKeys(currentKeys)
        return if (saved) newItem else null
    }

    @Synchronized
    override fun updateApiKey(item: GeminiApiKeyItem): Boolean {
        val currentKeys = getApiKeys().toMutableList()
        val index = currentKeys.indexOfFirst { it.id == item.id }
        if (index == -1) return false

        currentKeys[index] = item
        return saveApiKeys(currentKeys)
    }

    @Synchronized
    override fun removeApiKey(id: String): Boolean {
        val currentKeys = getApiKeys().toMutableList()
        val removed = currentKeys.removeAll { it.id == id }
        if (removed) {
            return saveApiKeys(currentKeys)
        }
        return false
    }

    @Synchronized
    override fun clearAllApiKeys(): Boolean {
        return try {
            prefs.edit()
                .remove(KEY_ENCRYPTED_KEYS_JSON)
                .remove(KEY_KEYS_IV)
                .remove(KEY_ENCRYPTED_API_KEY)
                .remove(KEY_IV)
                .commit()
        } catch (e: Exception) {
            false
        }
    }

    // --- Legacy Single-Key Compatibility Implementations ---

    @Synchronized
    override fun saveApiKey(apiKey: String): Boolean {
        val item = addApiKey(apiKey, label = "Primary Key")
        return item != null
    }

    @Synchronized
    override fun getApiKey(): String? {
        val keys = getApiKeys()
        if (keys.isEmpty()) return null
        val available = keys.firstOrNull { it.isAvailable() }
        return available?.key ?: keys.first().key
    }

    override fun hasApiKey(): Boolean {
        return getApiKeys().isNotEmpty()
    }

    override fun getMaskedApiKey(): String? {
        val keys = getApiKeys()
        if (keys.isEmpty()) return null
        val activeCount = keys.count { it.status == KeyStatus.ACTIVE }
        return if (keys.size == 1) {
            keys.first().maskedKey
        } else {
            "${keys.size} Keys ($activeCount Active)"
        }
    }

    @Synchronized
    override fun deleteApiKey(): Boolean {
        return clearAllApiKeys()
    }

    override fun hasValidCredentials(): Boolean {
        val primary = getPrimaryApiKey()
        return !primary.isNullOrBlank()
    }

    @Synchronized
    override fun getPrimaryApiKey(): String? {
        val keys = getApiKeys()
        return keys.firstOrNull()?.key ?: getApiKey()
    }

    @Synchronized
    override fun getSecondaryApiKey(): String? {
        val keys = getApiKeys()
        return if (keys.size > 1) keys[1].key else null
    }

    @Synchronized
    override fun saveDualApiKeys(primaryKey: String, secondaryKey: String?): Boolean {
        val trimmedPrimary = primaryKey.trim()
        if (trimmedPrimary.isEmpty()) return false

        val newKeys = mutableListOf<GeminiApiKeyItem>()
        newKeys.add(
            GeminiApiKeyItem(
                key = trimmedPrimary,
                label = "Primary Key",
                status = KeyStatus.ACTIVE
            )
        )

        val trimmedSecondary = secondaryKey?.trim()
        if (!trimmedSecondary.isNullOrEmpty()) {
            newKeys.add(
                GeminiApiKeyItem(
                    key = trimmedSecondary,
                    label = "Secondary Key",
                    status = KeyStatus.ACTIVE
                )
            )
        }

        val success = saveApiKeys(newKeys)
        if (success) {
            // Also keep legacy single key storage in sync
            val encryptedPair = encryptString(trimmedPrimary)
            if (encryptedPair != null) {
                prefs.edit()
                    .putString("encrypted_gemini_api_key", encryptedPair.first)
                    .putString("gemini_api_key_iv", encryptedPair.second)
                    .apply()
            }
        }
        return success
    }

    // --- Encryption Helpers ---

    private fun encryptString(plainText: String): Pair<String, String>? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(encryptedBase64, ivBase64)
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptString(encryptedBase64: String, ivBase64: String): String? {
        return try {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun getLegacyApiKey(): String? {
        return try {
            val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
            val ivBase64 = prefs.getString(KEY_IV, null) ?: return null
            decryptString(encryptedBase64, ivBase64)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } catch (e: Exception) {
            // Fallback for JVM/Robolectric test environments where AndroidKeyStore provider is not registered
            getFallbackJvmKey()
        }
    }

    private fun getFallbackJvmKey(): SecretKey {
        val fallbackPrefsKey = "readmate_jvm_key"
        val existingBase64 = prefs.getString(fallbackPrefsKey, null)
        if (existingBase64 != null) {
            val decoded = Base64.decode(existingBase64, Base64.NO_WRAP)
            return javax.crypto.spec.SecretKeySpec(decoded, "AES")
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val generated = keyGen.generateKey()
        val encoded = Base64.encodeToString(generated.encoded, Base64.NO_WRAP)
        prefs.edit().putString(fallbackPrefsKey, encoded).apply()
        return generated
    }
}

