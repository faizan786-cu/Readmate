package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.GeminiConnectionState
import com.example.data.model.KeyStatus
import com.example.ui.theme.ReadMateGreen
import com.example.ui.theme.ReadMateGreenContainer
import com.example.ui.theme.ReadMateOnGreenContainer
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.GeminiConfigViewModel
import com.example.ui.viewmodel.TestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiConfigScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeminiConfigViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var showClearAllDialog by remember { mutableStateOf(false) }

    // Dialog: Test API & Model Quotas Dashboard Modal
    TestApiDashboardDialog(
        isOpen = viewModel.showTestApiModal,
        keyLabel = viewModel.testDashboardKeyLabel,
        maskedKey = viewModel.testDashboardMaskedKey,
        testResults = viewModel.modelTestResults,
        isLoading = viewModel.isTestingAllModels,
        onRetest = viewModel::retryTestApiDashboard,
        onDismiss = viewModel::dismissTestApiDashboard
    )

    // Dialog: Delete Single Key Confirmation
    if (viewModel.deletingKeyItem != null) {
        val item = viewModel.deletingKeyItem!!
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteKey,
            title = {
                Text(
                    text = "Delete API Key?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${item.displayLabel}' (${item.maskedKey}) from this device?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::executeDeleteKey,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_key_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDeleteKey,
                    modifier = Modifier.testTag("cancel_delete_key_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Edit Key Label
    if (viewModel.editingKeyItem != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditLabel,
            title = {
                Text(
                    text = "Edit Key Label",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Give this key a friendly nickname:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.editingLabelInput,
                        onValueChange = viewModel::onEditingLabelChange,
                        label = { Text("Key Label") },
                        placeholder = { Text("e.g. Personal Key, Backup Key") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_key_label_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmEditLabel,
                    modifier = Modifier.testTag("save_edit_key_label_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissEditLabel,
                    modifier = Modifier.testTag("cancel_edit_key_label_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Bulk Import Keys
    if (viewModel.showBulkImportDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeBulkImportDialog,
            title = {
                Text(
                    text = "Bulk Import API Keys",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paste multiple Gemini API keys separated by new lines, spaces, or commas. Accepts both legacy 'AIzaSy...' and new 'AQ....' keys.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.bulkImportText,
                        onValueChange = viewModel::onBulkImportTextChange,
                        placeholder = { Text("AIzaSy...\nAQ....\nAIzaSy...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_import_keys_input")
                    )
                    if (viewModel.bulkImportResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = viewModel.bulkImportResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("bulk_import_result_text")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::submitBulkImport,
                    modifier = Modifier.testTag("submit_bulk_import_button")
                ) {
                    Text("Import Keys", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::closeBulkImportDialog,
                    modifier = Modifier.testTag("cancel_bulk_import_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog: Clear All Keys Confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(
                    text = "Clear All API Keys?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "All configured Gemini API keys will be securely deleted from this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearAllKeys(onComplete = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_clear_all_dialog_button")
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false },
                    modifier = Modifier.testTag("cancel_clear_all_dialog_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Gemini API Keys",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("gemini_config_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openTestApiDashboard() },
                        modifier = Modifier.testTag("test_api_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Test API & Quotas"
                        )
                    }
                    IconButton(
                        onClick = viewModel::openBulkImportDialog,
                        modifier = Modifier.testTag("open_bulk_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Bulk import keys"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Intro text
                item {
                    Text(
                        text = "Configure one or multiple Gemini API keys. Requests automatically rotate in Round-Robin order with instant auto-failover if a key hits rate limits (HTTP 429).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. Status Overview Card
                item {
                    val connectedState = connectionState as? GeminiConnectionState.Connected
                    val totalKeys = connectedState?.totalKeyCount ?: 0
                    val activeKeys = connectedState?.activeKeyCount ?: 0
                    val cooldownKeys = connectedState?.cooldownKeyCount ?: 0
                    val errorKeys = connectedState?.errorKeyCount ?: 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_rotation_status_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Multi-Key Auto-Rotation",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (totalKeys > 0) "$totalKeys Key${if (totalKeys > 1) "s" else ""} Configured ($activeKeys Active)" else "No Keys Configured",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (activeKeys > 0) ReadMateGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (cooldownKeys > 0) {
                                    FilledTonalButton(
                                        onClick = viewModel::resetAllCooldowns,
                                        modifier = Modifier.testTag("reset_all_cooldowns_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Resume All", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            if (totalKeys > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatusPill(
                                        label = "Active: $activeKeys",
                                        color = ReadMateGreen,
                                        backgroundColor = ReadMateGreenContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill(
                                        label = "Cooldown: $cooldownKeys",
                                        color = Color(0xFFE65100),
                                        backgroundColor = Color(0xFFFFE0B2),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill(
                                        label = "Error: $errorKeys",
                                        color = MaterialTheme.colorScheme.error,
                                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.openTestApiDashboard() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("test_all_models_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test All Models (Ping Quotas)")
                                }
                            }
                        }
                    }
                }

                // 2. Add New API Key Section
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_key_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Add API Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Supports both standard legacy format ('AIzaSy...') and new format ('AQ....').",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Key input field
                            OutlinedTextField(
                                value = viewModel.apiKeyInput,
                                onValueChange = viewModel::onApiKeyChange,
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("AIzaSy... or AQ....") },
                                singleLine = true,
                                visualTransformation = if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = viewModel::togglePasswordVisibility,
                                        modifier = Modifier.testTag("toggle_password_visibility_button")
                                    ) {
                                        Icon(
                                            imageVector = if (viewModel.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (viewModel.isPasswordVisible) "Hide API key" else "Show API key"
                                        )
                                    }
                                },
                                isError = viewModel.validationError != null,
                                supportingText = {
                                    if (viewModel.validationError != null) {
                                        Text(
                                            text = viewModel.validationError ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.testTag("api_key_validation_error")
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_api_key_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Label input field
                            OutlinedTextField(
                                value = viewModel.keyLabelInput,
                                onValueChange = viewModel::onKeyLabelChange,
                                label = { Text("Key Nickname / Label (Optional)") },
                                placeholder = { Text("e.g. Personal Key, Backup Key") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        viewModel.addKey()
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_api_key_label_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Test Feedback Banner
                            when (val testState = viewModel.testUiState) {
                                is TestUiState.Testing -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .testTag("test_state_testing")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Verifying API key...",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                is TestUiState.Success -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ReadMateGreenContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .testTag("test_state_success")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = ReadMateGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = testState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ReadMateOnGreenContainer
                                            )
                                        }
                                    }
                                }
                                is TestUiState.Failed -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .testTag("test_state_failed")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = testState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                                TestUiState.Idle -> { }
                            }

                            // Actions: Test Key & Add Key
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.testNewKey()
                                    },
                                    enabled = viewModel.testUiState !is TestUiState.Testing && !viewModel.isAddingKey,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("test_connection_button")
                                ) {
                                    Text("Test Key", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.addKey()
                                    },
                                    enabled = viewModel.testUiState !is TestUiState.Testing && !viewModel.isAddingKey,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("connect_api_key_button")
                                ) {
                                    if (viewModel.isAddingKey) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Key", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Configured Keys Header
                item {
                    val connectedState = connectionState as? GeminiConnectionState.Connected
                    val keyList = connectedState?.keys ?: emptyList()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configured API Keys (${keyList.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (keyList.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearAllDialog = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("clear_all_keys_button")
                            ) {
                                Text("Clear All")
                            }
                        }
                    }
                }

                // 4. Configured Keys List
                val connectedState = connectionState as? GeminiConnectionState.Connected
                val keyList = connectedState?.keys ?: emptyList()

                if (keyList.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_keys_notice")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Gemini API keys connected yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add at least one API key above to enable AI-powered book reading explanations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(keyList, key = { _, item -> item.id }) { index, item ->
                        ApiKeyCard(
                            item = item,
                            index = index + 1,
                            isTesting = viewModel.testingKeyId == item.id,
                            onTest = { viewModel.openTestApiDashboard(item) },
                            onEdit = { viewModel.startEditLabel(item) },
                            onDelete = { viewModel.startDeleteKey(item) },
                            onReset = { viewModel.resetKeyStatus(item) }
                        )
                    }
                }

                // Security & Privacy Note Card
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "All API keys are encrypted on-device with AES-256 via Android Keystore. Keys are sent securely via the 'x-goog-api-key' header and never saved in plain text or shared externally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ApiKeyCard(
    item: GeminiApiKeyItem,
    index: Int,
    isTesting: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("api_key_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Key Index Badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.maskedKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Key Status Badge
                when (item.status) {
                    KeyStatus.ACTIVE -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ReadMateGreenContainer,
                            modifier = Modifier.testTag("key_status_active")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ReadMateGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ReadMateOnGreenContainer
                                )
                            }
                        }
                    }
                    KeyStatus.COOLDOWN -> {
                        val remainingSec = item.remainingCooldownSeconds()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFE0B2),
                            modifier = Modifier.testTag("key_status_cooldown")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (remainingSec > 0) "Cooldown (${remainingSec}s)" else "Cooldown",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                    KeyStatus.INVALID -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.testTag("key_status_invalid")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Invalid",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    KeyStatus.PERMISSION_ERROR -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFE0B2),
                            modifier = Modifier.testTag("key_status_permission_error")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFD84315),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Permission issue",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD84315)
                                )
                            }
                        }
                    }
                    KeyStatus.ERROR, KeyStatus.TEST_FAILED -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.testTag("key_status_error")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Error",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Error Message or Cooldown Message if any
            if (!item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val isCooldown = item.status == KeyStatus.COOLDOWN
                val isPerm = item.status == KeyStatus.PERMISSION_ERROR
                val isActive = item.status == KeyStatus.ACTIVE
                val containerColor = when {
                    isCooldown || isPerm -> Color(0xFFFFF3E0)
                    isActive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                }
                val contentColor = when {
                    isCooldown -> Color(0xFFE65100)
                    isPerm -> Color(0xFFD84315)
                    isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Stats info & Action row
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Success: ${item.successCount}" + if (item.failureCount > 0) " • Fails: ${item.failureCount}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status != KeyStatus.ACTIVE || !item.errorMessage.isNullOrBlank()) {
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.testTag("reset_key_button_${item.id}")
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTesting,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("test_single_key_button_${item.id}")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test key",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Test", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_key_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit label",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_key_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete key",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
