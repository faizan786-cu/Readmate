package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.GeminiConnectionState
import com.example.data.model.KeyStatus
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.GeminiConfigViewModel
import com.example.ui.viewmodel.TestUiState

// Strict Monochrome Rigor Color Palette
private val CanvasObsidian = Color(0xFF0B0B0E)
private val CardSurfaceZinc = Color(0xFF141418)
private val SubduedZinc = Color(0xFF101013)
private val SlateBorder = Color(0xFF27272F)
private val FocusedBorder = Color(0xFF52525B)
private val CrispWhite = Color(0xFFFFFFFF)
private val ZincMuted = Color(0xFF71717A)
private val DarkBadgeBg = Color(0xFF1C1C22)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeminiConfigViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var isBulkImportExpanded by remember { mutableStateOf(true) }
    var isSingleKeyExpanded by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var isSubmittingBulk by remember { mutableStateOf(false) }

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
        val displayKey = formatMaskedKey(item.key)
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteKey,
            containerColor = CardSurfaceZinc,
            titleContentColor = CrispWhite,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Delete API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = CrispWhite
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to remove this API key ($displayKey)? It will no longer be used for AI queries or failover rotation.",
                        fontSize = 13.sp,
                        color = ZincMuted,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::executeDeleteKey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrispWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_key_button")
                ) {
                    Text("Delete Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDeleteKey,
                    modifier = Modifier.testTag("cancel_delete_key_button")
                ) {
                    Text("Cancel", color = ZincMuted, fontSize = 13.sp)
                }
            }
        )
    }

    // Dialog: Edit Key Nickname / Label
    if (viewModel.editingKeyItem != null) {
        val item = viewModel.editingKeyItem!!
        AlertDialog(
            onDismissRequest = viewModel::dismissEditLabel,
            containerColor = CardSurfaceZinc,
            titleContentColor = CrispWhite,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Edit Key Label",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = CrispWhite
                )
            },
            text = {
                Column {
                    Text(
                        text = "Key: ${formatMaskedKey(item.key)}",
                        fontSize = 12.sp,
                        color = ZincMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = viewModel.editingLabelInput,
                        onValueChange = viewModel::onEditingLabelChange,
                        placeholder = { Text("e.g. Primary Dev Key", color = ZincMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SubduedZinc,
                            unfocusedContainerColor = SubduedZinc,
                            focusedBorderColor = FocusedBorder,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite,
                            cursorColor = CrispWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_key_label_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmEditLabel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrispWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_save_label_button")
                ) {
                    Text("Save Label", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissEditLabel,
                    modifier = Modifier.testTag("cancel_edit_label_button")
                ) {
                    Text("Cancel", color = ZincMuted, fontSize = 13.sp)
                }
            }
        )
    }

    // Dialog: Clear All Keys Confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = CardSurfaceZinc,
            titleContentColor = CrispWhite,
            textContentColor = ZincMuted,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Clear All Configured Keys",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = CrispWhite
                )
            },
            text = {
                Text(
                    text = "This will remove all Gemini API keys stored on this device. AI explanation and chapter features will be locked until a key is added.",
                    fontSize = 13.sp,
                    color = ZincMuted,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllKeys {
                            showClearAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrispWhite,
                        contentColor = CanvasObsidian
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_clear_all_dialog_button")
                ) {
                    Text("Clear All Keys", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false },
                    modifier = Modifier.testTag("cancel_clear_all_dialog_button")
                ) {
                    Text("Cancel", color = ZincMuted, fontSize = 13.sp)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "API Key Management",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("api_key_management_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CrispWhite
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
                            contentDescription = "Test Quotas",
                            tint = CrispWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasObsidian
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val connectedState = connectionState as? GeminiConnectionState.Connected
                val keyList = connectedState?.keys ?: emptyList()
                val totalKeys = connectedState?.totalKeyCount ?: keyList.size
                val activeKeys = connectedState?.activeKeyCount ?: keyList.count { it.status == KeyStatus.ACTIVE }
                val cooldownKeys = connectedState?.cooldownKeyCount ?: keyList.count { it.status == KeyStatus.COOLDOWN }

                // 1. Key Pool Health Overview Card
                item {
                    KeyPoolHealthCard(
                        totalKeys = totalKeys,
                        activeKeys = activeKeys,
                        cooldownKeys = cooldownKeys,
                        onResetCooldowns = viewModel::resetAllCooldowns,
                        onTestAllModels = { viewModel.openTestApiDashboard() }
                    )
                }

                // 2. Bulk Key Import Accordion / Expandable Section
                item {
                    BulkKeyImportCard(
                        isExpanded = isBulkImportExpanded,
                        onToggleExpand = { isBulkImportExpanded = !isBulkImportExpanded },
                        bulkImportText = viewModel.bulkImportText,
                        onTextChange = viewModel::onBulkImportTextChange,
                        onImport = {
                            isSubmittingBulk = true
                            viewModel.submitBulkImport()
                            isSubmittingBulk = false
                        },
                        isLoading = isSubmittingBulk,
                        importResult = viewModel.bulkImportResult
                    )
                }

                // 3. Add Single Key Accordion / Expandable Section
                item {
                    AddSingleKeyCard(
                        isExpanded = isSingleKeyExpanded,
                        onToggleExpand = { isSingleKeyExpanded = !isSingleKeyExpanded },
                        apiKeyInput = viewModel.apiKeyInput,
                        keyLabelInput = viewModel.keyLabelInput,
                        isPasswordVisible = viewModel.isPasswordVisible,
                        validationError = viewModel.validationError,
                        testUiState = viewModel.testUiState,
                        isAddingKey = viewModel.isAddingKey,
                        onApiKeyChange = viewModel::onApiKeyChange,
                        onKeyLabelChange = viewModel::onKeyLabelChange,
                        onTogglePassword = viewModel::togglePasswordVisibility,
                        onTestKey = {
                            focusManager.clearFocus()
                            viewModel.testNewKey()
                        },
                        onAddKey = {
                            focusManager.clearFocus()
                            viewModel.addKey()
                        }
                    )
                }

                // 4. Configured Keys Header & Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configured Keys (${keyList.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = CrispWhite
                            )
                        )

                        if (keyList.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearAllDialog = true },
                                modifier = Modifier.testTag("clear_all_keys_button")
                            ) {
                                Text(
                                    text = "Clear All",
                                    color = ZincMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 5. Existing Keys List (RecyclerView / LazyColumn items)
                if (keyList.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceZinc),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_keys_notice")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = ZincMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No API keys configured",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = CrispWhite
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Use Bulk Import or Add Single Key above to configure your Gemini keys.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = ZincMuted
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(keyList, key = { _, item -> item.id }) { index, item ->
                        ExistingKeyCard(
                            item = item,
                            index = index + 1,
                            isTesting = viewModel.testingKeyId == item.id,
                            onTest = { viewModel.testSingleKey(item) },
                            onEdit = { viewModel.startEditLabel(item) },
                            onDelete = { viewModel.confirmDeleteKey(item) },
                            onReset = { viewModel.resetKeyStatus(item) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Key Pool Health Overview Card
 */
@Composable
private fun KeyPoolHealthCard(
    totalKeys: Int,
    activeKeys: Int,
    cooldownKeys: Int,
    onResetCooldowns: () -> Unit,
    onTestAllModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("key_pool_health_overview_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceZinc),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Key Pool Health",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CrispWhite
                        )
                    )
                }

                // Strategy Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkBadgeBg,
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = if (totalKeys > 0) "ROUND-ROBIN • AUTO-FAILOVER" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (totalKeys > 0) CrispWhite else ZincMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Metric Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    value = "$activeKeys",
                    label = "Active Keys",
                    highlight = activeKeys > 0,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    value = "$totalKeys",
                    label = "Total Ingested",
                    highlight = false,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    value = "$cooldownKeys",
                    label = "Rate Limited",
                    highlight = cooldownKeys > 0,
                    modifier = Modifier.weight(1f)
                )
            }

            if (cooldownKeys > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onResetCooldowns,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrispWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_all_cooldowns_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume Rate-Limited Keys", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    value: String,
    label: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SubduedZinc,
        border = BorderStroke(1.dp, if (highlight) FocusedBorder else SlateBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CrispWhite
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ZincMuted
                )
            )
        }
    }
}

/**
 * Bulk Key Import Accordion / Expandable Section
 */
@Composable
private fun BulkKeyImportCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    bulkImportText: String,
    onTextChange: (String) -> Unit,
    onImport: () -> Unit,
    isLoading: Boolean,
    importResult: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bulk_key_import_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceZinc),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Clickable Header to Expand/Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Bulk Key Import",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CrispWhite
                            )
                        )
                        Text(
                            text = "Paste multiple Gemini API keys at once",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = ZincMuted
                            )
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = CrispWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "Keys can be separated by newlines, commas, or spaces. Duplicates and invalid formats will be automatically filtered.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = ZincMuted,
                            lineHeight = 16.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = bulkImportText,
                        onValueChange = onTextChange,
                        placeholder = {
                            Text(
                                text = "AIzaSyD...98xQ\nAIzaSyB...42zK\nAIzaSyC...19wM",
                                color = ZincMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SubduedZinc,
                            unfocusedContainerColor = SubduedZinc,
                            focusedBorderColor = FocusedBorder,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite,
                            cursorColor = CrispWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_key_import_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onImport,
                        enabled = bulkImportText.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrispWhite,
                            contentColor = CanvasObsidian,
                            disabledContainerColor = SubduedZinc,
                            disabledContentColor = ZincMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("import_bulk_keys_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CanvasObsidian
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Validating...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text(
                                text = "Import & Validate Bulk Keys",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (bulkImportText.isNotBlank()) CanvasObsidian else ZincMuted
                            )
                        }
                    }

                    if (importResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SubduedZinc,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bulk_import_result")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = importResult,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = CrispWhite
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add Single Key Accordion / Expandable Section
 */
@Composable
private fun AddSingleKeyCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    apiKeyInput: String,
    keyLabelInput: String,
    isPasswordVisible: Boolean,
    validationError: String?,
    testUiState: TestUiState,
    isAddingKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onKeyLabelChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onTestKey: () -> Unit,
    onAddKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("add_single_key_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceZinc),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Add Single Key",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CrispWhite
                            )
                        )
                        Text(
                            text = "Add an individual key with an optional nickname",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = ZincMuted
                            )
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = CrispWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // API Key Input
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = onApiKeyChange,
                        placeholder = { Text("AIzaSyD... or AQ....", color = ZincMuted, fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        trailingIcon = {
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide key" else "Show key",
                                    tint = ZincMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        isError = validationError != null,
                        supportingText = {
                            if (validationError != null) {
                                Text(text = validationError, color = CrispWhite, fontSize = 11.sp)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SubduedZinc,
                            unfocusedContainerColor = SubduedZinc,
                            focusedBorderColor = FocusedBorder,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite,
                            cursorColor = CrispWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Optional Label Input
                    OutlinedTextField(
                        value = keyLabelInput,
                        onValueChange = onKeyLabelChange,
                        placeholder = { Text("Nickname (Optional, e.g. Primary Dev Key)", color = ZincMuted, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onAddKey() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SubduedZinc,
                            unfocusedContainerColor = SubduedZinc,
                            focusedBorderColor = FocusedBorder,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite,
                            cursorColor = CrispWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_label_input")
                    )

                    // Test UI feedback banner
                    when (testUiState) {
                        is TestUiState.Testing -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = CrispWhite
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pinging Gemini API...", fontSize = 11.sp, color = CrispWhite)
                                }
                            }
                        }
                        is TestUiState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(testUiState.message, fontSize = 11.sp, color = CrispWhite)
                                }
                            }
                        }
                        is TestUiState.Failed -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SubduedZinc,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = testUiState.message,
                                    fontSize = 11.sp,
                                    color = ZincMuted,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        TestUiState.Idle -> { }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestKey,
                            enabled = testUiState !is TestUiState.Testing && !isAddingKey,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrispWhite),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("test_connection_button")
                        ) {
                            Text("Test Key", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onAddKey,
                            enabled = testUiState !is TestUiState.Testing && !isAddingKey,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrispWhite,
                                contentColor = CanvasObsidian,
                                disabledContainerColor = SubduedZinc,
                                disabledContentColor = ZincMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("connect_api_key_button")
                        ) {
                            if (isAddingKey) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CanvasObsidian
                                )
                            } else {
                                Text("Add Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Existing Keys List Item Card
 */
@Composable
private fun ExistingKeyCard(
    item: GeminiApiKeyItem,
    index: Int,
    isTesting: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maskedString = formatMaskedKey(item.key)

    // Health Status: VALID, RATE_LIMITED, or INVALID
    val healthStatus = when (item.status) {
        KeyStatus.ACTIVE -> "VALID"
        KeyStatus.COOLDOWN -> if (item.remainingCooldownSeconds() > 0) "RATE_LIMITED" else "VALID"
        KeyStatus.INVALID,
        KeyStatus.PERMISSION_ERROR,
        KeyStatus.ERROR,
        KeyStatus.TEST_FAILED -> "INVALID"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("api_key_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceZinc),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Index + Masked Key + Health Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SubduedZinc,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = "#$index",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ZincMuted
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = maskedString,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CrispWhite
                        )
                    )
                }

                // Health Status Badge: VALID, RATE_LIMITED, INVALID
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (healthStatus == "VALID") Color(0xFF27272F) else DarkBadgeBg,
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.testTag("key_health_badge_${item.id}")
                ) {
                    Text(
                        text = healthStatus,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (healthStatus == "VALID") CrispWhite else ZincMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Nickname / Label Row
            if (item.label.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = ZincMuted
                    )
                )
            }

            // Error / Cooldown note if applicable
            if (item.errorMessage != null && healthStatus != "VALID") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        color = ZincMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row: Inline Test Connection + Edit Label + Delete / Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrispWhite),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("test_key_button_${item.id}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = CrispWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = CrispWhite,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("edit_key_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit label",
                        tint = ZincMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("delete_key_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete key",
                        tint = ZincMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Helper to mask an API key strictly in the format `AIzaSyD...98xQ`
 */
private fun formatMaskedKey(rawKey: String): String {
    val trimmed = rawKey.trim()
    return when {
        trimmed.length >= 11 -> "${trimmed.take(7)}...${trimmed.takeLast(4)}"
        trimmed.length >= 6 -> "${trimmed.take(3)}...${trimmed.takeLast(2)}"
        else -> "••••••••"
    }
}
