package com.example.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.ReadMateDatabase
import com.example.data.model.GeminiConnectionState
import com.example.data.model.GeminiModelRegistry
import com.example.data.model.KeyStatus
import com.example.data.model.ModelQuotaState
import com.example.data.model.ModelTestResult
import com.example.data.repository.GeminiRepository
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val geminiRepository: GeminiRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: ReadMateDatabase,
    private val context: Context
) : ViewModel() {

    var isResettingData by mutableStateOf(false)
        private set

    fun resetAllAppData(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isResettingData = true
            try {
                database.clearAllData(context)
                userPreferencesRepository.resetFontSize()
                onSuccess()
            } catch (_: Exception) {
                onSuccess()
            } finally {
                isResettingData = false
            }
        }
    }

    val responseFontSizePercent: StateFlow<Int> = userPreferencesRepository.responseFontSizePercent

    fun increaseFontSize() {
        userPreferencesRepository.increaseFontSize()
    }

    fun decreaseFontSize() {
        userPreferencesRepository.decreaseFontSize()
    }

    fun resetFontSize() {
        userPreferencesRepository.resetFontSize()
    }

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

    var showTestApiModal by mutableStateOf(false)
        private set

    var isTestingAllModels by mutableStateOf(false)
        private set

    var modelTestResults by mutableStateOf<List<ModelTestResult>>(emptyList())
        private set

    var testDashboardMaskedKey by mutableStateOf("")
        private set

    fun openTestApiDashboard() {
        val apiKey = geminiRepository.getApiKey() ?: return
        testDashboardMaskedKey = geminiRepository.getMaskedApiKey() ?: "••••••••"
        showTestApiModal = true
        runTestApiDashboard(apiKey)
    }

    fun dismissTestApiDashboard() {
        showTestApiModal = false
        isTestingAllModels = false
    }

    fun retryTestApiDashboard() {
        val apiKey = geminiRepository.getApiKey() ?: return
        runTestApiDashboard(apiKey)
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

    fun disconnect(onDisconnected: () -> Unit = {}) {
        viewModelScope.launch {
            geminiRepository.disconnect()
            onDisconnected()
        }
    }

    fun addApiKey(key: String, label: String = "Primary Key", onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            geminiRepository.addApiKey(key, label)
            userPreferencesRepository.setApiKeyConfigured(true)
            onComplete()
        }
    }

    fun refreshState() {
        geminiRepository.refreshState()
    }
}
