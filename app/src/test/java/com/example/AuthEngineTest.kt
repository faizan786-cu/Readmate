package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.security.AndroidKeystoreAuthSessionStorage
import com.example.data.remote.auth.AuthApiService
import com.example.data.remote.auth.AuthPurposes
import com.example.data.remote.auth.AuthRequestPayload
import com.example.data.remote.auth.AuthResponsePayload
import com.example.data.remote.auth.AuthUserData
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AuthEngineTest {

    private lateinit var context: Context
    private lateinit var sessionStorage: AndroidKeystoreAuthSessionStorage
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionStorage = AndroidKeystoreAuthSessionStorage(context)
        sessionStorage.clearSession()
    }

    @Test
    fun sessionStorage_saveAndRetrieveSession_persistsEncryptedData() {
        val testUser = AuthUserData(
            user_id = "usr_99812",
            name = "Test Reader",
            email = "reader@example.com",
            session_token = "tok_sec_123456789"
        )

        assertFalse(sessionStorage.isAuthenticated())
        assertNull(sessionStorage.getSessionToken())

        val saved = sessionStorage.saveSession(testUser)
        assertTrue(saved)

        assertTrue(sessionStorage.isAuthenticated())
        assertEquals("tok_sec_123456789", sessionStorage.getSessionToken())
        assertEquals("usr_99812", sessionStorage.getUserId())
        assertEquals("reader@example.com", sessionStorage.getEmail())
        assertEquals("Test Reader", sessionStorage.getUserName())
        assertEquals(true, sessionStorage.isAuthenticatedFlow.value)

        val cleared = sessionStorage.clearSession()
        assertTrue(cleared)
        assertFalse(sessionStorage.isAuthenticated())
        assertNull(sessionStorage.getSessionToken())
        assertEquals(false, sessionStorage.isAuthenticatedFlow.value)
    }

    @Test
    fun authRepository_createAccount_validatesInput() = runTest(testDispatcher) {
        val fakeApi = object : AuthApiService {
            override suspend fun executeAuthAction(payload: AuthRequestPayload): AuthResponsePayload {
                return AuthResponsePayload(success = true, message = "Account created.")
            }
        }
        val repo = AuthRepository(authApiService = fakeApi, authSessionStorage = sessionStorage, ioDispatcher = testDispatcher)

        // Invalid name
        val resultBlankName = repo.createAccount("", "test@example.com", "password123")
        assertTrue(resultBlankName.isFailure)

        // Short password
        val resultShortPass = repo.createAccount("Reader", "test@example.com", "123")
        assertTrue(resultShortPass.isFailure)

        // Valid call
        val resultSuccess = repo.createAccount("Reader", "test@example.com", "password123")
        assertTrue(resultSuccess.isSuccess)
        assertEquals("Account created.", resultSuccess.getOrNull())
    }

    @Test
    fun authRepository_signIn_savesSessionOnSuccess() = runTest(testDispatcher) {
        val fakeApi = object : AuthApiService {
            override suspend fun executeAuthAction(payload: AuthRequestPayload): AuthResponsePayload {
                return AuthResponsePayload(
                    success = true,
                    message = "Success",
                    data = AuthUserData(
                        user_id = "usr_101",
                        name = "Alex",
                        email = "alex@example.com",
                        session_token = "sess_tok_abc"
                    )
                )
            }
        }
        val repo = AuthRepository(authApiService = fakeApi, authSessionStorage = sessionStorage, ioDispatcher = testDispatcher)

        val signInResult = repo.signIn("alex@example.com", "secret123")
        assertTrue(signInResult.isSuccess)

        val userData = signInResult.getOrNull()
        assertNotNull(userData)
        assertEquals("sess_tok_abc", userData?.session_token)

        assertTrue(sessionStorage.isAuthenticated())
        assertEquals("sess_tok_abc", sessionStorage.getSessionToken())
    }

    @Test
    fun authRepository_verifyOtp_handlesFailureGracefully() = runTest(testDispatcher) {
        val fakeApi = object : AuthApiService {
            override suspend fun executeAuthAction(payload: AuthRequestPayload): AuthResponsePayload {
                return AuthResponsePayload(
                    success = false,
                    message = "Invalid or expired OTP."
                )
            }
        }
        val repo = AuthRepository(authApiService = fakeApi, authSessionStorage = sessionStorage, ioDispatcher = testDispatcher)

        val result = repo.verifyOtp("alex@example.com", "123456", AuthPurposes.SIGNUP_VERIFY)
        assertTrue(result.isFailure)
        assertEquals("Invalid or expired OTP.", result.exceptionOrNull()?.message)
        assertFalse(sessionStorage.isAuthenticated())
    }
}
