package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.security.SecureApiKeyStorage
import com.example.data.manager.ApiKeyValidationResult
import com.example.data.manager.ApiKeyValidator
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

// Monochrome Palette
private val DialogScrim = Color.Black.copy(alpha = 0.70f)
private val DialogBackground = Color(0xFF141418)
private val InputBackground = Color(0xFF0B0B0E)
private val GetKeyTileBackground = Color(0xFF101013)
private val BadgeBackground = Color(0xFF18181B)
private val BorderSlate = Color(0xFF27272F)
private val BorderDashed = Color(0xFF27272A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMutedSilver = Color(0xFFA1A1AA)
private val TextZincMuted = Color(0xFF71717A)
private val TextPlaceholder = Color(0xFF52525B)
private val ConnectedGreen = Color(0xFF4ADE80)
private val ConnectedGreenBg = Color(0xFF101A12)
private val ConnectedGreenBorder = Color(0xFF1B4D25)
private val ErrorRed = Color(0xFFF87171)
private val ErrorRedBg = Color(0xFF1A1012)
private val ErrorRedBorder = Color(0xFF4D1B25)

/**
 * Custom dashed border modifier for tactile get-key tile aesthetic.
 */
private fun Modifier.dashedBorder(
    strokeWidth: Dp = 1.dp,
    color: Color = BorderDashed,
    cornerRadius: Dp = 8.dp,
    dashLength: Dp = 5.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidthPx = strokeWidth.toPx()
    val cornerRadiusPx = cornerRadius.toPx()
    val dashLengthPx = dashLength.toPx()
    val gapLengthPx = gapLength.toPx()

    val pathEffect = PathEffect.dashPathEffect(
        floatArrayOf(dashLengthPx, gapLengthPx),
        0f
    )

    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = pathEffect
        )
    )
}

/**
 * Centered API Key Setup Dialog.
 * Explicit center-anchored dialog for configuring Gemini Cognitive Engine credentials.
 */
@Composable
fun ApiKeySetupDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onKeySaved: (String) -> Unit = {},
    onSuccess: () -> Unit = {},
    secureStorage: SecureApiKeyStorage? = null,
    userPreferencesRepository: UserPreferencesRepository? = null,
    validator: ApiKeyValidator = remember { ApiKeyValidator() },
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputKey by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<ApiKeyValidationResult>(ApiKeyValidationResult.Idle) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun triggerValidation() {
        val trimmed = inputKey.trim()
        if (trimmed.isEmpty()) {
            validationState = ApiKeyValidationResult.Error("Please enter your Gemini API key.")
            return
        }
        keyboardController?.hide()
        validationState = ApiKeyValidationResult.Testing
        coroutineScope.launch {
            val result = validator.validateKey(trimmed)
            validationState = result
        }
    }

    fun checkAndAutofillClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return
            if (!clipboard.hasPrimaryClip()) return
            val item = clipboard.primaryClip?.getItemAt(0) ?: return
            val rawText = item.text?.toString()?.trim() ?: return

            val apiKeyRegex = Regex("^AIzaSy[A-Za-z0-9_-]{33}$")
            if (!apiKeyRegex.matches(rawText)) return

            if (inputKey.isBlank()) {
                inputKey = rawText
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
                triggerValidation()
            }
        } catch (_: Exception) {
            // Silently ignore clipboard exceptions
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAndAutofillClipboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = { /* Modal: dismissOnClickOutside = false */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        // Scrim Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DialogScrim)
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            // Centered Card Container
            Surface(
                modifier = modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("api_key_setup_dialog"),
                shape = RoundedCornerShape(16.dp),
                color = DialogBackground,
                border = BorderStroke(1.dp, BorderSlate),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Top Micro Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BadgeBackground,
                        border = BorderStroke(1.dp, BorderDashed)
                    ) {
                        Text(
                            text = "COGNITIVE ENGINE SETUP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = TextZincMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    Text(
                        text = "Connect Gemini Intelligence",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = TextWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Description
                    Text(
                        text = "Required for zero-friction chapter parsing, automated retention quizzes, and insight extraction.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = TextMutedSilver
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Get Key Action Tile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GetKeyTileBackground)
                            .dashedBorder(strokeWidth = 1.dp, color = BorderDashed, cornerRadius = 8.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = "API Key",
                                tint = TextMutedSilver,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Don't have an API key?",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = TextZincMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Visual Guide ↗",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextMutedSilver,
                                modifier = Modifier
                                    .clickable { openVisualGuide(context) }
                                    .padding(4.dp)
                                    .testTag("visual_guide_link")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Get API Key ↗",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextWhite,
                                modifier = Modifier
                                    .clickable {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://aistudio.google.com/api-keys")
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) { }
                                    }
                                    .padding(4.dp)
                                    .testTag("get_api_key_link")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Field & Tactile Plug Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputBackground)
                            .border(
                                width = 1.dp,
                                color = when (validationState) {
                                    is ApiKeyValidationResult.Validated -> ConnectedGreenBorder
                                    is ApiKeyValidationResult.Error -> ErrorRedBorder
                                    else -> BorderSlate
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputKey.isEmpty()) {
                                Text(
                                    text = "Paste AI Studio Key (AIzaSy...)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = TextPlaceholder
                                )
                            }
                            BasicTextField(
                                value = inputKey,
                                onValueChange = {
                                    inputKey = it
                                    if (validationState !is ApiKeyValidationResult.Idle) {
                                        validationState = ApiKeyValidationResult.Idle
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(TextWhite),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { triggerValidation() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input_field")
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Trailing Tactile Plug / Test Button
                        IconButton(
                            onClick = { triggerValidation() },
                            enabled = validationState !is ApiKeyValidationResult.Testing && inputKey.isNotBlank(),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when (validationState) {
                                        is ApiKeyValidationResult.Validated -> ConnectedGreen.copy(alpha = 0.15f)
                                        else -> Color(0xFF1F1F24)
                                    }
                                )
                                .testTag("validate_key_button")
                        ) {
                            when (validationState) {
                                is ApiKeyValidationResult.Testing -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = TextWhite,
                                        strokeWidth = 2.dp
                                    )
                                }
                                is ApiKeyValidationResult.Validated -> {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = "Validated",
                                        tint = ConnectedGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Outlined.Power,
                                        contentDescription = "Test Connection",
                                        tint = if (inputKey.isNotBlank()) TextWhite else TextPlaceholder,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Status Banner
                    AnimatedVisibility(
                        visible = validationState !is ApiKeyValidationResult.Idle,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            when (val state = validationState) {
                                is ApiKeyValidationResult.Testing -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = TextZincMuted,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pinging Gemini 3.1 Flash-Lite endpoint...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = TextZincMuted
                                        )
                                    }
                                }

                                is ApiKeyValidationResult.Validated -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ConnectedGreenBg,
                                        border = BorderStroke(1.dp, ConnectedGreenBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = ConnectedGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Connection Established • Ready to Deploy",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = ConnectedGreen
                                            )
                                        }
                                    }
                                }

                                is ApiKeyValidationResult.Error -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ErrorRedBg,
                                        border = BorderStroke(1.dp, ErrorRedBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 15.sp
                                            ),
                                            color = ErrorRed,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Actions Footer (Row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left "Later" Button
                        TextButton(
                            onClick = {
                                userPreferencesRepository?.setApiKeyConfigured(false)
                                userPreferencesRepository?.setFirstLaunchCompleted(true)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("api_setup_later_button")
                        ) {
                            Text(
                                text = "Later",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextZincMuted
                            )
                        }

                        // Right "Save & Continue →" Button
                        val isValidated = validationState is ApiKeyValidationResult.Validated
                        Button(
                            onClick = {
                                if (isValidated) {
                                    val trimmed = inputKey.trim()
                                    secureStorage?.addApiKey(trimmed, label = "Primary Key")
                                    userPreferencesRepository?.setApiKeyConfigured(true)
                                    userPreferencesRepository?.setFirstLaunchCompleted(true)
                                    onKeySaved(trimmed)
                                    onSuccess()
                                }
                            },
                            enabled = isValidated,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextWhite,
                                contentColor = InputBackground,
                                disabledContainerColor = TextWhite.copy(alpha = 0.35f),
                                disabledContentColor = InputBackground.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("api_setup_save_button")
                        ) {
                            Text(
                                text = "Save & Continue →",
                                style = MaterialTheme.typography.labelLarge.copy(
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
