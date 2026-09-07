package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.auth.AuthPurposes
import com.example.data.remote.auth.AuthUserData
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthTab {
    SIGN_IN,
    CREATE_ACCOUNT
}

enum class AuthScreenState {
    CREDENTIALS,
    OTP_VERIFY,
    FORGOT_PASSWORD_EMAIL,
    FORGOT_PASSWORD_RESET,
    AUTHENTICATED
}

data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.SIGN_IN,
    val screenState: AuthScreenState = AuthScreenState.CREDENTIALS,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val newPassword: String = "",
    val otpDigits: List<String> = List(6) { "" },
    val verifiedResetOtp: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val resendCountdown: Int = 0,
    val currentPurpose: String = AuthPurposes.SIGNUP_VERIFY,
    val isPasswordVisible: Boolean = false,
    val isNewPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val authenticatedUser: AuthUserData? = null
) {
    val fullOtpCode: String get() = otpDigits.joinToString("")
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null && authRepository.isAuthenticated()) {
            _uiState.update {
                it.copy(
                    screenState = AuthScreenState.AUTHENTICATED,
                    authenticatedUser = currentUser
                )
            }
        }
    }

    fun onTabChanged(tab: AuthTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, errorMessage = null) }
    }

    fun onNewPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(newPassword = newPassword, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleNewPasswordVisibility() {
        _uiState.update { it.copy(isNewPasswordVisible = !it.isNewPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun onOtpDigitChanged(index: Int, digit: String, onComplete: (() -> Unit)? = null) {
        if (index !in 0..5) return
        val sanitized = digit.takeLast(1).filter { it.isDigit() }
        val current = _uiState.value.otpDigits.toMutableList()
        current[index] = sanitized
        _uiState.update { it.copy(otpDigits = current, errorMessage = null) }

        // If all 6 digits are filled, automatically trigger OTP verification
        if (current.all { it.isNotBlank() } && current.size == 6) {
            onComplete?.invoke()
        }
    }

    fun setFullOtp(code: String, onComplete: (() -> Unit)? = null) {
        val sanitized = code.filter { it.isDigit() }.take(6)
        val digits = List(6) { idx -> sanitized.getOrNull(idx)?.toString() ?: "" }
        _uiState.update { it.copy(otpDigits = digits, errorMessage = null) }
        if (sanitized.length == 6) {
            onComplete?.invoke()
        }
    }

    fun switchToCredentials() {
        _uiState.update {
            it.copy(
                screenState = AuthScreenState.CREDENTIALS,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun switchToForgotPasswordEmail() {
        _uiState.update {
            it.copy(
                screenState = AuthScreenState.FORGOT_PASSWORD_EMAIL,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startResendCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(resendCountdown = 60) }
        countdownJob = viewModelScope.launch {
            for (i in 59 downTo 0) {
                delay(1000)
                _uiState.update { it.copy(resendCountdown = i) }
            }
        }
    }

    fun onSignInClicked(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password.trim()

        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email is required.") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password is required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.signIn(email.lowercase(), password)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        screenState = AuthScreenState.AUTHENTICATED,
                        authenticatedUser = userData
                    )
                }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Sign in failed."
                    )
                }
            }
        }
    }

    fun onCreateAccountClicked() {
        val name = _uiState.value.name.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password.trim()
        val confirmPassword = _uiState.value.confirmPassword.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name is required.") }
            return
        }
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email is required.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.createAccount(name, email.lowercase(), password)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        screenState = AuthScreenState.OTP_VERIFY,
                        currentPurpose = AuthPurposes.SIGNUP_VERIFY,
                        otpDigits = List(6) { "" },
                        verifiedResetOtp = "",
                        successMessage = result.getOrNull()
                    )
                }
                startResendCountdown()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Account creation failed."
                    )
                }
            }
        }
    }

    fun onVerifyOtpClicked(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val otpCode = _uiState.value.fullOtpCode.trim()
        val purpose = _uiState.value.currentPurpose

        if (otpCode.length < 6) {
            _uiState.update { it.copy(errorMessage = "Please enter the complete 6-digit code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.verifyOtp(email.lowercase(), otpCode, purpose)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (purpose == AuthPurposes.PASSWORD_RESET) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            screenState = AuthScreenState.FORGOT_PASSWORD_RESET,
                            verifiedResetOtp = otpCode,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            screenState = AuthScreenState.AUTHENTICATED,
                            authenticatedUser = userData
                        )
                    }
                    onSuccess()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Invalid verification code."
                    )
                }
            }
        }
    }

    fun onRequestPasswordResetClicked() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your registered email address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.requestPasswordReset(email.lowercase())
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        screenState = AuthScreenState.OTP_VERIFY,
                        currentPurpose = AuthPurposes.PASSWORD_RESET,
                        otpDigits = List(6) { "" },
                        verifiedResetOtp = "",
                        successMessage = result.getOrNull()
                    )
                }
                startResendCountdown()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to request password reset."
                    )
                }
            }
        }
    }

    fun onConfirmPasswordResetClicked(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val verifiedOtp = _uiState.value.verifiedResetOtp.ifBlank { _uiState.value.fullOtpCode.trim() }
        val newPassword = _uiState.value.newPassword.trim()
        val confirmPassword = _uiState.value.confirmPassword.trim()

        if (newPassword.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.confirmPasswordReset(email.lowercase(), verifiedOtp, newPassword)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        screenState = AuthScreenState.CREDENTIALS,
                        selectedTab = AuthTab.SIGN_IN,
                        password = "",
                        confirmPassword = "",
                        newPassword = "",
                        otpDigits = List(6) { "" },
                        verifiedResetOtp = "",
                        successMessage = "Password reset successfully. Please sign in.",
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to reset password."
                    )
                }
            }
        }
    }

    fun resendOtp() {
        if (_uiState.value.resendCountdown > 0) return
        val email = _uiState.value.email.trim()
        val purpose = _uiState.value.currentPurpose

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (purpose == AuthPurposes.PASSWORD_RESET) {
                authRepository.requestPasswordReset(email.lowercase())
            } else {
                val name = _uiState.value.name.ifBlank { "Reader" }
                val password = _uiState.value.password.ifBlank { "Password123" }
                authRepository.createAccount(name, email.lowercase(), password)
            }

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "New verification code dispatched."
                    )
                }
                startResendCountdown()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to resend code."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
