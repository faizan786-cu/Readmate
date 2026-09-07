package com.example.data.repository

import com.example.data.local.security.AuthSessionStorage
import com.example.data.remote.auth.AuthActions
import com.example.data.remote.auth.AuthApiService
import com.example.data.remote.auth.AuthPurposes
import com.example.data.remote.auth.AuthRequestPayload
import com.example.data.remote.auth.AuthResponsePayload
import com.example.data.remote.auth.AuthUserData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository responsible for user authentication, OTP verification, password resets,
 * and secure session persistence.
 */
class AuthRepository(
    private val authApiService: AuthApiService = AuthApiService.create(),
    private val authSessionStorage: AuthSessionStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val isAuthenticatedFlow: StateFlow<Boolean> = authSessionStorage.isAuthenticatedFlow

    fun isAuthenticated(): Boolean = authSessionStorage.isAuthenticated()

    fun getCurrentUser(): AuthUserData? = authSessionStorage.getUserData()

    fun getSessionToken(): String? = authSessionStorage.getSessionToken()

    /**
     * Registers a new account and triggers OTP dispatch to the user's email.
     */
    suspend fun createAccount(
        name: String,
        email: String,
        password: String
    ): Result<String> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()

        if (trimmedName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty."))
        if (trimmedEmail.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))
        if (trimmedPassword.length < 6) return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))

        val payload = AuthRequestPayload(
            action = AuthActions.CREATE_ACCOUNT,
            name = trimmedName,
            email = trimmedEmail,
            password = trimmedPassword
        )

        safeApiCall {
            val response = authApiService.executeAuthAction(payload)
            if (response.success) {
                Result.success(response.message.ifBlank { "Account created. Verification code sent to $trimmedEmail." })
            } else {
                Result.failure(Exception(response.message.ifBlank { "Account creation failed. Please try again." }))
            }
        }
    }

    /**
     * Verifies the 6-digit OTP code for either SIGNUP_VERIFY or PASSWORD_RESET.
     * Persists session token on successful signup verification if returned by backend.
     */
    suspend fun verifyOtp(
        email: String,
        otpCode: String,
        purpose: String = AuthPurposes.SIGNUP_VERIFY
    ): Result<AuthUserData> = withContext(ioDispatcher) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedOtp = otpCode.trim()

        if (trimmedEmail.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))
        if (trimmedOtp.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Verification code cannot be empty."))

        val payload = AuthRequestPayload(
            action = AuthActions.VERIFY_OTP,
            email = trimmedEmail,
            otp_code = trimmedOtp,
            purpose = purpose
        )

        safeApiCall {
            val response = authApiService.executeAuthAction(payload)
            if (response.success) {
                val userData = response.data ?: AuthUserData(
                    email = trimmedEmail,
                    session_token = null
                )

                if (!userData.session_token.isNullOrBlank()) {
                    authSessionStorage.saveSession(userData)
                }

                Result.success(userData)
            } else {
                Result.failure(Exception(response.message.ifBlank { "Verification failed. Invalid or expired OTP." }))
            }
        }
    }

    /**
     * Signs in an existing user with email and password.
     * Persists session token into secure Keystore storage on success.
     */
    suspend fun signIn(
        email: String,
        password: String
    ): Result<AuthUserData> = withContext(ioDispatcher) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))
        if (trimmedPassword.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Password cannot be empty."))

        val payload = AuthRequestPayload(
            action = AuthActions.SIGN_IN,
            email = trimmedEmail,
            password = trimmedPassword
        )

        safeApiCall {
            val response = authApiService.executeAuthAction(payload)
            if (response.success && response.data != null) {
                val userData = response.data
                authSessionStorage.saveSession(userData)
                Result.success(userData)
            } else {
                Result.failure(Exception(response.message.ifBlank { "Invalid email or password." }))
            }
        }
    }

    /**
     * Requests a password reset OTP code sent to the registered email.
     */
    suspend fun requestPasswordReset(
        email: String
    ): Result<String> = withContext(ioDispatcher) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))

        val payload = AuthRequestPayload(
            action = AuthActions.FORGOT_PASSWORD_REQUEST,
            email = trimmedEmail
        )

        safeApiCall {
            val response = authApiService.executeAuthAction(payload)
            if (response.success) {
                Result.success(response.message.ifBlank { "Password reset instructions sent to $trimmedEmail." })
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to request password reset." }))
            }
        }
    }

    /**
     * Confirms password reset with OTP code and updates user's password.
     */
    suspend fun confirmPasswordReset(
        email: String,
        otpCode: String,
        newPassword: String
    ): Result<String> = withContext(ioDispatcher) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedOtp = otpCode.trim()
        val trimmedNewPassword = newPassword.trim()

        if (trimmedEmail.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Email cannot be empty."))
        if (trimmedOtp.isEmpty()) return@withContext Result.failure(IllegalArgumentException("OTP code cannot be empty."))
        if (trimmedNewPassword.length < 6) return@withContext Result.failure(IllegalArgumentException("New password must be at least 6 characters."))

        val payload = AuthRequestPayload(
            action = AuthActions.RESET_PASSWORD_CONFIRM,
            email = trimmedEmail,
            otp_code = trimmedOtp,
            new_password = trimmedNewPassword
        )

        safeApiCall {
            val response = authApiService.executeAuthAction(payload)
            if (response.success) {
                Result.success(response.message.ifBlank { "Password has been successfully reset. You may now sign in." })
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to reset password. Please check your verification code." }))
            }
        }
    }

    /**
     * Clears user session and logs out.
     */
    fun logout(): Result<Unit> {
        return try {
            authSessionStorage.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes network calls safely, handling network and HTTP errors without throwing.
     */
    private inline fun <T> safeApiCall(block: () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out. Please check your network and try again."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Unable to connect to authentication server. Please check your internet connection."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Please verify your connection."}"))
        } catch (e: HttpException) {
            val code = e.code()
            Result.failure(Exception("Server returned HTTP $code. Please try again later."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "An unexpected error occurred."))
        }
    }
}
