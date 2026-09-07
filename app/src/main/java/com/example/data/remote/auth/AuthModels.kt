package com.example.data.remote.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Authentication request payload DTO for Google Apps Script auth backend.
 */
@JsonClass(generateAdapter = true)
data class AuthRequestPayload(
    @Json(name = "action") val action: String, // CREATE_ACCOUNT, VERIFY_OTP, SIGN_IN, FORGOT_PASSWORD_REQUEST, RESET_PASSWORD_CONFIRM
    @Json(name = "email") val email: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "password") val password: String? = null,
    @Json(name = "otp_code") val otp_code: String? = null,
    @Json(name = "purpose") val purpose: String? = null, // SIGNUP_VERIFY, PASSWORD_RESET
    @Json(name = "new_password") val new_password: String? = null
)

/**
 * Authentication response payload DTO returned by Google Apps Script auth backend.
 */
@JsonClass(generateAdapter = true)
data class AuthResponsePayload(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "error_code") val error_code: String? = null,
    @Json(name = "data") val data: AuthUserData? = null
)

/**
 * Active authenticated user profile and session token data.
 */
@JsonClass(generateAdapter = true)
data class AuthUserData(
    @Json(name = "user_id") val user_id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "session_token") val session_token: String? = null
)

/**
 * Standard action types supported by the authentication backend.
 */
object AuthActions {
    const val CREATE_ACCOUNT = "CREATE_ACCOUNT"
    const val VERIFY_OTP = "VERIFY_OTP"
    const val SIGN_IN = "SIGN_IN"
    const val FORGOT_PASSWORD_REQUEST = "FORGOT_PASSWORD_REQUEST"
    const val RESET_PASSWORD_CONFIRM = "RESET_PASSWORD_CONFIRM"
}

/**
 * Standard verification purposes for OTP requests.
 */
object AuthPurposes {
    const val SIGNUP_VERIFY = "SIGNUP_VERIFY"
    const val PASSWORD_RESET = "PASSWORD_RESET"
}
