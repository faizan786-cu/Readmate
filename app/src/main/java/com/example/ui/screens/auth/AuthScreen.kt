package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ReadMateEmblem
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.AuthScreenState
import com.example.ui.viewmodel.AuthTab
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Strict Monochrome Palette
private val CanvasObsidian = Color(0xFF0B0B0E)
private val SurfaceZinc = Color(0xFF141418)
private val SurfaceSubdued = Color(0xFF101013)
private val BorderSlate = Color(0xFF27272F)
private val BorderFocused = Color(0xFF71717A)
private val TextPureWhite = Color(0xFFFAFAFA)
private val TextZincMuted = Color(0xFF71717A)
private val TextZincSecondary = Color(0xFFA1A1AA)
private val TextPlaceholder = Color(0xFF52525B)
private val ErrorMutedRed = Color(0xFFF87171)

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasObsidian)
            .systemBarsPadding()
            .imePadding()
    ) {
        val availableHeight = maxHeight
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = (availableHeight - 40.dp).coerceAtLeast(0.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Centered ReadMate Logo Emblem & Wordmark (decluttered header)
                AuthBrandHeader()

                // Spacing (20dp to 24dp) leading straight into the segment switch pod
                Spacer(modifier = Modifier.height(22.dp))

                when (uiState.screenState) {
                    AuthScreenState.CREDENTIALS -> {
                        CredentialsContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onAuthSuccess = onAuthSuccess
                        )
                    }

                    AuthScreenState.OTP_VERIFY -> {
                        OtpVerifyContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onAuthSuccess = onAuthSuccess
                        )
                    }

                    AuthScreenState.FORGOT_PASSWORD_EMAIL -> {
                        ForgotPasswordEmailContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }

                    AuthScreenState.FORGOT_PASSWORD_RESET -> {
                        ForgotPasswordResetContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onAuthSuccess = onAuthSuccess
                        )
                    }

                    AuthScreenState.AUTHENTICATED -> {
                        LaunchedEffect(Unit) {
                            onAuthSuccess()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Retained Brand Header:
 * - Centered ReadMate Logo Emblem (#141418 surface, 1dp #27272F border, rounded 14dp, size 56dp).
 * - Spacing (12dp).
 * - Clean Brand Wordmark: "ReadMate" (#FFFFFF, 20sp, bold, centered).
 */
@Composable
private fun AuthBrandHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        ReadMateEmblem(
            size = 56.dp,
            cornerRadius = 14.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ReadMate",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = TextPureWhite,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Credentials Form View (Sign In & Create Account Tabs).
 */
@Composable
private fun CredentialsContent(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Segmented Control
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceZinc,
            border = BorderStroke(1.dp, BorderSlate),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sign In Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.selectedTab == AuthTab.SIGN_IN) BorderSlate else Color.Transparent)
                        .clickable { viewModel.onTabChanged(AuthTab.SIGN_IN) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = if (uiState.selectedTab == AuthTab.SIGN_IN) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (uiState.selectedTab == AuthTab.SIGN_IN) TextPureWhite else TextZincMuted
                    )
                }

                // Create Account Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.selectedTab == AuthTab.CREATE_ACCOUNT) BorderSlate else Color.Transparent)
                        .clickable { viewModel.onTabChanged(AuthTab.CREATE_ACCOUNT) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = if (uiState.selectedTab == AuthTab.CREATE_ACCOUNT) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (uiState.selectedTab == AuthTab.CREATE_ACCOUNT) TextPureWhite else TextZincMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Name Field (Only on Create Account)
        if (uiState.selectedTab == AuthTab.CREATE_ACCOUNT) {
            MonochromeInputField(
                label = "Full Name",
                value = uiState.name,
                placeholder = "Alex Mercer",
                onValueChange = { viewModel.onNameChanged(it) },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                bottomClearance = 14.dp,
                testTag = "auth_name_input"
            )
        }

        // Email Field
        MonochromeInputField(
            label = "Email Address",
            value = uiState.email,
            placeholder = "name@domain.com",
            onValueChange = { viewModel.onEmailChanged(it) },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            bottomClearance = 14.dp,
            testTag = "auth_email_input"
        )

        // Password Fields
        if (uiState.selectedTab == AuthTab.SIGN_IN) {
            MonochromeInputField(
                label = "Password",
                value = uiState.password,
                placeholder = "••••••••",
                onValueChange = { viewModel.onPasswordChanged(it) },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                isPasswordVisible = uiState.isPasswordVisible,
                onToggleVisibility = { viewModel.togglePasswordVisibility() },
                onDone = { viewModel.onSignInClicked(onAuthSuccess) },
                bottomClearance = 6.dp,
                testTag = "auth_password_input"
            )

            // Forgot Password Link (Sign In Only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Forgot Password?",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextZincSecondary,
                    modifier = Modifier
                        .clickable { viewModel.switchToForgotPasswordEmail() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("forgot_password_link")
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        } else {
            // Create Account: Sequential Password & Confirm Password
            MonochromeInputField(
                label = "Password",
                value = uiState.password,
                placeholder = "Password (min 6 chars)",
                onValueChange = { viewModel.onPasswordChanged(it) },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                isPassword = true,
                isPasswordVisible = uiState.isPasswordVisible,
                onToggleVisibility = { viewModel.togglePasswordVisibility() },
                bottomClearance = 14.dp,
                testTag = "auth_password_input"
            )

            MonochromeInputField(
                label = "Confirm Password",
                value = uiState.confirmPassword,
                placeholder = "Confirm Password",
                onValueChange = { viewModel.onConfirmPasswordChanged(it) },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                isPasswordVisible = uiState.isConfirmPasswordVisible,
                onToggleVisibility = { viewModel.toggleConfirmPasswordVisibility() },
                onDone = { viewModel.onCreateAccountClicked() },
                bottomClearance = 18.dp,
                testTag = "auth_confirm_password_input"
            )
        }

        // Error Message Banner
        if (uiState.errorMessage != null) {
            ErrorMessageBanner(message = uiState.errorMessage!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Success Message Banner
        if (uiState.successMessage != null) {
            SuccessMessageBanner(message = uiState.successMessage!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Primary Action CTA
        Button(
            onClick = {
                if (uiState.selectedTab == AuthTab.SIGN_IN) {
                    viewModel.onSignInClicked(onAuthSuccess)
                } else {
                    viewModel.onCreateAccountClicked()
                }
            },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPureWhite,
                contentColor = CanvasObsidian,
                disabledContainerColor = SurfaceZinc,
                disabledContentColor = TextZincMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("auth_primary_submit_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = CanvasObsidian,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = if (uiState.selectedTab == AuthTab.SIGN_IN) "Sign In →" else "Create Account →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * 6-Digit Tactile OTP Verification View.
 */
@Composable
private fun OtpVerifyContent(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Verify Security Code",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPureWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "A 6-digit verification code has been dispatched to ${uiState.email}.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),
            color = TextZincSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 6 Discrete Digit Boxes
        OtpSixDigitBoxes(
            digits = uiState.otpDigits,
            onDigitChanged = { index, value ->
                viewModel.onOtpDigitChanged(index, value) {
                    viewModel.onVerifyOtpClicked(onAuthSuccess)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Message Banner
        if (uiState.errorMessage != null) {
            ErrorMessageBanner(message = uiState.errorMessage!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Resend Timer & Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.resendCountdown > 0) {
                Text(
                    text = "Resend code in 0:${uiState.resendCountdown.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextZincMuted
                )
            } else {
                Text(
                    text = "Resend Code",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPureWhite,
                    modifier = Modifier
                        .clickable { viewModel.resendOtp() }
                        .padding(4.dp)
                        .testTag("resend_otp_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Verify CTA Button
        Button(
            onClick = { viewModel.onVerifyOtpClicked(onAuthSuccess) },
            enabled = !uiState.isLoading && uiState.fullOtpCode.length == 6,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPureWhite,
                contentColor = CanvasObsidian,
                disabledContainerColor = SurfaceZinc,
                disabledContentColor = TextZincMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("verify_otp_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = CanvasObsidian,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "Verify Code →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Change Email link
        TextButton(
            onClick = { viewModel.switchToCredentials() },
            modifier = Modifier.testTag("change_email_link")
        ) {
            Text(
                text = "← Change Email",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextZincSecondary
            )
        }
    }
}

/**
 * 6 discrete input containers for tactile OTP entry.
 */
@Composable
private fun OtpSixDigitBoxes(
    digits: List<String>,
    onDigitChanged: (Int, String) -> Unit
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until 6) {
            val digit = digits.getOrElse(index) { "" }
            var isFocused by remember { mutableStateOf(false) }

            BasicTextField(
                value = digit,
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        onDigitChanged(index, "")
                        if (index > 0) {
                            focusRequesters[index - 1].requestFocus()
                        }
                    } else {
                        val sanitized = newValue.takeLast(1).filter { it.isDigit() }
                        onDigitChanged(index, sanitized)
                        if (sanitized.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        } else if (index == 5) {
                            focusManager.clearFocus()
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == 5) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (index < 5) focusRequesters[index + 1].requestFocus()
                    },
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                textStyle = TextStyle(
                    color = TextPureWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(TextPureWhite),
                modifier = Modifier
                    .width(46.dp)
                    .height(52.dp)
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Backspace && digit.isEmpty() && index > 0) {
                            focusRequesters[index - 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("otp_box_$index"),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceZinc, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                focusRequesters[index].requestFocus()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = if (isFocused) 1.5.dp else 1.dp,
                                color = if (isFocused) TextPureWhite else BorderSlate
                            )
                        ) {}

                        innerTextField()
                    }
                }
            )
        }
    }
}

/**
 * Forgot Password Step A (Email Input View).
 */
@Composable
private fun ForgotPasswordEmailContent(
    uiState: AuthUiState,
    viewModel: AuthViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPureWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter your registered email address to receive a secure recovery code.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),
            color = TextZincSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        MonochromeInputField(
            label = "Email Address",
            value = uiState.email,
            placeholder = "name@domain.com",
            onValueChange = { viewModel.onEmailChanged(it) },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onDone = { viewModel.onRequestPasswordResetClicked() },
            bottomClearance = 20.dp,
            testTag = "forgot_email_input"
        )

        if (uiState.errorMessage != null) {
            ErrorMessageBanner(message = uiState.errorMessage!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.onRequestPasswordResetClicked() },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPureWhite,
                contentColor = CanvasObsidian,
                disabledContainerColor = SurfaceZinc,
                disabledContentColor = TextZincMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("send_reset_code_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = CanvasObsidian,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "Send Reset Code →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { viewModel.switchToCredentials() },
            modifier = Modifier.testTag("back_to_sign_in_button")
        ) {
            Text(
                text = "← Back to Sign In",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextZincSecondary
            )
        }
    }
}

/**
 * Forgot Password Step B (New Password Input View).
 */
@Composable
private fun ForgotPasswordResetContent(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create New Password",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPureWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Verification code confirmed. Set a strong password with at least 6 characters.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),
            color = TextZincSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        MonochromeInputField(
            label = "New Password",
            value = uiState.newPassword,
            placeholder = "••••••••",
            onValueChange = { viewModel.onNewPasswordChanged(it) },
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            isPassword = true,
            isPasswordVisible = uiState.isNewPasswordVisible,
            onToggleVisibility = { viewModel.toggleNewPasswordVisibility() },
            bottomClearance = 14.dp,
            testTag = "new_password_input"
        )

        MonochromeInputField(
            label = "Confirm New Password",
            value = uiState.confirmPassword,
            placeholder = "••••••••",
            onValueChange = { viewModel.onConfirmPasswordChanged(it) },
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isPassword = true,
            isPasswordVisible = uiState.isConfirmPasswordVisible,
            onToggleVisibility = { viewModel.toggleConfirmPasswordVisibility() },
            onDone = { viewModel.onConfirmPasswordResetClicked(onAuthSuccess) },
            bottomClearance = 20.dp,
            testTag = "confirm_password_input"
        )

        if (uiState.errorMessage != null) {
            ErrorMessageBanner(message = uiState.errorMessage!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.onConfirmPasswordResetClicked(onAuthSuccess) },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TextPureWhite,
                contentColor = CanvasObsidian,
                disabledContainerColor = SurfaceZinc,
                disabledContentColor = TextZincMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("confirm_reset_password_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = CanvasObsidian,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "Update Password & Sign In →",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Reusable Monochrome Styled Input Field with Smart Bring-Into-View on focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonochromeInputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    bottomClearance: Dp = 16.dp,
    testTag: String = ""
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isFocused) TextPureWhite else TextZincMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceZinc,
            border = BorderStroke(
                width = 1.dp,
                color = if (isFocused) BorderFocused else BorderSlate
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDone?.invoke() }
                ),
                visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPureWhite,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(TextPureWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(180)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
                    .testTag(testTag),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp
                                    ),
                                    color = TextPlaceholder
                                )
                            }
                            innerTextField()
                        }

                        if (isPassword && onToggleVisibility != null) {
                            IconButton(
                                onClick = onToggleVisibility,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = if (isFocused) TextZincSecondary else TextZincMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        if (bottomClearance > 0.dp) {
            Spacer(modifier = Modifier.height(bottomClearance))
        }
    }
}

@Composable
private fun ErrorMessageBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0x1AF87171),
        border = BorderStroke(1.dp, Color(0x33F87171)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            ),
            color = ErrorMutedRed,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SuccessMessageBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0x1A10B981),
        border = BorderStroke(1.dp, Color(0x3310B981)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            ),
            color = Color(0xFF34D399),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
