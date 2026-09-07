package com.example.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.remote.auth.AuthUserData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AuthSessionStorage {
    val isAuthenticatedFlow: StateFlow<Boolean>
    fun saveSession(userData: AuthUserData): Boolean
    fun saveSession(userId: String?, name: String?, email: String?, sessionToken: String?): Boolean
    fun getSessionToken(): String?
    fun getUserId(): String?
    fun getEmail(): String?
    fun getUserName(): String?
    fun isAuthenticated(): Boolean
    fun getUserData(): AuthUserData?
    fun clearSession(): Boolean
}

class AndroidKeystoreAuthSessionStorage(
    private val context: Context
) : AuthSessionStorage {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val userDataAdapter: JsonAdapter<AuthUserData> = moshi.adapter(AuthUserData::class.java)

    private val _isAuthenticatedFlow = MutableStateFlow(false)
    override val isAuthenticatedFlow: StateFlow<Boolean> = _isAuthenticatedFlow.asStateFlow()

    init {
        _isAuthenticatedFlow.value = hasValidSession()
    }

    companion object {
        private const val PREFS_NAME = "readmate_auth_secure_prefs"
        private const val KEY_ENCRYPTED_USER_DATA_JSON = "encrypted_auth_user_data_json"
        private const val KEY_USER_DATA_IV = "auth_user_data_iv"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated_flag"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "readmate_auth_session_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun hasValidSession(): Boolean {
        val token = getSessionToken()
        return !token.isNullOrBlank() && prefs.getBoolean(KEY_IS_AUTHENTICATED, false)
    }

    @Synchronized
    override fun saveSession(userData: AuthUserData): Boolean {
        return try {
            val json = userDataAdapter.toJson(userData)
            val encryptedPair = encryptString(json) ?: return false

            prefs.edit()
                .putString(KEY_ENCRYPTED_USER_DATA_JSON, encryptedPair.first)
                .putString(KEY_USER_DATA_IV, encryptedPair.second)
                .putBoolean(KEY_IS_AUTHENTICATED, true)
                .commit()

            _isAuthenticatedFlow.value = true
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    override fun saveSession(
        userId: String?,
        name: String?,
        email: String?,
        sessionToken: String?
    ): Boolean {
        val userData = AuthUserData(
            user_id = userId,
            name = name,
            email = email,
            session_token = sessionToken
        )
        return saveSession(userData)
    }

    @Synchronized
    override fun getUserData(): AuthUserData? {
        return try {
            val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_USER_DATA_JSON, null)
            val ivBase64 = prefs.getString(KEY_USER_DATA_IV, null)

            if (encryptedBase64 != null && ivBase64 != null) {
                val decryptedJson = decryptString(encryptedBase64, ivBase64)
                if (!decryptedJson.isNullOrBlank()) {
                    return userDataAdapter.fromJson(decryptedJson)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun getSessionToken(): String? {
        return getUserData()?.session_token
    }

    override fun getUserId(): String? {
        return getUserData()?.user_id
    }

    override fun getEmail(): String? {
        return getUserData()?.email
    }

    override fun getUserName(): String? {
        return getUserData()?.name
    }

    override fun isAuthenticated(): Boolean {
        return hasValidSession()
    }

    @Synchronized
    override fun clearSession(): Boolean {
        return try {
            prefs.edit()
                .remove(KEY_ENCRYPTED_USER_DATA_JSON)
                .remove(KEY_USER_DATA_IV)
                .putBoolean(KEY_IS_AUTHENTICATED, false)
                .commit()

            _isAuthenticatedFlow.value = false
            true
        } catch (e: Exception) {
            false
        }
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
        val fallbackPrefsKey = "readmate_auth_jvm_key"
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
