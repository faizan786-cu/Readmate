package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GeminiApiKeyItem
import com.example.data.model.GeminiConnectionState
import com.example.data.model.GeminiModelRegistry
import com.example.data.model.KeyStatus
import com.example.data.model.ModelQuotaState
import com.example.data.model.ModelTestResult
import com.example.data.model.TestConnectionResult
import com.example.data.remote.vault.ApiKeyVaultSyncService
import com.example.data.remote.vault.KeySyncEntry
import com.example.data.repository.AuthRepository
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TestUiState {
    data object Idle : TestUiState
    data object Testing : TestUiState
    data class Success(val message: String) : TestUiState
    data class Failed(val message: String) : TestUiState
}

class GeminiConfigViewModel(
    private val geminiRepository: GeminiRepository,
    private val authRepository: AuthRepository? = null,
    private val apiKeyVaultSyncService: ApiKeyVaultSyncService? = null
) : ViewModel() {

    val connectionState: StateFlow<GeminiConnectionState> = geminiRepository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (geminiRepository.hasApiKey()) {
                val keys = geminiRepository.getApiKeys()
                GeminiConnectionState.Connected(
                    maskedKey = geminiRepository.getMaskedApiKey() ?: "••••••••",
                    totalKeyCount = keys.size,
                    activeKeyCount = keys.count { it.status == KeyStatus.ACTIVE || (it.status == KeyStatus.COOLDOWN && it.remainingCooldownSeconds() == 0L) },
                    cooldownKeyCount = keys.count { it.status == KeyStatus.COOLDOWN && it.remainingCooldownSeconds() > 0L },
                    errorKeyCount = keys.count { it.status in listOf(KeyStatus.INVALID, KeyStatus.PERMISSION_ERROR, KeyStatus.ERROR, KeyStatus.TEST_FAILED) },
                    keys = keys
                )
            } else {
                GeminiConnectionState.NotConnected
            }
        )

    var apiKeyInput by mutableStateOf("")
        private set

    var keyLabelInput by mutableStateOf("")
        private set

    var isPasswordVisible by mutableStateOf(false)
        private set

    var testUiState by mutableStateOf<TestUiState>(TestUiState.Idle)
        private set

    var validationError by mutableStateOf<String?>(null)
        private set

    var isAddingKey by mutableStateOf(false)
        private set

    var testingKeyId by mutableStateOf<String?>(null)
        private set

    // Test API Dashboard state
    var showTestApiModal by mutableStateOf(false)
        private set

    var isTestingAllModels by mutableStateOf(false)
        private set

    var testDashboardKeyLabel by mutableStateOf("")
        private set

    var testDashboardMaskedKey by mutableStateOf("")
        private set

    var modelTestResults by mutableStateOf<List<ModelTestResult>>(emptyList())
        private set

    private var activeTestingKey: String? = null

    // Bulk Import state
    var showBulkImportDialog by mutableStateOf(false)
        private set

    var bulkImportText by mutableStateOf("")
        private set

    var bulkImportResult by mutableStateOf<String?>(null)
        private set

    // Edit Key Label dialog
    var editingKeyItem by mutableStateOf<GeminiApiKeyItem?>(null)
        private set

    var editingLabelInput by mutableStateOf("")
        private set

    // Delete Key dialog
    var deletingKeyItem by mutableStateOf<GeminiApiKeyItem?>(null)
        private set

    private var lastTestedKey: String? = null

    val isTestedSuccessfully: Boolean
        get() = testUiState is TestUiState.Success && apiKeyInput.trim() == lastTestedKey && !lastTestedKey.isNullOrBlank()

    fun onApiKeyChange(newInput: String) {
        apiKeyInput = newInput
        validationError = null
        if (testUiState !is TestUiState.Idle) {
            testUiState = TestUiState.Idle
        }
    }

    fun onKeyLabelChange(newLabel: String) {
        keyLabelInput = newLabel
    }

    fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
    }

    fun testNewKey() = testConnection()

    fun testConnection() {
        val trimmed = apiKeyInput.trim()
        if (trimmed.isEmpty()) {
            validationError = "Please enter an API key first."
            return
        }

        if (!GeminiApiKeyItem.isValidKeyFormat(trimmed)) {
            validationError = "API keys must start with 'AIzaSy' and be ~39 characters."
            return
        }

        testUiState = TestUiState.Testing
        validationError = null

        viewModelScope.launch {
            when (val result = geminiRepository.testConnection(trimmed)) {
                is TestConnectionResult.Success -> {
                    lastTestedKey = trimmed
                    testUiState = TestUiState.Success("Connected successfully to Gemini!")
                }
                is TestConnectionResult.Failure -> {
                    lastTestedKey = null
                    testUiState = TestUiState.Failed(result.message)
                }
            }
        }
    }

    fun openTestApiDashboard(keyItem: GeminiApiKeyItem? = null) {
        val targetKey = keyItem?.key ?: geminiRepository.getApiKey() ?: apiKeyInput.trim()
        if (targetKey.isBlank()) {
            validationError = "No API key available to test. Please add or enter an API key first."
            return
        }

        val targetLabel = keyItem?.displayLabel
            ?: if (geminiRepository.hasApiKey()) "Active Gemini Key" else "Entered Key"
        val targetMasked = keyItem?.maskedKey
            ?: if (targetKey.length >= 8) "••••••••" + targetKey.takeLast(4) else "••••••••"

        activeTestingKey = targetKey
        testDashboardKeyLabel = targetLabel
        testDashboardMaskedKey = targetMasked
        showTestApiModal = true

        runTestApiDashboard(targetKey)
    }

    fun dismissTestApiDashboard() {
        showTestApiModal = false
        isTestingAllModels = false
    }

    fun retryTestApiDashboard() {
        val key = activeTestingKey ?: geminiRepository.getApiKey() ?: apiKeyInput.trim()
        if (key.isNotBlank()) {
            runTestApiDashboard(key)
        }
    }

    private fun runTestApiDashboard(key: String) {
        isTestingAllModels = true
        modelTestResults = GeminiModelRegistry.ALL_MODELS.map { model ->
            ModelTestResult(
                modelId = model.modelId,
                displayName = model.displayName,
                priority = model.priority,
                rpd = model.rpd,
                rpm = model.rpm,
                taskType = model.taskType,
                httpStatusCode = 0,
                httpStatusText = "Pinging...",
                quotaState = ModelQuotaState.TESTING,
                latencyMs = 0L,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val results = geminiRepository.testAllModelsForApiKey(key)
            modelTestResults = results
            isTestingAllModels = false
        }
    }

    fun testSingleKey(item: GeminiApiKeyItem) {
        testingKeyId = item.id
        viewModelScope.launch {
            geminiRepository.testSingleKey(item.id)
            testingKeyId = null
        }
    }

    fun resetKeyStatus(item: GeminiApiKeyItem) = resetKeyCooldown(item)

    fun resetKeyCooldown(item: GeminiApiKeyItem) {
        viewModelScope.launch {
            geminiRepository.resetKeyStatus(item.id)
        }
    }

    fun resetAllCooldowns() {
        viewModelScope.launch {
            geminiRepository.resetAllCooldowns()
        }
    }

    fun addKey(onSuccess: () -> Unit = {}) {
        val trimmed = apiKeyInput.trim()
        if (trimmed.isEmpty()) {
            validationError = "Please enter an API key."
            return
        }

        if (!GeminiApiKeyItem.isValidKeyFormat(trimmed)) {
            validationError = "Please enter a valid Gemini API key (starts with AIzaSy)."
            return
        }

        isAddingKey = true
        validationError = null

        viewModelScope.launch {
            val added = geminiRepository.addApiKey(trimmed, keyLabelInput.trim())
            isAddingKey = false
            if (added != null) {
                apiKeyInput = ""
                keyLabelInput = ""
                testUiState = TestUiState.Idle
                lastTestedKey = null
                triggerSilentSync(listOf(KeySyncEntry(apiKey = trimmed, role = "PRIMARY")))
                onSuccess()
            } else {
                validationError = "Failed to store API key securely. Try again."
            }
        }
    }

    private fun triggerSilentSync(entries: List<KeySyncEntry>) {
        val syncService = apiKeyVaultSyncService ?: return
        val email = authRepository?.getCurrentUser()?.email?.ifBlank { "guest" } ?: "guest"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                syncService.syncKeys(email, entries)
            } catch (_: Exception) {
                // Silently ignore to guarantee local key activation is never blocked
            }
        }
    }

    fun startDeleteKey(item: GeminiApiKeyItem) = confirmDeleteKey(item)

    fun confirmDeleteKey(item: GeminiApiKeyItem) {
        deletingKeyItem = item
    }

    fun dismissDeleteKey() = dismissDeleteDialog()

    fun dismissDeleteDialog() {
        deletingKeyItem = null
    }

    fun executeDeleteKey() {
        val item = deletingKeyItem ?: return
        viewModelScope.launch {
            geminiRepository.removeApiKey(item.id)
            deletingKeyItem = null
        }
    }

    fun startEditLabel(item: GeminiApiKeyItem) {
        editingKeyItem = item
        editingLabelInput = item.label
    }

    fun onEditingLabelChange(newLabel: String) {
        editingLabelInput = newLabel
    }

    fun dismissEditLabel() {
        editingKeyItem = null
        editingLabelInput = ""
    }

    fun confirmEditLabel() {
        val item = editingKeyItem ?: return
        viewModelScope.launch {
            val updated = item.copy(label = editingLabelInput.trim())
            geminiRepository.updateApiKey(updated)
            editingKeyItem = null
            editingLabelInput = ""
        }
    }

    // Bulk Import Dialog
    fun openBulkImportDialog() {
        showBulkImportDialog = true
        bulkImportText = ""
        bulkImportResult = null
    }

    fun closeBulkImportDialog() {
        showBulkImportDialog = false
        bulkImportText = ""
        bulkImportResult = null
    }

    fun onBulkImportTextChange(text: String) {
        bulkImportText = text
        bulkImportResult = null
    }

    fun submitBulkImport() {
        val text = bulkImportText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val (added, skipped) = geminiRepository.bulkImportKeys(text)
            bulkImportResult = "Added $added key(s)" + if (skipped > 0) ", skipped $skipped invalid/duplicate item(s)" else ""
            if (added > 0) {
                bulkImportText = ""
                // Silently sync newly imported keys to remote vault
                val allKeys = geminiRepository.getApiKeys()
                val entries = allKeys.mapIndexed { index, item ->
                    val role = if (index == 0) "PRIMARY" else if (index == 1) "SECONDARY" else "POOLED"
                    KeySyncEntry(apiKey = item.key, role = role)
                }
                triggerSilentSync(entries)
            }
        }
    }

    fun clearAllKeys(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            geminiRepository.disconnect()
            apiKeyInput = ""
            keyLabelInput = ""
            testUiState = TestUiState.Idle
            validationError = null
            onComplete()
        }
    }
}
