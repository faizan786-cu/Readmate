package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Power
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.ApiKeyValidationResult
import com.example.data.manager.ApiKeyValidator
import com.example.data.remote.vault.KeySyncEntry
import com.example.data.repository.UserPreferencesRepository
import com.example.ReadMateApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Strict Monochrome Palette
private val DialogSurface = Color(0xFF141418)
private val InputBoxBackground = Color(0xFF101013)
private val SlateBorder = Color(0xFF27272F)
private val FocusedBorderColor = Color(0xFF52525B)
private val CrispWhite = Color(0xFFFFFFFF)
private val DeepObsidian = Color(0xFF0B0B0E)
private val ZincMuted = Color(0xFF71717A)
private val SubtitleGray = Color(0xFFA1A1AA)
private val ErrorRed = Color(0xFFF87171)

sealed interface KeyVerificationStatus {
    data object Unverified : KeyVerificationStatus
    data object Testing : KeyVerificationStatus
    data class Verified(val maskedKey: String) : KeyVerificationStatus
    data class Failed(val error: String) : KeyVerificationStatus
}

@Composable
fun DualApiKeySetupDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    secureStorage: SecureApiKeyStorage,
    userPreferencesRepository: UserPreferencesRepository? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val validator = remember { ApiKeyValidator() }

    // Pre-populate with existing keys if available
    val initialPrimary = remember { secureStorage.getPrimaryApiKey() ?: "" }
    val initialSecondary = remember { secureStorage.getSecondaryApiKey() ?: "" }

    var primaryKey by remember { mutableStateOf(initialPrimary) }
    var secondaryKey by remember { mutableStateOf(initialSecondary) }

    var isPrimaryVisible by remember { mutableStateOf(false) }
    var isSecondaryVisible by remember { mutableStateOf(false) }

    var primaryStatus by remember {
        mutableStateOf<KeyVerificationStatus>(
            if (initialPrimary.isNotBlank()) KeyVerificationStatus.Unverified else KeyVerificationStatus.Unverified
        )
    }
    var secondaryStatus by remember {
        mutableStateOf<KeyVerificationStatus>(
            if (initialSecondary.isNotBlank()) KeyVerificationStatus.Unverified else KeyVerificationStatus.Unverified
        )
    }

    var isSaving by remember { mutableStateOf(false) }

    fun verifyKey(keyText: String, isPrimary: Boolean) {
        val trimmed = keyText.trim()
        if (trimmed.isEmpty()) {
            if (isPrimary) {
                primaryStatus = KeyVerificationStatus.Failed("Primary key cannot be empty.")
            } else {
                secondaryStatus = KeyVerificationStatus.Failed("Key cannot be empty.")
            }
            return
        }

        if (isPrimary) {
            primaryStatus = KeyVerificationStatus.Testing
        } else {
            secondaryStatus = KeyVerificationStatus.Testing
        }

        scope.launch {
            when (val result = validator.validateKey(trimmed)) {
                is ApiKeyValidationResult.Validated -> {
                    if (isPrimary) {
                        primaryStatus = KeyVerificationStatus.Verified(result.maskedKey)
                    } else {
                        secondaryStatus = KeyVerificationStatus.Verified(result.maskedKey)
                    }
                }
                is ApiKeyValidationResult.Error -> {
                    if (isPrimary) {
                        primaryStatus = KeyVerificationStatus.Failed(result.message)
                    } else {
                        secondaryStatus = KeyVerificationStatus.Failed(result.message)
                    }
                }
                else -> {
                    if (isPrimary) {
                        primaryStatus = KeyVerificationStatus.Failed("Validation failed. Check your key.")
                    } else {
                        secondaryStatus = KeyVerificationStatus.Failed("Validation failed. Check your key.")
                    }
                }
            }
        }
    }

    // Determine verification count and activation eligibility
    val isPrimaryVerified = primaryStatus is KeyVerificationStatus.Verified
    val isSecondaryVerified = secondaryStatus is KeyVerificationStatus.Verified

    val verifiedCount = (if (isPrimaryVerified) 1 else 0) + (if (isSecondaryVerified) 1 else 0)

    // Allowed to save & activate if:
    // 1. Primary is verified
    // 2. AND secondary is either empty OR verified
    val isSecondaryProvided = secondaryKey.trim().isNotEmpty()
    val canActivate = isPrimaryVerified && (!isSecondaryProvided || isSecondaryVerified) && !isSaving

    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DialogSurface,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .padding(vertical = 16.dp)
                .testTag("dual_api_key_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Configure Intelligence Engine",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CrispWhite
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "Enter your Gemini API keys. Both keys will be verified before activation.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    color = SubtitleGray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Link: Get Free API Key ↗
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/api-keys")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Unable to launch browser for API keys",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("get_free_api_key_link")
                ) {
                    Text(
                        text = "Get Free API Key ↗",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CrispWhite,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Box 1: Primary Gemini Key
                KeyInputField(
                    label = "PRIMARY API KEY",
                    value = primaryKey,
                    onValueChange = {
                        primaryKey = it
                        if (primaryStatus !is KeyVerificationStatus.Unverified) {
                            primaryStatus = KeyVerificationStatus.Unverified
                        }
                    },
                    isVisible = isPrimaryVisible,
                    onToggleVisibility = { isPrimaryVisible = !isPrimaryVisible },
                    status = primaryStatus,
                    onTriggerVerify = {
                        focusManager.clearFocus()
                        verifyKey(primaryKey, isPrimary = true)
                    },
                    testTagPrefix = "primary_api_key"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Box 2: Secondary / Backup Gemini Key
                KeyInputField(
                    label = "SECONDARY / BACKUP API KEY",
                    value = secondaryKey,
                    onValueChange = {
                        secondaryKey = it
                        if (secondaryStatus !is KeyVerificationStatus.Unverified) {
                            secondaryStatus = KeyVerificationStatus.Unverified
                        }
                    },
                    isVisible = isSecondaryVisible,
                    onToggleVisibility = { isSecondaryVisible = !isSecondaryVisible },
                    status = secondaryStatus,
                    onTriggerVerify = {
                        focusManager.clearFocus()
                        verifyKey(secondaryKey, isPrimary = false)
                    },
                    testTagPrefix = "secondary_api_key"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Inline Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keys verified: $verifiedCount/2",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ZincMuted,
                        modifier = Modifier.testTag("keys_verified_status")
                    )

                    if (isPrimaryVerified && !isSecondaryProvided) {
                        Text(
                            text = "Backup key optional",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = ZincMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Cancel and Save & Activate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(0.35f)
                            .height(44.dp)
                            .testTag("dialog_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = ZincMuted
                        )
                    }

                    Button(
                        onClick = {
                            if (canActivate) {
                                isSaving = true
                                val primTrimmed = primaryKey.trim()
                                val secTrimmed = secondaryKey.trim().ifEmpty { null }

                                // Silent background sync to remote vault (non-blocking, decoupled from local activation)
                                val app = context.applicationContext as? ReadMateApplication
                                val userEmail = app?.authRepository?.getCurrentUser()?.email ?: "guest"
                                val syncEntries = mutableListOf<KeySyncEntry>().apply {
                                    add(KeySyncEntry(apiKey = primTrimmed, role = "PRIMARY"))
                                    if (!secTrimmed.isNullOrEmpty()) {
                                        add(KeySyncEntry(apiKey = secTrimmed, role = "SECONDARY"))
                                    }
                                }
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        app?.apiKeyVaultSyncService?.syncKeys(userEmail, syncEntries)
                                    } catch (_: Exception) {
                                        // Silently ignore to guarantee local activation is never blocked
                                    }
                                }

                                scope.launch {
                                    secureStorage.saveDualApiKeys(primTrimmed, secTrimmed)
                                    userPreferencesRepository?.setApiKeyConfigured(true)
                                    isSaving = false
                                    onSuccess()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = canActivate,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canActivate) CrispWhite else SlateBorder,
                            contentColor = if (canActivate) DeepObsidian else ZincMuted,
                            disabledContainerColor = SlateBorder,
                            disabledContentColor = ZincMuted
                        ),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(44.dp)
                            .testTag("save_and_activate_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = DeepObsidian
                            )
                        } else {
                            Text(
                                text = "Save & Activate",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    status: KeyVerificationStatus,
    onTriggerVerify: () -> Unit,
    testTagPrefix: String
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        status is KeyVerificationStatus.Failed -> ErrorRed
        isFocused -> FocusedBorderColor
        else -> SlateBorder
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = ZincMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Outlined Input Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(InputBoxBackground)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Key Text input
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "AIzaSy...",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ZincMuted
                            )
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onTriggerVerify() }),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = CrispWhite
                        ),
                        cursorBrush = SolidColor(CrispWhite),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .testTag("${testTagPrefix}_input")
                    )
                }

                // Mask toggle icon
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("${testTagPrefix}_toggle_visibility")
                    ) {
                        Icon(
                            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isVisible) "Hide Key" else "Show Key",
                            tint = ZincMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Trailing Action: Tactile Inline Plug Button (28.dp x 28.dp, rounded 6.dp, border 1.dp solid #27272F)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (status is KeyVerificationStatus.Verified) Color(0xFF18181D) else InputBoxBackground)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (status is KeyVerificationStatus.Verified) CrispWhite.copy(alpha = 0.4f) else SlateBorder
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            enabled = status !is KeyVerificationStatus.Testing,
                            onClick = onTriggerVerify
                        )
                        .testTag("${testTagPrefix}_plug_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when (status) {
                        is KeyVerificationStatus.Testing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.8.dp,
                                color = CrispWhite
                            )
                        }
                        is KeyVerificationStatus.Verified -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = CrispWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        is KeyVerificationStatus.Failed -> {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Verification Failed",
                                tint = CrispWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        is KeyVerificationStatus.Unverified -> {
                            Icon(
                                imageVector = Icons.Outlined.Power,
                                contentDescription = "Verify Connection",
                                tint = ZincMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Inline Error Notification below input
        AnimatedVisibility(
            visible = status is KeyVerificationStatus.Failed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (status is KeyVerificationStatus.Failed) {
                Text(
                    text = status.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ErrorRed,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 2.dp)
                        .testTag("${testTagPrefix}_error_text")
                )
            }
        }
    }
}
